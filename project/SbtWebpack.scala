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

/** Runs `webpack` command. Project's build has to define `configurations` setting. */
object SbtWebpack extends AutoPlugin {

  override def requires = SbtWeb

  override def trigger = AllRequirements

  object autoImport {

    object WebpackKeys {
      val webpack =
        TaskKey[Seq[File]]("webpack", "Run all enabled webpack configurations.")
      val webpackOnly =
        inputKey[Seq[File]]("Run selected configurations for provided ids.")
      val binary = SettingKey[File]("webpackBinary", "The location of the webpack binary.")
      val nodeModules = TaskKey[File](
        "webpackNodeModules",
        "The location of the node_modules folder, either relative to webpack's sourceDirectory or absolute."
      )
      val configurations = SettingKey[Seq[WebpackConfig]](
        "webpackConfigurations",
        "Configurations of the webpack to execute."
      )
    }

    /** Configuration of a single webpack compilation. */
    case class WebpackConfig(
      /** Compilation id */
      id: String,
      /** A path of the webpack config.js definition relative to the working directory. */
      configFilePath: String,
      /** Condsidered files filter. */
      includeFilter: FileFilter,
      /** Compilation input paths.
        *   - if the path starts with `assets:` it will be resolved in assests source directory.
        *   - if the path starts with `webjar:` it will be resolved in the target/web/webjars/lib directory.
        */
      inputs: Seq[String],
      /** A path of the compilation output file. */
      output: String,
      /** On/Off flag */
      enabled: Boolean = true,
      /** Additional environment properties to add to the webpack execution command line. */
      additionalProperties: Map[String, String] = Map.empty
    )
  }

  import SbtWeb.autoImport._
  import WebKeys._
  import autoImport.WebpackKeys
  import SbtNpm.autoImport.NpmKeys

