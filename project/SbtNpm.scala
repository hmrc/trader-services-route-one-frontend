import sbt._
import sbt.Keys._
import xsbti.{Position, Problem, Severity}
import com.typesafe.sbt.web._
import com.typesafe.sbt.web.incremental._
import com.typesafe.sbt.packager
import scala.io.Source
import scala.sys.process.{Process, ProcessBuilder}
import java.io.PrintWriter
import java.nio.file.Paths
import sbt.internal.util.ManagedLogger

/** Enables running NPM scripts, if a package.json file exists in the `packageJsonDirectory`. This directory is
  * explicitly configured below.
  *
  * Example usage: sbt "npm run build" sbt "npm test"
  *
  * This assumes that NPM is available, up-to-date, configured appropriately, etc. It makes not guarantees apart from
  * being able to invoke NPM with arguments.
  *
  * Additionally to this, there is some wiring to make 'npm test' run whenever 'sbt test' is run, and to run 'npm run
  * build' when doing the dist command (which is part of the distTgz command run in Jenkins)
  */
object SbtNpm extends AutoPlugin {

  override def requires = SbtWeb

  override def trigger = AllRequirements

  object autoImport {
    object NpmKeys {
      val packageJsonDirectory = SettingKey[File]("packageJsonDirectory", "Root directory of package.json")
      val npmInstall = TaskKey[Int]("npm-install", "Run npm install")
      val npmTest = TaskKey[Int]("npm-test", "Run npm test")
    }
  }

  import SbtWeb.autoImport._
  import autoImport.NpmKeys._

  override def projectSettings: Seq[Setting[_]] =
    inConfig(Assets)(
      Seq(
        packageJsonDirectory := (Assets / sourceDirectory).value,
        // this enables 'sbt "npm <args>"' commands
        commands ++= packageJsonDirectory(base => Seq(npmCommand(base))).value,
        npmInstall := {
          val logger: ManagedLogger = (Assets / streams).value.log
          val projectRoot: File = baseDirectory.value
          val nodeModulesDir = packageJsonDirectory.value / "node_modules"
          if (nodeModulesDir.exists() && nodeModulesDir.isDirectory()) {
            logger.info(
              s"[sbt-npm] Folder ${nodeModulesDir.relativeTo(projectRoot).getOrElse(nodeModulesDir)} already exists."
            )
            0
          } else {
            logger.info(
              s"[sbt-npm] Running clean install of node modules in ${nodeModulesDir.relativeTo(projectRoot).getOrElse(nodeModulesDir)}"
            )
            npmProcess("npm ci failed")(packageJsonDirectory.value, "ci")
          }
        },
        npmTest := {
          npmProcess("npm test failed")(packageJsonDirectory.value, "test")
        },
        npmTest := (npmTest dependsOn npmInstall).value,
        //    (test in Test) := (test in Test).dependsOn(npmTest).value,
        packager.Keys.dist := (packager.Keys.dist dependsOn npmInstall).value
      )
    )

  def npmCommand(base: File) =
    Command.args("npm", "<npm-command>") { (state, args) =>
      val exitValue = process(base, args: _*) !;
      if (exitValue != 0)
        throw new Exception(s"NPM command failed: ${args.mkString(" ")}")
      else state
    }

  def npmProcess(failureMessage: String)(base: File, args: String*): Int = {
    val processBuilder = process(base, args: _*)
    val exitValue = processBuilder.run().exitValue()
    if (exitValue != 0)
      throw new Exception(failureMessage)
    else exitValue
  }

  private def process(base: File, args: String*): ProcessBuilder =
    // this hasn't actually been tested on windows, so it might not work :)
    if (sys.props("os.name").toLowerCase contains "windows")
      Process("cmd" :: "/c" :: "npm" :: args.toList, base)
    else
      Process("npm" :: args.toList, base)

}
