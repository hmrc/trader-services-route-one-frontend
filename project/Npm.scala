import sbt.{Command, File}

import scala.sys.process.{Process, ProcessBuilder}

object Npm {

  def npmCommand(base: File) = Command.args("npm", "<npm-command>") { (state, args) =>
    val exitValue = process(base, args: _*) !;
    if (exitValue != 0)
      throw new Exception(s"NPM command failed: ${args.mkString(" ")}")
    else state
  }

  def npmProcess(failureMessage: String)(base: File, args: String*): Int = {
    val processBuilder = process(base, args: _*)
    val exitValue      = processBuilder.run().exitValue()
    if (exitValue != 0) {
      throw new Exception(failureMessage)
    } else exitValue
  }

  private def process(base: File, args: String*): ProcessBuilder =
    // this hasn't actually been tested on windows, so it might not work :)
    if (sys.props("os.name").toLowerCase contains "windows") {
      Process("cmd" :: "/c" :: "npm" :: args.toList, base)
    } else {
      Process("npm" :: args.toList, base)
    }

}
