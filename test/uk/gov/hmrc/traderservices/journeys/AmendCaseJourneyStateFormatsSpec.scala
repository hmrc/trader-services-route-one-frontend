/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.traderservices.journeys

import play.api.libs.json.{Format, JsResultException, Json}
import uk.gov.hmrc.traderservices.connectors.TraderServicesResult
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.State
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.FileUploadState
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.support.UnitSpec
import uk.gov.hmrc.traderservices.support.JsonFormatTest

import java.time.ZonedDateTime
import scala.util.Random

class AmendCaseJourneyStateFormatsSpec extends UnitSpec {

  implicit val formats: Format[State] = AmendCaseJourneyStateFormats.formats
  val generatedAt = java.time.LocalDateTime.of(2018, 12, 11, 10, 20, 30)

  "AmendCaseJourneyStateFormats" should {
    "serialize and deserialize state" in new JsonFormatTest[State](info) {
      validateJsonFormat(
        """{"state":"Start"}""",
        State.Start
      )
      validateJsonFormat(
        """{"state":"EnterCaseReferenceNumber","properties":{"model":{}}}""",
        State.EnterCaseReferenceNumber()
      )
      validateJsonFormat(
        """{"state":"EnterCaseReferenceNumber","properties":{"model":{"caseReferenceNumber":"PC12010081330XGBNZJO04"}}}""",
        State.EnterCaseReferenceNumber(AmendCaseModel(Some("PC12010081330XGBNZJO04")))
      )
      validateJsonFormat(
        """{"state":"SelectTypeOfAmendment","properties":{"model":{"caseReferenceNumber":"PC12010081330XGBNZJO04"}}}""",
        State.SelectTypeOfAmendment(AmendCaseModel(Some("PC12010081330XGBNZJO04")))
      )
      validateJsonFormat(
        """{"state":"EnterResponseText","properties":{"model":{"caseReferenceNumber":"PC12010081330XGBNZJO04","typeOfAmendment":"WriteResponse"}}}""",
        State.EnterResponseText(
          AmendCaseModel(Some("PC12010081330XGBNZJO04"), Some(TypeOfAmendment.WriteResponse))
        )
      )
      val text = Random.alphanumeric.take(1000).mkString
      validateJsonFormat(
        s"""{"state":"AmendCaseConfirmation","properties":{"result":{"caseId":"PC12010081330XGBNZJO04","generatedAt":"${generatedAt.toString}"}}}""".stripMargin,
        State.AmendCaseConfirmation(TraderServicesResult("PC12010081330XGBNZJO04", generatedAt))
      )
      val fileUploadHostData =
        AmendCaseModel(Some("PC12010081330XGBNZJO04"), Some(TypeOfAmendment.WriteResponse), Some(text))
      validateJsonFormat(
        s"""{"state":"UploadFile","properties":{
           |"hostData":{"caseReferenceNumber":"PC12010081330XGBNZJO04","typeOfAmendment":"WriteResponse","responseText":"$text"},
           |"reference":"foo-bar-ref",
           |"uploadRequest":{"href":"https://foo.bar","fields":{}},
           |"fileUploads":{"files":[
           |{"Initiated":{"nonce":0,"timestamp":0,"reference":"foo1"}},
           |{"Posted":{"nonce":0,"timestamp":0,"reference":"foo3"}},
           |{"Accepted":{"nonce":0,"timestamp":0,"reference":"foo4","url":"https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
           |"uploadTimestamp":"2018-04-24T09:30:00Z","checksum":"396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100","fileName":"test.pdf","fileMimeType":"application/pdf","fileSize":4567890}},
           |{"Failed":{"nonce":0,"timestamp":0,"reference":"foo2","details":{"failureReason":"QUARANTINE","message":"some reason"}}}
           |]},"maybeUploadError":{"FileVerificationFailed":{"details":{"failureReason":"QUARANTINE","message":"some reason"}}}}}""".stripMargin,
        FileUploadState.UploadFile(
          fileUploadHostData,
          "foo-bar-ref",
          UploadRequest(href = "https://foo.bar", fields = Map.empty),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo1"),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo3"),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "foo4",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                4567890
              ),
              FileUpload
                .Failed(
                  Nonce.Any,
                  Timestamp.Any,
                  "foo2",
                  UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
                )
            )
          ),
          Some(FileVerificationFailed(UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")))
        )
      )
      validateJsonFormat(
        s"""{"state":"UploadFile","properties":{
           |"hostData":{"caseReferenceNumber":"PC12010081330XGBNZJO04","typeOfAmendment":"WriteResponse","responseText":"$text"},
           |"reference":"foo-bar-ref-2",
           |"uploadRequest":{"href":"https://foo.bar","fields":{"amz":"123"}},
           |"fileUploads":{"files":[
           |{"Initiated":{"nonce":0,"timestamp":0,"reference":"foo1"}},
           |{"Accepted":{"nonce":0,"timestamp":0,"reference":"foo4","url":"https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
           |"uploadTimestamp":"2018-04-24T09:30:00Z","checksum":"396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100","fileName":"test.pdf","fileMimeType":"application/pdf","fileSize":4567890}},
           |{"Failed":{"nonce":0,"timestamp":0,"reference":"foo2","details":{"failureReason":"QUARANTINE","message":"some reason"}}},
           |{"Posted":{"nonce":0,"timestamp":0,"reference":"foo3"}}
           |]}}}""".stripMargin,
        FileUploadState.UploadFile(
          fileUploadHostData,
          "foo-bar-ref-2",
          UploadRequest(href = "https://foo.bar", fields = Map("amz" -> "123")),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo1"),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "foo4",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                4567890
              ),
              FileUpload
                .Failed(
                  Nonce.Any,
                  Timestamp.Any,
                  "foo2",
                  UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
                ),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo3")
            )
          )
        )
      )
      validateJsonFormat(
        s"""{"state":"WaitingForFileVerification","properties":{
           |"hostData":{"caseReferenceNumber":"PC12010081330XGBNZJO04","typeOfAmendment":"WriteResponse","responseText":"$text"},
           |"reference":"foo-bar-ref-2",
           |"uploadRequest":{"href":"https://foo.bar","fields":{"amz":"123"}},
           |"currentFileUpload":{"Posted":{"nonce":0,"timestamp":0,"reference":"foo3"}},
           |"fileUploads":{"files":[
           |{"Initiated":{"nonce":0,"timestamp":0,"reference":"foo1"}},
           |{"Accepted":{"nonce":0,"timestamp":0,"reference":"foo4","url":"https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
           |"uploadTimestamp":"2018-04-24T09:30:00Z","checksum":"396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100","fileName":"test.pdf","fileMimeType":"application/pdf","fileSize":4567890}},
           |{"Failed":{"nonce":0,"timestamp":0,"reference":"foo2","details":{"failureReason":"QUARANTINE","message":"some reason"}}},
           |{"Posted":{"nonce":0,"timestamp":0,"reference":"foo3"}}
           |]}}}""".stripMargin,
        FileUploadState.WaitingForFileVerification(
          fileUploadHostData,
          "foo-bar-ref-2",
          UploadRequest(href = "https://foo.bar", fields = Map("amz" -> "123")),
          FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo3"),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo1"),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "foo4",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                4567890
              ),
              FileUpload
                .Failed(
                  Nonce.Any,
                  Timestamp.Any,
                  "foo2",
                  UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
                ),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo3")
            )
          )
        )
      )

      validateJsonFormat(
        s"""{"state":"FileUploaded","properties":{
           |"hostData":{"caseReferenceNumber":"PC12010081330XGBNZJO04","typeOfAmendment":"WriteResponse","responseText":"$text"},
           |"fileUploads":{"files":[
           |{"Initiated":{"nonce":0,"timestamp":0,"reference":"foo1"}},
           |{"Accepted":{"nonce":0,"timestamp":0,"reference":"foo4","url":"https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
           |"uploadTimestamp":"2018-04-24T09:30:00Z","checksum":"396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100","fileName":"test.pdf","fileMimeType":"application/pdf","fileSize":4567890}},
           |{"Failed":{"nonce":0,"timestamp":0,"reference":"foo2","details":{"failureReason":"QUARANTINE","message":"some reason"}}},
           |{"Posted":{"nonce":0,"timestamp":0,"reference":"foo3"}}
           |]},
           |"acknowledged":false}}""".stripMargin,
        FileUploadState.FileUploaded(
          fileUploadHostData,
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo1"),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "foo4",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                4567890
              ),
              FileUpload
                .Failed(
                  Nonce.Any,
                  Timestamp.Any,
                  "foo2",
                  UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
                ),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo3")
            )
          )
        )
      )

      validateJsonFormat(
        s"""{"state":"UploadMultipleFiles","properties":{"hostData":{"caseReferenceNumber":"PC12010081330XGBNZJO04","typeOfAmendment":"WriteResponse","responseText":"$text"},
           |"fileUploads":{"files":[
           |{"Initiated":{"nonce":0,"timestamp":0,"reference":"foo1","uploadRequest":{"href":"https://foo.bar","fields":{"amz":"123"}},"uploadId":"aBc"}},
           |{"Accepted":{"nonce":0,"timestamp":0,"reference":"foo4","url":"https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
           |"uploadTimestamp":"2018-04-24T09:30:00Z","checksum":"396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100","fileName":"test.pdf","fileMimeType":"application/pdf","fileSize":4567890}},
           |{"Failed":{"nonce":0,"timestamp":0,"reference":"foo2","details":{"failureReason":"QUARANTINE","message":"some reason"}}},
           |{"Posted":{"nonce":0,"timestamp":0,"reference":"foo3"}}
           |]}}}""".stripMargin,
        FileUploadState.UploadMultipleFiles(
          fileUploadHostData,
          FileUploads(files =
            Seq(
              FileUpload.Initiated(
                Nonce.Any,
                Timestamp.Any,
                "foo1",
                uploadRequest = Some(UploadRequest(href = "https://foo.bar", fields = Map("amz" -> "123"))),
                uploadId = Some("aBc")
              ),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "foo4",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                4567890
              ),
              FileUpload
                .Failed(
                  Nonce.Any,
                  Timestamp.Any,
                  "foo2",
                  UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
                ),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo3")
            )
          )
        )
      )
    }

    "throw an exception when unknown state" in {
      val json = Json.parse("""{"state":"StrangeState","properties":{}}""")
      an[JsResultException] shouldBe thrownBy {
        json.as[State]
      }
    }

  }
}
