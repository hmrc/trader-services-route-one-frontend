package uk.gov.hmrc.traderservices.support

import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.libs.ws.WSClient
import play.api.mvc.SessionCookieBaker
import uk.gov.hmrc.play.bootstrap.frontend.filters.crypto.SessionCookieCrypto
import uk.gov.hmrc.traderservices.wiring.AppConfig

import java.util.UUID

abstract class ServerISpec extends BaseISpec with GuiceOneServerPerSuite {

  override def fakeApplication: Application = appBuilder.build()

  lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]
  lazy val appConfig = fakeApplication.injector.instanceOf[AppConfig]
  lazy val sessionCookieBaker: SessionCookieBaker = app.injector.instanceOf[SessionCookieBaker]
  lazy val sessionCookieCrypto: SessionCookieCrypto = app.injector.instanceOf[SessionCookieCrypto]

  case class JourneyId(value: String = UUID.randomUUID().toString)

  val baseUrl: String = s"http://localhost:$port/send-documents-for-customs-check"

  def requestWithoutJourneyId(path: String) =
    wsClient
      .url(s"$baseUrl$path")

}
