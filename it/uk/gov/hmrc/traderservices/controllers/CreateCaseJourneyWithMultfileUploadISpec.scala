package uk.gov.hmrc.traderservices.controllers

import play.api.libs.json.Format
import play.api.mvc.Session
import uk.gov.hmrc.crypto.{ApplicationCrypto, PlainText}
import uk.gov.hmrc.traderservices.connectors.TraderServicesResult
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.FileUploadHostData
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyStateFormats
import uk.gov.hmrc.traderservices.models.{ExportContactInfo, _}
import uk.gov.hmrc.traderservices.repository.CacheRepository
import uk.gov.hmrc.traderservices.services.{CreateCaseJourneyService, MongoDBCachedJourneyService}
import uk.gov.hmrc.traderservices.stubs.{PdfGeneratorStubs, TraderServicesApiStubs, UpscanInitiateStubs}
import uk.gov.hmrc.traderservices.support.{ServerISpec, TestData, TestJourneyService}
import uk.gov.hmrc.traderservices.views.CommonUtilsHelper.DateTimeUtilities

import java.time.{LocalDate, LocalDateTime, ZonedDateTime}
import java.time.temporal.{ChronoField, ChronoUnit}
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.ws.DefaultWSCookie
import scala.util.Random
import play.api.libs.json.JsValue
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import com.github.tomakehurst.wiremock.client.WireMock
import akka.actor.ActorSystem
import uk.gov.hmrc.traderservices.connectors.FileTransferResult
import play.mvc.Http.HeaderNames
import play.api.test.FakeRequest
import play.api.mvc.Cookie
import com.fasterxml.jackson.module.scala.deser.overrides
import java.util.UUID

class CreateCaseJourneyWithMultfileUploadISpec
    extends CreateCaseJourneyISpecSetup with TraderServicesApiStubs with UpscanInitiateStubs with PdfGeneratorStubs {

  import journey.model.FileUploadState._
  import journey.model.State._

  def uploadMultipleFilesFeature: Boolean = true
  def requireEnrolmentFeature: Boolean = true
  def requireOptionalTransportFeature: Boolean = false

  val dateTime = LocalDateTime.now()

  implicit val journeyId: JourneyId = JourneyId()

  "CreateCaseJourneyController" when {

    "preferUploadMultipleFiles" should {
      "return false when jsenabled cookie NOT set" in {
        controller.preferUploadMultipleFiles(FakeRequest()) shouldBe false
      }

      "return true when jsenabled cookie set and uploadMultipleFilesFeature flag set" in {
        controller.preferUploadMultipleFiles(
          fakeRequest(Cookie(controller.COOKIE_JSENABLED, "true"))
        ) shouldBe true
      }

    }

  }

}
