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

    "GET /new/export/check-your-answers" should {
      "show the export questions summary page" in {
        val dateTimeOfArrival = dateTime.plusDays(1).truncatedTo(ChronoUnit.MINUTES)
        val state = ExportQuestionsSummary(
          ExportQuestionsStateModel(
            TestData.exportEntryDetails,
            TestData.fullExportQuestions(dateTimeOfArrival),
            Some(
              FileUploads(files =
                Seq(
                  FileUpload.Accepted(
                    Nonce(1),
                    Timestamp.Any,
                    "foo-bar-ref-1",
                    "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                    ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                    "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                    "test.pdf",
                    "application/pdf",
                    Some(4567890)
                  )
                )
              )
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result =
          await(requestWithCookies("/new/export/check-your-answers", controller.COOKIE_JSENABLED -> "true").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.summary.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.summary.heading"))
        result.body should include(routes.CreateCaseJourneyController.showUploadMultipleFiles.url)
        journey.getState shouldBe state
      }
    }

    "GET /new/import/check-your-answers" should {
      "show the import questions summary page" in {
        val dateTimeOfArrival = dateTime.plusDays(1).truncatedTo(ChronoUnit.MINUTES)
        val state = ImportQuestionsSummary(
          ImportQuestionsStateModel(
            TestData.importEntryDetails,
            TestData.fullImportQuestions(dateTimeOfArrival),
            Some(
              FileUploads(files =
                Seq(
                  FileUpload.Accepted(
                    Nonce(1),
                    Timestamp.Any,
                    "foo-bar-ref-1",
                    "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                    ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                    "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                    "test.pdf",
                    "application/pdf",
                    Some(4567890)
                  )
                )
              )
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result =
          await(requestWithCookies("/new/import/check-your-answers", controller.COOKIE_JSENABLED -> "true").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.summary.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.summary.heading"))
        result.body should include(routes.CreateCaseJourneyController.showUploadMultipleFiles.url)
        journey.getState shouldBe state
      }
    }

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

    "POST /new/import/contact-information" should {
      "go to multi-file upload page when an email submitted" in {
        journey.setState(
          AnswerImportQuestionsContactInfo(
            ImportQuestionsStateModel(
              EntryDetails(EPU(235), EntryNumber("111111X"), today),
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route6),
                priorityGoods = Some(ImportPriorityGoods.HumanRemains),
                freightType = Some(ImportFreightType.Air)
              )
            )
          )
        )
        val callbackUrl =
          appConfig.baseInternalCallbackUrl + s"/send-documents-for-customs-check/new/journey/${journeyId.value}/callback-from-upscan"
        givenUpscanInitiateSucceeds(callbackUrl)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "contactEmail" -> "someone@email.com"
        )

        val result = await(
          requestWithCookies("/new/import/contact-information", controller.COOKIE_JSENABLED -> "true")
            .post(payload)
        )

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-multiple-files.title"))
        result.body should include(htmlEscapedMessage("view.upload-multiple-files.heading"))

        journey.getState shouldBe UploadMultipleFiles(
          hostData = FileUploadHostData(
            EntryDetails(EPU(235), EntryNumber("111111X"), today),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route6),
              priorityGoods = Some(ImportPriorityGoods.HumanRemains),
              freightType = Some(ImportFreightType.Air),
              contactInfo = Some(ImportContactInfo(contactEmail = "someone@email.com"))
            )
          ),
          fileUploads = FileUploads()
        )
      }
    }

    "POST /new/export/contact-information" should {
      "go to multi-file upload page when an email submitted" in {
        journey.setState(
          AnswerExportQuestionsContactInfo(
            ExportQuestionsStateModel(
              EntryDetails(EPU(235), EntryNumber("A11111X"), today),
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route6),
                priorityGoods = Some(ExportPriorityGoods.HumanRemains),
                freightType = Some(ExportFreightType.Air)
              )
            )
          )
        )
        val callbackUrl =
          appConfig.baseInternalCallbackUrl + s"/send-documents-for-customs-check/new/journey/${journeyId.value}/callback-from-upscan"
        givenUpscanInitiateSucceeds(callbackUrl)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "contactEmail" -> "someone@email.com"
        )

        val result = await(
          requestWithCookies("/new/export/contact-information", controller.COOKIE_JSENABLED -> "true")
            .post(payload)
        )

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-multiple-files.title"))
        result.body should include(htmlEscapedMessage("view.upload-multiple-files.heading"))

        journey.getState shouldBe UploadMultipleFiles(
          hostData = FileUploadHostData(
            EntryDetails(EPU(235), EntryNumber("A11111X"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HumanRemains),
              freightType = Some(ExportFreightType.Air),
              contactInfo = Some(ExportContactInfo(contactEmail = "someone@email.com"))
            )
          ),
          fileUploads = FileUploads()
        )
      }

    }

  }

}
