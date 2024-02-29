package test.uk.gov.hmrc.traderservices.controllers.internal

import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json._
import test.uk.gov.hmrc.traderservices.controllers.AmendCaseJourneyISpecSetup
import test.uk.gov.hmrc.traderservices.stubs.UpscanInitiateStubs
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.utils.SHA256

import java.time.ZonedDateTime
import scala.concurrent.ExecutionContext.Implicits.global
class UpscanCallBackAmendCaseControllerISpec extends AmendCaseJourneyISpecSetup with UpscanInitiateStubs {
  import journey.model.FileUploadState._

  override def uploadMultipleFilesFeature: Boolean = false
  override def requireEnrolmentFeature: Boolean = true
  override def requireOptionalTransportFeature: Boolean = false

  "POST /callback-from-upscan/add/journey/:journeyId" should {
    "return 400 if callback body invalid" in {
      val nonce = Nonce.random
      journey.setState(
        UploadMultipleFiles(
          exampleAmendCaseModel,
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Posted(nonce, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
            )
          )
        )
      )
      val result =
        await(
          request(s"/callback-from-upscan/add/journey/${journeyId.value}/$nonce", true)
            .withHttpHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
            .post(
              Json.obj(
                "reference" -> JsString("2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
        )

      result.status shouldBe 400
      journey.getState should beState(
        UploadMultipleFiles(
          exampleAmendCaseModel,
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Posted(nonce, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
            )
          )
        )
      )
    }

    "modify file status to Accepted and return 204" in {
      val nonce = Nonce.random
      journey.setState(
        UploadMultipleFiles(
          exampleAmendCaseModel,
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Posted(nonce, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
            )
          )
        )
      )
      val result =
        await(
          request(s"/callback-from-upscan/add/journey/${SHA256.compute(journeyId.value)}/$nonce", true)
            .withHttpHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
            .post(
              Json.obj(
                "reference"   -> JsString("2b72fe99-8adf-4edb-865e-622ae710f77c"),
                "fileStatus"  -> JsString("READY"),
                "downloadUrl" -> JsString("https://foo.bar/XYZ123/foo.pdf"),
                "uploadDetails" -> Json.obj(
                  "uploadTimestamp" -> JsString("2018-04-24T09:30:00Z"),
                  "checksum"        -> JsString("396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100"),
                  "fileName"        -> JsString("foo.pdf"),
                  "fileMimeType"    -> JsString("application/pdf"),
                  "size"            -> JsNumber(1)
                )
              )
            )
        )

      result.status shouldBe 204
      journey.getState should beState(
        UploadMultipleFiles(
          exampleAmendCaseModel,
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Accepted(
                nonce,
                Timestamp.Any,
                "2b72fe99-8adf-4edb-865e-622ae710f77c",
                "https://foo.bar/XYZ123/foo.pdf",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "foo.pdf",
                "application/pdf",
                Some(1)
              )
            )
          )
        )
      )
    }

    "keep file status Accepted and return 204" in {
      val nonce = Nonce.random
      journey.setState(
        UploadMultipleFiles(
          exampleAmendCaseModel,
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Accepted(
                nonce,
                Timestamp.Any,
                "2b72fe99-8adf-4edb-865e-622ae710f77c",
                "https://foo.bar/XYZ123/foo.pdf",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "foo.pdf",
                "application/pdf",
                Some(1)
              )
            )
          )
        )
      )
      val result =
        await(
          request(s"/callback-from-upscan/add/journey/${SHA256.compute(journeyId.value)}/$nonce", true)
            .withHttpHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
            .post(
              Json.obj(
                "reference"   -> JsString("2b72fe99-8adf-4edb-865e-622ae710f77c"),
                "fileStatus"  -> JsString("READY"),
                "downloadUrl" -> JsString("https://foo.bar/XYZ123/foo.pdf"),
                "uploadDetails" -> Json.obj(
                  "uploadTimestamp" -> JsString("2018-04-24T09:30:00Z"),
                  "checksum"        -> JsString("396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100"),
                  "fileName"        -> JsString("foo.pdf"),
                  "fileMimeType"    -> JsString("application/pdf"),
                  "size"            -> JsNumber(1)
                )
              )
            )
        )

      result.status shouldBe 204
      journey.getState should beState(
        UploadMultipleFiles(
          exampleAmendCaseModel,
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Accepted(
                nonce,
                Timestamp.Any,
                "2b72fe99-8adf-4edb-865e-622ae710f77c",
                "https://foo.bar/XYZ123/foo.pdf",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "foo.pdf",
                "application/pdf",
                Some(1)
              )
            )
          )
        )
      )
    }

    "change nothing if nonce not matching" in {
      val nonce = Nonce.random
      journey.setState(
        UploadMultipleFiles(
          exampleAmendCaseModel,
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Posted(nonce, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
            )
          )
        )
      )
      val result =
        await(
          request(s"/callback-from-upscan/add/journey/${SHA256.compute(journeyId.value)}/${Nonce.random}", true)
            .withHttpHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
            .post(
              Json.obj(
                "reference"   -> JsString("2b72fe99-8adf-4edb-865e-622ae710f77c"),
                "fileStatus"  -> JsString("READY"),
                "downloadUrl" -> JsString("https://foo.bar/XYZ123/foo.pdf"),
                "uploadDetails" -> Json.obj(
                  "uploadTimestamp" -> JsString("2018-04-24T09:30:00Z"),
                  "checksum"        -> JsString("396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100"),
                  "fileName"        -> JsString("foo.pdf"),
                  "fileMimeType"    -> JsString("application/pdf"),
                  "size"            -> JsNumber(1)
                )
              )
            )
        )

      result.status shouldBe 204
      journey.getState should beState(
        UploadMultipleFiles(
          exampleAmendCaseModel,
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Posted(nonce, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
            )
          )
        )
      )
    }
  }
}
