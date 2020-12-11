import play.sbt.PlayRunHook
import sbt._

import scala.sys.process.Process

object Webpack {
  def apply(base: File): PlayRunHook = {

    object WebpackProcess extends PlayRunHook {

      var watchProcess: Option[Process] = None
      val log = ConsoleLogger()

      override def beforeStarted(): Unit = {
        log.info("run npm install...")
        Process("npm install", base).!

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
