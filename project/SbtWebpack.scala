import com.typesafe.sbt.packager
import com.typesafe.sbt.web._
import com.typesafe.sbt.web.incremental._
import sbt.Keys._
import sbt._
import sbt.internal.util.ManagedLogger
import xsbti.{Position, Problem, Reporter, Severity}

import java.io.File
import java.util.Optional
import scala.io.{AnsiColor, Source}
import scala.sys.process.{Process, ProcessLogger}

/** Runs `webpack` command in assets. Project's build has to define `entries` and `outputFileName` settings. Supports
  * `skip` setting.
  */
object SbtWebpack extends AutoPlugin {

  override def requires: SbtWeb.type = SbtWeb

  override def trigger: PluginTrigger = AllRequirements

  object autoImport {
    object WebpackKeys {
      val webpack = TaskKey[Seq[File]]("webpack", "Run webpack")
      val binary = SettingKey[File]("webpackBinary", "The location of webpack binary")
      val configFile = SettingKey[File]("webpackConfigFile", "The location of webpack.config.js")
      val nodeModulesPath = TaskKey[File]("webpackNodeModules", "The location of the node_modules.")
      val sourceDirs = SettingKey[Seq[File]]("webpackSourceDirs", "The directories that contains source files.")
      val entries = SettingKey[Seq[String]](
        "webpackEntries",
        "The entry point pseudo-paths. If the path starts with `assets:` it will be resolved in assests source directory, if the path starts with `webjar:` it will be resolved in the webjars/lib target directory."
      )
      val outputFileName =
        SettingKey[String]("webpackOutputFileName", "The name of the webpack output file.")
    }
  }

  import SbtNpm.autoImport.NpmKeys
  import SbtWeb.autoImport._
  import WebKeys._
  import autoImport.WebpackKeys

  final val unscopedWebpackSettings = Seq(
    WebpackKeys.binary := (Assets / sourceDirectory).value / "node_modules" / ".bin" / "webpack",
    WebpackKeys.configFile := (Assets / sourceDirectory).value / "webpack.config.js",
    WebpackKeys.sourceDirs := Seq((Assets / sourceDirectory).value),
    WebpackKeys.nodeModulesPath := new File("./node_modules"),
    WebpackKeys.webpack := task
      .dependsOn(Assets / WebKeys.webModules)
      .dependsOn(NpmKeys.npmInstall)
      .value,
    WebpackKeys.webpack / excludeFilter := HiddenFileFilter ||
      ((file: File) => {
        val path = file.getAbsolutePath
        path.contains("/node_modules/") ||
        path.contains("/build/")
      }),
    WebpackKeys.webpack / includeFilter := "*.js" || "*.ts",
    WebpackKeys.webpack / resourceManaged := webTarget.value / "webpack" / "build",
    WebpackKeys.webpack / sbt.Keys.skip := false,
    packager.Keys.dist := (packager.Keys.dist dependsOn WebpackKeys.webpack).value,
    managedResourceDirectories += (WebpackKeys.webpack / resourceManaged).value,
    resourceGenerators += WebpackKeys.webpack,
    // Because sbt-webpack might compile JS and output into the same file.
    // Therefore, we need to deduplicate the files by choosing the one in the target directory.
    // Otherwise, the "duplicate mappings" error would occur.
    deduplicators += {
      val targetDir = (WebpackKeys.webpack / resourceManaged).value
      val targetDirAbsolutePath = targetDir.getAbsolutePath

      { files: Seq[File] => files.find(_.getAbsolutePath.startsWith(targetDirAbsolutePath)) }
    }
  )

  override def projectSettings: Seq[Setting[_]] =
    inConfig(Assets)(unscopedWebpackSettings)

  final def readAndClose(file: File): String =
    if (file.exists() && file.canRead) {
      val s = Source.fromFile(file)
      try s.mkString
      finally s.close()
    } else ""

