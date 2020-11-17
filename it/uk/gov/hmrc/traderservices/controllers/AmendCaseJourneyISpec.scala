package uk.gov.hmrc.traderservices.controllers

import java.time.LocalDate
import java.util.UUID

import play.api.Application
import play.api.libs.json.Format
import play.api.libs.ws.WSClient
import play.api.mvc.{Cookies, Session, SessionCookieBaker}
import uk.gov.hmrc.cache.repository.CacheMongoRepository
import uk.gov.hmrc.crypto.{ApplicationCrypto, PlainText}
import uk.gov.hmrc.play.bootstrap.frontend.filters.crypto.SessionCookieCrypto
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyStateFormats
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.services.{AmendCaseJourneyService, MongoDBCachedJourneyService}
import uk.gov.hmrc.traderservices.stubs.{TraderServicesApiStubs, UpscanInitiateStubs}
import uk.gov.hmrc.traderservices.support.{ServerISpec, TestJourneyService}
import uk.gov.hmrc.traderservices.support.TestData

import scala.concurrent.ExecutionContext.Implicits.global
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.time.LocalDateTime
import uk.gov.hmrc.traderservices.models.ExportContactInfo
import java.time.ZonedDateTime

class AmendCaseJourneyISpec extends AmendCaseJourneyISpecSetup with TraderServicesApiStubs with UpscanInitiateStubs {

  import journey.model.State._

  val dateTime = LocalDateTime.now()

  "AmendCaseJourneyController" when {

    "GET /trader-services/pre-clearance/amend/case-reference-number" should {
      "show enter case reference number page" in {
        implicit val journeyId: JourneyId = JourneyId()
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/amend/case-reference-number").get())

        result.status shouldBe 200
        result.body should include(
          htmlEscapedMessage("view.case-reference-number.title") + " - " + htmlEscapedMessage(
            "site.serviceName"
          ) + " - " + htmlEscapedMessage("site.govuk")
        )
        result.body should include(htmlEscapedMessage("view.case-reference-number.heading"))
        journey.getState shouldBe EnterCaseReferenceNumber(None)
      }
    }

    "POST /trader-services/pre-clearance/amend/case-reference-number" should {
      "sumbit case reference number and show next page" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(EnterCaseReferenceNumber(None))
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "caseReferenceNumber" -> "PC12010081330XGBNZJO04"
        )

        val result = await(request("/pre-clearance/amend/case-reference-number").post(payload))

        result.status shouldBe 200
        journey.getState shouldBe SelectTypeOfAmendment("PC12010081330XGBNZJO04")
      }
    }
  }
}

trait AmendCaseJourneyISpecSetup extends ServerISpec {

  override def fakeApplication: Application = appBuilder.build()

  lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]
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

  val baseUrl: String = s"http://localhost:$port/trader-services"

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