  final val unscopedWebpackSettings = Seq(
    WebpackKeys.binary := (Assets / sourceDirectory).value / "node_modules" / ".bin" / "webpack",
    WebpackKeys.nodeModules := new File("./node_modules"),
    WebpackKeys.webpack := runWebpackConfigs(Seq.empty)
      .dependsOn(Assets / WebKeys.webModules)
      .dependsOn(NpmKeys.npmInstall)
      .value,
    WebpackKeys.webpackOnly := Def.inputTaskDyn {
      val ids = sbt.complete.Parsers.spaceDelimited("<args>").parsed
      runWebpackConfigs(ids)
    }.evaluated,
    WebpackKeys.webpack / excludeFilter := HiddenFileFilter ||
      new FileFilter {
        override def accept(file: File): Boolean = {
          val path = file.getAbsolutePath()
          path.contains("/node_modules/") ||
          path.contains("/build/") ||
          path.contains("/dist/")
        }
      },
    WebpackKeys.webpack / sourceDirectory := (Assets / sourceDirectory).value,
    WebpackKeys.webpack / resourceManaged := webTarget.value / "webpack",
    WebpackKeys.webpack / sbt.Keys.skip := false,
    packager.Keys.dist := (packager.Keys.dist dependsOn WebpackKeys.webpack).value,
    managedResourceDirectories += (WebpackKeys.webpack / resourceManaged).value,
    resourceGenerators += WebpackKeys.webpack,
    // Because sbt-webpack might compile sources and output into the same file.
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

  final def runWebpackConfigs(acceptedIds: Seq[String]) = Def.task {
    val tag = "[sbt-webpack]"
    val logger: ManagedLogger = (Assets / streams).value.log
    val targetDir: File = (Assets / WebpackKeys.webpack / resourceManaged).value

    val nodeModulesPath: File = (WebpackKeys.webpack / WebpackKeys.nodeModules).value
    val webpackReporter: Reporter = (Assets / reporter).value
    val webpackBinary: File = (WebpackKeys.webpack / WebpackKeys.binary).value
    val outputDirectory: File = (WebpackKeys.webpack / resourceManaged).value
    val assetsLocation: File = (Assets / sourceDirectory).value
    val assetsWebJarsLocation: File = (Assets / webJarsDirectory).value
    val projectRoot: File = baseDirectory.value
    val webpackSourceDirectory: File = (WebpackKeys.webpack / sourceDirectory).value

    val webpackConfigs: Seq[autoImport.WebpackConfig] =
      (WebpackKeys.webpack / WebpackKeys.configurations).value

    val cacheDirectory = (Assets / streams).value.cacheDirectory

    val nodeModules: File =
      if (nodeModulesPath.toPath.isAbsolute()) nodeModulesPath
      else webpackSourceDirectory.toPath().resolve(nodeModulesPath.toPath()).toFile()

    webpackConfigs
      .filter(config => config.enabled && (acceptedIds.isEmpty || acceptedIds.contains(config.id)))
      .flatMap { config =>
        val configTag = s"$tag[${config.id}]"

        val inputFiles: Set[File] =
          config.inputs.map { path =>
            if (path.startsWith("assets:"))
              assetsLocation.toPath.resolve(path.drop(7)).toFile()
            else if (path.startsWith("webjar:"))
              assetsWebJarsLocation.toPath.resolve(path.drop(7)).toFile()
            else webpackSourceDirectory.toPath().resolve(path).toFile()
          }.toSet

        val configFile: File =
          webpackSourceDirectory.toPath.resolve(config.configFilePath).toFile()

        val outputFileName: String = config.output

        val sources: Seq[File] =
          ((webpackSourceDirectory ** (config.includeFilter -- (WebpackKeys.webpack / excludeFilter).value)).get
            .filter(_.isFile) ++ inputFiles ++ Seq(configFile)).distinct

        val globalHash = new String(
          Hash(
            Seq(
              configTag,
              readAndClose(configFile),
              config.toString
            ).mkString("--")
          )
        )

        val fileHasherIncludingOptions = OpInputHasher[File] { f =>
          OpInputHash.hashString(
            Seq(
              f.getCanonicalPath,
              globalHash
            ).mkString("--")
          )
        }

        val results =
          incremental.syncIncremental(cacheDirectory / configTag, sources) { modifiedSources =>
            if (modifiedSources.nonEmpty) {
              logger.info(s"""
                |$configTag Detected ${modifiedSources.size} changed files:
                |$configTag - ${modifiedSources
                .map(f => f.relativeTo(projectRoot).getOrElse(f).toString())
                .mkString(s"\n$configTag - ")}
           """.stripMargin.trim)

              Webpack
                .execute(
                  configTag,
                  projectRoot,
                  webpackBinary,
                  configFile,
                  inputFiles,
                  outputFileName,
                  outputDirectory,
                  webpackSourceDirectory,
                  assetsLocation,
                  assetsWebJarsLocation,
                  nodeModules,
                  config.additionalProperties,
                  logger
                )
                .fold(
                  error => {
                    CompileProblems.report(
                      reporter = webpackReporter,
                      problems = Seq(new Problem {
                        override def category() = ""
                        override def severity() = Severity.Error
                        override def message() = error
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
                    )

                    (modifiedSources.map(file => file -> OpFailure).toMap, Seq.empty)
                  },
                  result => {
                    val opResults =
                      (if (modifiedSources.exists(f => f.getCanonicalPath == result.configFile.getCanonicalPath)) {
                         Map(result.configFile -> OpSuccess(result.filesRead, result.filesWritten))
                       } else Map.empty) ++ modifiedSources
                        .filterNot(modifiedSource =>
                          result.configFile.getCanonicalPath.contains(modifiedSource.getCanonicalPath)
                        )
                        .map(modifiedSource =>
                          modifiedSource -> OpSuccess(
                            Set(modifiedSource),
                            if (
                              result.filesRead
                                .exists(file => file.getCanonicalPath == modifiedSource.getCanonicalPath())
                            )
                              result.filesWritten
                            else Set.empty
                          )
                        )
                        .toMap

                    (opResults, result.filesWritten)
                  }
                )
            } else {
              logger.info(s"$configTag No changes to re-compile")
              (Map.empty, Seq.empty)
            }

          }(fileHasherIncludingOptions)

        // Return the dependencies
        results._1.toSeq ++ results._2
      }
  }

  final case class WebpackExecutionResult(
    configFile: File,
    filesRead: Set[File],
    filesWritten: Set[File]
  )

  object Webpack {

    def execute(
      tag: String,
      projectRoot: File,
      webpackBinary: File,
      configFile: File,
      inputFiles: Set[File],
      outputFileName: String,
      outputDirectory: File,
      sourceDirectory: File,
      assetsLocation: File,
      webjarsLocation: File,
      nodeModules: File,
      additionalEnvironmentProperties: Map[String, String],
      logger: ManagedLogger
    ): Either[String, WebpackExecutionResult] = {
      import sbt._

      if (!webpackBinary.exists() || !webpackBinary.isFile())
        Left(s"webpack binary file ${webpackBinary.getPath()} not found")
      else if (!configFile.exists() || !configFile.isFile())
        Left(s"webpack config file ${configFile.getPath()} not found")
      else if (!nodeModules.exists() || !nodeModules.isDirectory())
        Left(s"node_modules folder ${nodeModules.getPath()} not found")
      else if (!inputFiles.forall(f => f.exists() && f.isFile()))
        if (inputFiles.size == 1)
          Left(s"input file ${inputFiles.map(_.toPath()).mkString(", ")} not found")
        else
          Left(s"some input file(s) of ${inputFiles.map(_.toPath()).mkString(", ")} not found")
      else {

        val entriesEnvs = inputFiles.zipWithIndex.flatMap { case (file, index) =>
          Seq("--env", s"""entry.$index=${file.getAbsolutePath()}""")
        }

        val additionalEnvs = additionalEnvironmentProperties.toSeq
          .flatMap { case (key, value) =>
            Seq("--env", s"$key=$value")
          }

        val cmd = Seq(
          s"""${webpackBinary.getCanonicalPath}""",
          "--config",
          s"""${configFile.getAbsolutePath()}""",
          "--env",
          s"""output.path=${outputDirectory.getAbsolutePath()}""",
          "--env",
          s"""output.filename=$outputFileName""",
          "--env",
          s"""assets.path=${assetsLocation.getAbsolutePath()}""",
          "--env",
          s"""webjars.path=${webjarsLocation.getAbsolutePath()}"""
        ) ++ entriesEnvs ++ additionalEnvs

        logger.info(s"$tag Running command ${AnsiColor.CYAN}${cmd.mkString(" ")}${AnsiColor.RESET}")

        val (exitCode, output) =
          Shell.execute(cmd, sourceDirectory, "NODE_PATH" -> nodeModules.getCanonicalPath)

        val success = exitCode == 0

        if (success) {
          val processedFiles: Seq[File] =
            output
              .filter(s => s.contains("[built]") && (s.startsWith(".") || s.startsWith("|")))
              .map(_.dropWhile(_ == '|').dropWhile(_ == ' ').takeWhile(_ != ' '))
              .sorted
              .map(path => sourceDirectory.toPath().resolve(path).toFile.getCanonicalFile())

          logger.info(
            processedFiles
              .map(file => file.relativeTo(projectRoot).getOrElse(file))
              .mkString(
                s"$tag Processed files:\n$tag - ${AnsiColor.GREEN}",
                s"${AnsiColor.RESET}\n$tag - ${AnsiColor.GREEN}",
                s"${AnsiColor.RESET}\n"
              )
          )

          val generatedAssets: Seq[File] =
            output
              .filter(s => s.startsWith("asset ") || s.startsWith("sourceMap "))
              .map(_.dropWhile(_ != ' ').drop(1).takeWhile(_ != ' '))
              .sorted
              .map(path => outputDirectory.toPath().resolve(path).toFile.getCanonicalFile())

          logger.info(
            generatedAssets
              .map(file => file.relativeTo(projectRoot).getOrElse(file))
              .mkString(
                s"$tag Generated assets:\n$tag - ${AnsiColor.MAGENTA}",
                s"${AnsiColor.RESET}\n$tag - ${AnsiColor.MAGENTA}",
                s"${AnsiColor.RESET}\n"
              )
          )

          Right(
            WebpackExecutionResult(
              configFile = configFile,
              filesRead = processedFiles.toSet,
              filesWritten = generatedAssets.toSet
            )
          )
        } else {
          logger.error(
            output.map(s => s"$tag $s").mkString("\n")
          )
          Left(s"Error while executing ${cmd.mkString(" ")}")
        }
      }
    }
  }

  object Shell {
    def execute(cmd: Seq[String], cwd: File, envs: (String, String)*): (Int, Seq[String]) = {
      var output = Vector.empty[String]
      val process = Process(cmd, cwd, envs: _*)
      val exitCode = process.!(ProcessLogger(s => output = output.:+(s.trim)))
      (exitCode, output)
    }
  }

}