  lazy val task = Def.task {

    val skip = (WebpackKeys.webpack / sbt.Keys.skip).value
    val logger: ManagedLogger = (Assets / streams).value.log
    val baseDir: File = (Assets / sourceDirectory).value
    val targetDir: File = (Assets / WebpackKeys.webpack / resourceManaged).value

    val nodeModulesLocation: File = (WebpackKeys.webpack / WebpackKeys.nodeModulesPath).value
    val webpackSourceDirs: Seq[File] = (WebpackKeys.webpack / WebpackKeys.sourceDirs).value
    val webpackReporter: Reporter = (Assets / reporter).value
    val webpackBinaryLocation: File = (WebpackKeys.webpack / WebpackKeys.binary).value
    val webpackConfigFileLocation: File = (WebpackKeys.webpack / WebpackKeys.configFile).value
    val webpackEntries: Seq[String] = (WebpackKeys.webpack / WebpackKeys.entries).value
    val webpackOutputFileName: String = (WebpackKeys.webpack / WebpackKeys.outputFileName).value
    val webpackTargetDir: File = (WebpackKeys.webpack / resourceManaged).value
    val assetsWebJarsLocation: File = (Assets / webJarsDirectory).value
    val projectRoot: File = baseDirectory.value

    val webpackEntryFiles: Set[File] =
      webpackEntries.map { path =>
        if (path.startsWith("assets:"))
          baseDir.toPath.resolve(path.drop(7)).toFile
        else if (path.startsWith("webjar:"))
          assetsWebJarsLocation.toPath.resolve(path.drop(7)).toFile
        else projectRoot.toPath.resolve(path).toFile
      }.toSet

    val sources: Seq[File] = (webpackSourceDirs
      .flatMap { sourceDir =>
        (sourceDir ** ((WebpackKeys.webpack / includeFilter).value -- (WebpackKeys.webpack / excludeFilter).value)).get
      }
      .filter(_.isFile) ++ webpackEntryFiles ++ Seq(webpackConfigFileLocation)).distinct

    val globalHash = new String(
      Hash(
        Seq(
          readAndClose(webpackConfigFileLocation),
          state.value.currentCommand.map(_.commandLine).getOrElse(""),
          sys.env.toList.sorted.toString
        ).mkString("--")
      )
    )

    val fileHasherIncludingOptions = OpInputHasher[File] { f =>
      OpInputHash.hashString(
        Seq(
          "sbt-webpack",
          f.getCanonicalPath,
          baseDir.getAbsolutePath,
          globalHash
        ).mkString("--")
      )
    }

    val results = incremental.syncIncremental((Assets / streams).value.cacheDirectory / "run", sources) {
      modifiedSources =>
        val startInstant = System.currentTimeMillis

        if (!skip && modifiedSources.nonEmpty) {
          logger.info(s"""
                         |[sbt-webpack] Detected ${modifiedSources.size} changed files:
                         |[sbt-webpack] - ${modifiedSources
                          .map(f => f.relativeTo(projectRoot).getOrElse(f).toString)
                          .mkString("\n[sbt-webpack] - ")}
           """.stripMargin.trim)

          val compiler = new Compiler(
            projectRoot,
            webpackBinaryLocation,
            webpackConfigFileLocation,
            webpackEntryFiles,
            webpackOutputFileName,
            webpackTargetDir,
            assetsWebJarsLocation,
            baseDir,
            targetDir,
            logger,
            nodeModulesLocation
          )

          // Compile all modified sources at once
          val result = compiler.compile()

          // Report compilation problems
          CompileProblems.report(
            reporter = webpackReporter,
            problems =
              if (!result.success)
                Seq(new Problem {
                  override def category() = ""

                  override def severity() = Severity.Error

                  override def message() = ""

                  override def position(): Position =
                    new Position {
                      override def line(): Optional[Integer] = java.util.Optional.empty()

                      override def lineContent() = ""

                      override def offset(): Optional[Integer] = java.util.Optional.empty()

                      override def pointer(): Optional[Integer] = java.util.Optional.empty()

                      override def pointerSpace(): Optional[String] = java.util.Optional.empty()

                      override def sourcePath(): Optional[String] = java.util.Optional.empty()

                      override def sourceFile(): Optional[File] = java.util.Optional.empty()
                    }
                })
              else Seq.empty
          )

          val opResults = result.entries
            .filter { entry =>
              // Webpack might generate extra files from extra input files. We can't track those input files.
              modifiedSources.exists(f => f.getCanonicalPath == entry.inputFile.getCanonicalPath)
            }
            .map { entry =>
              entry.inputFile -> OpSuccess(entry.filesRead, entry.filesWritten)
            }
            .toMap

          // The below is important for excluding unrelated files in the next recompilation.
          val resultInputFilePaths = result.entries.map(_.inputFile.getCanonicalPath)
          val unrelatedOpResults = modifiedSources
            .filterNot(file => resultInputFilePaths.contains(file.getCanonicalPath))
            .map { file =>
              file -> OpSuccess(Set(file), Set.empty)
            }
            .toMap

          val createdFiles = result.entries.flatMap(_.filesWritten).distinct
          val endInstant = System.currentTimeMillis

          createdFiles
            .map(f => f.relativeTo(projectRoot).getOrElse(f))
            .sorted
            .foreach { s =>
              logger.info(
                s"[sbt-webpack] Generated ${AnsiColor.MAGENTA}$s${AnsiColor.RESET} in ${endInstant - startInstant} ms"
              )
            }

          (opResults ++ unrelatedOpResults, createdFiles)
        } else {
          if (skip)
            logger.info(s"[sbt-webpack] Skiping webpack")
          else
            logger.info(s"[sbt-webpack] No changes to re-compile")
          (Map.empty, Seq.empty)
        }

    }(fileHasherIncludingOptions)

    // Return the dependencies
    (results._1 ++ results._2.toSet).toSeq

  }

