package uk.gov.hmrc.traderservices.controllers

import play.api.mvc.Cookie
import play.api.test.FakeRequest
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.FileUploadHostData
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.stubs.{PdfGeneratorStubs, TraderServicesApiStubs, UpscanInitiateStubs}
import uk.gov.hmrc.traderservices.support.TestData
import uk.gov.hmrc.traderservices.utils.SHA256

import java.time.ZonedDateTime
import scala.concurrent.ExecutionContext.Implicits.global

class CreateCaseJourneyWithMultfileUploadISpec
    extends CreateCaseJourneyISpecSetup with TraderServicesApiStubs with UpscanInitiateStubs with PdfGeneratorStubs {

  import journey.model.CreateCaseJourneyState._
  import journey.model.FileUploadState._

  def uploadMultipleFilesFeature: Boolean = true
  def requireEnrolmentFeature: Boolean = true
  def requireOptionalTransportFeature: Boolean = false

  "CreateCaseJourneyController" when {

    "GET /new/export/check-your-answers" should {
      "show the export questions summary page" in {

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

    "getCallFor" should {
      "return /new/file-verification for WaitingForFileVerification" in {

        val state = WaitingForFileVerification(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          "2b72fe99-8adf-4edb-865e-622ae710f77c",
          UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
          FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c"),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
            )
          )
        )
        controller
          .getCallFor(state)(FakeRequest())
          .url
          .should(endWith("/new/file-verification"))
      }

      "return workInProgresDeadEndCall for an unsupported state" in {
        controller
          .getCallFor(WorkInProgressDeadEnd)(FakeRequest())
          .shouldBe(controller.workInProgresDeadEndCall)
      }
    }

    "renderState" should {
      "redirect at TurnToAmendCaseJourney the same way as in getCallFor" in {
        controller
          .renderState(TurnToAmendCaseJourney(false), Nil, None)(FakeRequest())
          .shouldBe(controller.Redirect(controller.getCallFor(TurnToAmendCaseJourney(false))(FakeRequest())))
        controller
          .renderState(TurnToAmendCaseJourney(true), Nil, None)(FakeRequest())
          .shouldBe(controller.Redirect(controller.getCallFor(TurnToAmendCaseJourney(true))(FakeRequest())))
      }
      "return NotImplemented for an unsupported state" in {
        controller
          .renderState(WorkInProgressDeadEnd, Nil, None)(FakeRequest())
          .shouldBe(controller.NotImplemented)
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
          appConfig.baseInternalCallbackUrl + s"/send-documents-for-customs-check/callback-from-upscan/new/journey/${SHA256
              .compute(journeyId.value)}"
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
          appConfig.baseInternalCallbackUrl + s"/send-documents-for-customs-check/callback-from-upscan/new/journey/${SHA256
              .compute(journeyId.value)}"
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
