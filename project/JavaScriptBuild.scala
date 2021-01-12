import com.typesafe.sbt.packager.Keys._
import sbt.Keys._
import sbt._

/**
  * Enables running NPM scripts, if a package.json file exists in the `javaScriptDirectory`.
  * This directory is explicitly configured below.
  *
  * Example usage:
  *     sbt "npm run build"
  *     sbt "npm test"
  *
  * This assumes that NPM is available, up-to-date, configured appropriately, etc. It makes not guarantees apart
  * from being able to invoke NPM with arguments.
  *
  * Additionally to this, there is some wiring to make 'npm test' run whenever 'sbt test' is run, and to
  * run 'npm run build' when doing the dist command (which is part of the distTgz command run in Jenkins)
  */
object JavaScriptBuild {

  val javaScriptDirectory = SettingKey[File]("javascriptDirectory")

  // Extra tasks, to make configuring Jenkins easier.
  val npmInstall  = TaskKey[Int]("npm-install")
//  val npmTest     = TaskKey[Int]("npm-test")
  val npmBuild    = TaskKey[Int]("npm-build")

  val javaScriptSettings: Seq[Setting[_]] = Seq(
    javaScriptDirectory := (baseDirectory in Compile) {
      _ / "app" / "assets"
    }.value,
    // this enables 'sbt "npm <args>"' commands
    commands ++= javaScriptDirectory(base => Seq(Npm.npmCommand(base))).value,
    npmInstall := Npm.npmProcess("npm ci failed")(javaScriptDirectory.value, "ci"),
    npmBuild := Npm.npmProcess("npm build failed")(javaScriptDirectory.value, "run", "build"),
    npmBuild := (npmBuild dependsOn npmInstall).value,
//    npmTest := Npm.npmProcess("npm test failed")(javaScriptDirectory.value, "test"),
//    npmTest := (npmTest dependsOn npmBuild).value,
//    (test in Test) := (test in Test).dependsOn(npmTest).value,
    dist := (dist dependsOn npmBuild).value
  )
}