  case class CompilationResult(success: Boolean, entries: Seq[CompilationEntry])
  case class CompilationEntry(inputFile: File, filesRead: Set[File], filesWritten: Set[File])

  object Shell {
    def execute(cmd: Seq[String], cwd: File, envs: (String, String)*): (Int, Seq[String]) = {
      var output = Vector.empty[String]
      val process = Process(cmd, cwd, envs: _*)
      val exitCode = process.!(ProcessLogger(s => output = output.:+(s)))
      (exitCode, output)
    }
  }

  class Compiler(
    projectRoot: File,
    binary: File,
    configFile: File,
    entries: Set[File],
    outputFileName: String,
    outputDirectory: File,
    webjarsDirectory: File,
    baseDir: File,
    targetDir: File,
    logger: ManagedLogger,
    nodeModules: File
  ) {

    def getFile(path: String): File =
      if (path.startsWith("/"))
        new File(path)
      else
        targetDir.toPath.resolve(path).toFile.getCanonicalFile

    def compile(): CompilationResult = {
      import sbt._

      val entriesEnvs = entries.zipWithIndex.flatMap { case (file, index) =>
        Seq("--env", s"""entry.$index=${file.getAbsolutePath}""")
      }

      val cmd = Seq(
        s"""${binary.getCanonicalPath}""",
        "--config",
        s"""${configFile.getAbsolutePath}""",
        "--env",
        s"""output.path=${outputDirectory.getAbsolutePath}""",
        "--env",
        s"""output.filename=$outputFileName""",
        "--env",
        s"""webjars.path=${webjarsDirectory.getAbsolutePath}"""
      ) ++ entriesEnvs

      logger.info(s"[sbt-webpack] Running command ${AnsiColor.CYAN}${cmd.mkString(" ")}${AnsiColor.RESET}")

      val (exitCode, output) =
        Shell.execute(cmd, baseDir, "NODE_PATH" -> nodeModules.getCanonicalPath)

      val success = exitCode == 0

      val regex1 = """^(\[\d+\]|\|)\s(.+?)\s.*""".r

      def parseOutputLine(line: String): String =
        line.trim match {
          case regex1(_, s) => s
          case s            => s
        }

      if (success) {
        val processedFiles: Seq[File] =
          output
            .filter(s => s.contains("[built]") && !s.contains("multi") && !s.contains("(webpack)"))
            .map(parseOutputLine)
            .sorted
            .map(path => baseDir.toPath.resolve(path).toFile)

        logger.info(
          processedFiles
            .map(file => file.relativeTo(projectRoot).getOrElse(file))
            .mkString("[sbt-webpack] Processed files:\n[sbt-webpack] - ", "\n[sbt-webpack] - ", "\n")
        )

        CompilationResult(
          success = true,
          entries = Seq(
            CompilationEntry(
              inputFile = configFile,
              filesRead = processedFiles.toSet,
              filesWritten = Set(outputDirectory.toPath.resolve(outputFileName).toFile)
            )
          )
        )
      } else {
        logger.error(
          output.map(s => s"[sbt-webpack] $s").mkString("\n")
        )
        CompilationResult(success = false, entries = Seq.empty)
      }
    }
  }

}
