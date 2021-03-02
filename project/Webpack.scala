import play.sbt.PlayRunHook
import sbt._

import scala.sys.process.Process
import scala.util.Properties

object Webpack {
  def apply(base: File): PlayRunHook = {

    object WebpackProcess extends PlayRunHook {

      var watchProcess: Option[Process] = None
      val log = ConsoleLogger()

      override def beforeStarted(): Unit = {
        if (Properties.propOrFalse("isCi")) {
          log.info("Running npm ci...")
          Process("npm ci", base).!
          val exitValue = Process("npm test", base).!

          if (exitValue != 0) {
            throw new Exception("Tests failed")
          }
        }
        else {
          log.info("Running npm install...")
          Process("npm install --no-save", base).!
        }

        log.info("Starting webpack in watch mode...")
        watchProcess = Some(Process("npm start", base).run)
      }

      override def afterStopped(): Unit = {
        log.info("Shutting down webpack...")
        watchProcess.map(p => p.destroy())
        watchProcess = None
      }
    }

    WebpackProcess
  }
}
