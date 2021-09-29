import java.io.PrintWriter
import java.nio.file.Paths

import com.typesafe.sbt.web._
import com.typesafe.sbt.web.incremental._
import com.typesafe.sbt.packager
import sbt.Keys._
import sbt._
import xsbti.{Position, Problem, Severity}

import scala.io.Source
import sbt.internal.util.ManagedLogger
import xsbti.Reporter
import scala.sys.process.ProcessLogger
import scala.io.AnsiColor

import java.io.{File, FileOutputStream, PrintWriter}
import java.nio.file.{Files, Path}
import sbt.internal.util.ManagedLogger
import scala.io.Source
import play.sbt.PlayRunHook
import sbt.internal.util.ConsoleLogger
import scala.util.Properties
import scala.sys.process.Process

/**
  * Runs `webpack` command in assets.
  * Project's build has to define `entries` and `outputFileName` settings.
  * Supports `skip` setting.
  */
object SbtWebpack extends AutoPlugin {

  override def requires = SbtWeb

  override def trigger = AllRequirements

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

  import SbtWeb.autoImport._
  import WebKeys._
  import autoImport.WebpackKeys
  import SbtNpm.autoImport.NpmKeys

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
      new FileFilter {
        override def accept(file: File): Boolean = {
          val path = file.getAbsolutePath()
          path.contains("/node_modules/") ||
          path.contains("/build/")
        }
      },
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
    if (file.exists() && file.canRead()) {
      val s = Source.fromFile(file)
      try s.mkString
      finally s.close()
    } else ""

  lazy val task = Def.task {

    val skip = (WebpackKeys.webpack / sbt.Keys.skip).value
    val logger: ManagedLogger = (streams in Assets).value.log
    val baseDir: File = (sourceDirectory in Assets).value
    val targetDir: File = (resourceManaged in WebpackKeys.webpack in Assets).value

    val nodeModulesLocation: File = (WebpackKeys.nodeModulesPath in WebpackKeys.webpack).value
    val webpackSourceDirs: Seq[File] = (WebpackKeys.sourceDirs in WebpackKeys.webpack).value
    val webpackReporter: Reporter = (reporter in Assets).value
    val webpackBinaryLocation: File = (WebpackKeys.binary in WebpackKeys.webpack).value
    val webpackConfigFileLocation: File = (WebpackKeys.configFile in WebpackKeys.webpack).value
    val webpackEntries: Seq[String] = (WebpackKeys.entries in WebpackKeys.webpack).value
    val webpackOutputFileName: String = (WebpackKeys.outputFileName in WebpackKeys.webpack).value
    val webpackTargetDir: File = (resourceManaged in WebpackKeys.webpack).value
    val assetsWebJarsLocation: File = (webJarsDirectory in Assets).value
    val projectRoot: File = baseDirectory.value

    val webpackEntryFiles: Set[File] =
      webpackEntries.map { path =>
        if (path.startsWith("assets:"))
          baseDir.toPath.resolve(path.drop(7)).toFile()
        else if (path.startsWith("webjar:"))
          assetsWebJarsLocation.toPath.resolve(path.drop(7)).toFile()
        else projectRoot.toPath().resolve(path).toFile()
      }.toSet

    val sources: Seq[File] = (webpackSourceDirs
      .flatMap { sourceDir =>
        (sourceDir ** ((includeFilter in WebpackKeys.webpack).value -- (excludeFilter in WebpackKeys.webpack).value)).get
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

    val results = incremental.syncIncremental((streams in Assets).value.cacheDirectory / "run", sources) {
      modifiedSources =>
        val startInstant = System.currentTimeMillis

        if (!skip && modifiedSources.nonEmpty) {
          logger.info(s"""
                         |[sbt-webpack] Detected ${modifiedSources.size} changed files:
                         |[sbt-webpack] - ${modifiedSources.map(f => f.relativeTo(projectRoot).getOrElse(f).toString()).mkString("\n[sbt-webpack] - ")}
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

                  override def position() =
                    new Position {
                      override def line() = java.util.Optional.empty()

                      override def lineContent() = ""

                      override def offset() = java.util.Optional.empty()

                      override def pointer() = java.util.Optional.empty()

                      override def pointerSpace() = java.util.Optional.empty()

                      override def sourcePath() = java.util.Optional.empty()

                      override def sourceFile() = java.util.Optional.empty()
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
    def execute(cmd: String, cwd: File, envs: (String, String)*): (Int, Seq[String]) = {
      var output = Vector.empty[String]
      val exitCode = Process(cmd, cwd, envs: _*).!(ProcessLogger(s => output = output.:+(s)))
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

      val entriesEnvs = entries.zipWithIndex.map {
        case (file, index) =>
          s"""--env entry.$index=${file.getAbsolutePath()}"""
      }

      val cmd = (Seq(
        binary.getCanonicalPath,
        "--config",
        configFile.getAbsolutePath(),
        s"""--env output.path=${outputDirectory.getAbsolutePath()}""",
        s"""--env output.filename=$outputFileName""",
        s"""--env webjars.path=${webjarsDirectory.getAbsolutePath()}"""
      ) ++ entriesEnvs).mkString(" ")

      logger.info(s"[sbt-webpack] Running command ${AnsiColor.CYAN}$cmd${AnsiColor.RESET}")

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
            .map(path => baseDir.toPath().resolve(path).toFile)

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
              filesWritten = Set(outputDirectory.toPath().resolve(outputFileName).toFile())
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
