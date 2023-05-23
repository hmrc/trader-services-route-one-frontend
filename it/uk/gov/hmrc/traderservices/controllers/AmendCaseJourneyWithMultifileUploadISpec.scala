package uk.gov.hmrc.traderservices.controllers

import play.api.mvc.Cookie
import play.api.test.FakeRequest
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.stubs.{PdfGeneratorStubs, TraderServicesApiStubs, UpscanInitiateStubs}

import java.time.LocalDateTime

class AmendCaseJourneyWithMultifileUploadISpec
    extends AmendCaseJourneyISpecSetup with TraderServicesApiStubs with UpscanInitiateStubs with PdfGeneratorStubs {

  import journey.model.FileUploadState._
  import journey.model.State._

  val dateTime = LocalDateTime.now()

  override def uploadMultipleFilesFeature: Boolean = true
  override def requireEnrolmentFeature: Boolean = true
  override def requireOptionalTransportFeature: Boolean = false

  "AmendCaseJourneyController" when {

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
      "return /add/file-verification for WaitingForFileVerification" in {

        val state = WaitingForFileVerification(
          exampleAmendCaseModel,
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
          .should(endWith("/add/file-verification"))
      }

      "return workInProgresDeadEndCall for an unsupported state" in {
        controller
          .getCallFor(WorkInProgressDeadEnd)(FakeRequest())
          .shouldBe(controller.workInProgresDeadEndCall)
      }

      "return amend case already submitted" in {
        controller
          .getCallFor(AmendCaseAlreadySubmitted)(FakeRequest())
          .url
          .should(endWith("/add/case-already-submitted"))
      }
    }

    "renderState" should {
      "return NotImplemented for an unsupported state" in {
        controller
          .renderState(WorkInProgressDeadEnd, Nil, None)(FakeRequest())
          .shouldBe(controller.NotImplemented)
      }
    }

  }
}
