package uk.gov.hmrc.traderservices.support

import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.libs.json.Format
import play.api.libs.ws.WSClient
import play.api.mvc.{Cookies, Session, SessionCookieBaker}
import uk.gov.hmrc.cache.repository.CacheMongoRepository
import uk.gov.hmrc.crypto.{ApplicationCrypto, PlainText}
import uk.gov.hmrc.play.bootstrap.frontend.filters.crypto.SessionCookieCrypto
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyStateFormats
import uk.gov.hmrc.traderservices.services.{AmendCaseJourneyService, MongoDBCachedJourneyService}
import uk.gov.hmrc.traderservices.wiring.AppConfig

import java.util.UUID

abstract class ServerISpec extends BaseISpec with GuiceOneServerPerSuite {

  override def fakeApplication: Application = appBuilder.build()

  lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]
  lazy val appConfig = fakeApplication.injector.instanceOf[AppConfig]
  lazy val sessionCookieBaker: SessionCookieBaker = app.injector.instanceOf[SessionCookieBaker]
  lazy val sessionCookieCrypto: SessionCookieCrypto = app.injector.instanceOf[SessionCookieCrypto]

  case class JourneyId(value: String = UUID.randomUUID().toString)

  // define test service capable of manipulating journey state
  lazy val journey = new TestJourneyService[JourneyId]
    with AmendCaseJourneyService[JourneyId] with MongoDBCachedJourneyService[JourneyId] {

    override lazy val cacheMongoRepository = app.injector.instanceOf[CacheMongoRepository]
    override lazy val applicationCrypto = app.injector.instanceOf[ApplicationCrypto]

    override val stateFormats: Format[model.State] =
      AmendCaseJourneyStateFormats.formats

    override def getJourneyId(journeyId: JourneyId): Option[String] = Some(journeyId.value)
  }

  val baseUrl: String = s"http://localhost:$port/send-documents-for-customs-check"

  def request(path: String)(implicit journeyId: JourneyId) = {
    val sessionCookie = sessionCookieBaker.encodeAsCookie(Session(Map(journey.journeyKey -> journeyId.value)))

    wsClient
      .url(s"$baseUrl$path")
      .withHttpHeaders(
        play.api.http.HeaderNames.COOKIE -> Cookies.encodeCookieHeader(
          Seq(
            sessionCookie.copy(value = sessionCookieCrypto.crypto.encrypt(PlainText(sessionCookie.value)).value)
          )
        )
      )
  }

  def requestWithoutJourneyId(path: String) =
    wsClient
      .url(s"$baseUrl$path")

}
