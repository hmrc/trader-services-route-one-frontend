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

import uk.gov.hmrc.traderservices.connectors._
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.FileUploadState._
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.FileUploadTransitions._
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.State._
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.Transitions._
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.{start => _, _}
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.services.AmendCaseJourneyService
import uk.gov.hmrc.traderservices.support.{InMemoryStore, StateMatchers, UnitSpec}

import java.time.ZonedDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.util.{Random, Try}
class AmendCaseJourneyModelSpec extends UnitSpec with StateMatchers[State] with TestData {

  import scala.concurrent.duration._
  override implicit val defaultTimeout: FiniteDuration = 60 seconds

  // dummy journey context
  case class DummyContext()
  implicit val dummyContext: DummyContext = DummyContext()

  "AmendCaseJourneyModel" when {
    "at state Start" should {
      "go to Start when start" in {
        given(Start) when start should thenGo(Start)
      }

      "go to EnterCaseReferenceNumber when enterCaseReferenceNumber" in {
        given(Start) when enterCaseReferenceNumber should thenGo(EnterCaseReferenceNumber())
      }
    }

    "at state EnterCaseReferenceNumber" should {
      "stay at EnterCaseReferenceNumber when enterCaseReferenceNumber" in {
        given(EnterCaseReferenceNumber()) when enterCaseReferenceNumber should thenGo(
          EnterCaseReferenceNumber()
        )
      }

      "stay at EnterCaseReferenceNumber when enterCaseReferenceNumber and keep former answers" in {
        val model = AmendCaseModel(caseReferenceNumber = Some("PC12010081330XGBNZJO04"))
        given(
          EnterCaseReferenceNumber(model)
        ) when enterCaseReferenceNumber should thenGo(
          EnterCaseReferenceNumber(model)
        )
      }

      "go to SelectTypeOfAmendment when submited case reference number" in {
        given(EnterCaseReferenceNumber()) when submitedCaseReferenceNumber(
          "PC12010081330XGBNZJO04"
        ) should thenGo(
          SelectTypeOfAmendment(AmendCaseModel(caseReferenceNumber = Some("PC12010081330XGBNZJO04")))
        )
      }
    }

    "at state SelectTypeOfAmendment" should {
      "go to EnterResponseText when submited type of amendment WriteResponse" in {
        given(
          SelectTypeOfAmendment(AmendCaseModel(caseReferenceNumber = Some("PC12010081330XGBNZJO04")))
        ) when submitedTypeOfAmendment(uploadMultipleFiles = false)(testUpscanRequest)(mockUpscanInitiate)(
          TypeOfAmendment.WriteResponse
        ) should thenGo(
          EnterResponseText(
            AmendCaseModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.WriteResponse)
            )
          )
        )
      }

      "go to EnterResponseText when submited type of amendment WriteResponseAndUploadDocuments" in {
        given(
          SelectTypeOfAmendment(AmendCaseModel(caseReferenceNumber = Some("PC12010081330XGBNZJO04")))
        ) when submitedTypeOfAmendment(uploadMultipleFiles = false)(testUpscanRequest)(mockUpscanInitiate)(
          TypeOfAmendment.WriteResponseAndUploadDocuments
        ) should thenGo(
          EnterResponseText(
            AmendCaseModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.WriteResponseAndUploadDocuments)
            )
          )
        )
      }

      "go to UploadMultipleFiles when submited type of amendment UploadDocuments and uploadMultipleFiles feature switched on" in {
        given(
          SelectTypeOfAmendment(AmendCaseModel(caseReferenceNumber = Some("PC12010081330XGBNZJO04")))
        ) when submitedTypeOfAmendment(uploadMultipleFiles = true)(testUpscanRequest)(mockUpscanInitiate)(
          TypeOfAmendment.UploadDocuments
        ) should thenGo(
          UploadMultipleFiles(
            AmendCaseModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.UploadDocuments)
            ),
            FileUploads()
          )
        )
      }

      "go to UploadFile when submited type of amendment UploadDocuments and uploadMultipleFiles feature switched off" in {
        given(
          SelectTypeOfAmendment(AmendCaseModel(caseReferenceNumber = Some("PC12010081330XGBNZJO04")))
        ) when submitedTypeOfAmendment(uploadMultipleFiles = false)(testUpscanRequest)(mockUpscanInitiate)(
          TypeOfAmendment.UploadDocuments
        ) should thenGo(
          UploadFile(
            AmendCaseModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.UploadDocuments)
            ),
            "foo-bar-ref",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"         -> "https://foo.bar/callback",
                "successRedirect"     -> "https://foo.bar/success",
                "errorRedirect"       -> "https://foo.bar/failure",
                "minimumFileSize"     -> "0",
                "maximumFileSize"     -> "10485760",
                "expectedContentType" -> "image/jpeg,image/png"
              )
            ),
            FileUploads(files = Seq(FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo-bar-ref")))
          )
        )
      }

      "retreat to EnterCaseReferenceNumber when enterCaseReferenceNumber" in {
        val model = AmendCaseModel(caseReferenceNumber = Some("PC12010081330XGBNZJO04"))
        given(
          SelectTypeOfAmendment(model)
        ) when enterCaseReferenceNumber should thenGo(
          EnterCaseReferenceNumber(model)
        )
      }
    }

    "at state EnterResponseText" should {
      "goto AmendCaseSummary when submitted response text in WriteResponse mode" in {

        val responseText = Random.alphanumeric.take(1000).mkString
        val model = AmendCaseModel(
          caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
          typeOfAmendment = Some(TypeOfAmendment.WriteResponse)
        )
        given(
          EnterResponseText(model)
        ) when submitedResponseText(uploadMultipleFiles = false)(testUpscanRequest)(mockUpscanInitiate)(
          responseText
        ) should thenGo(AmendCaseSummary(model.copy(responseText = Some(responseText))))
      }

      "goto EnterCaseReferenceNumber when submitted response text in WriteResponse mode but case reference number is missing" in {
        val responseText = Random.alphanumeric.take(1000).mkString
        given(
          EnterResponseText(
            AmendCaseModel(
              caseReferenceNumber = None,
              typeOfAmendment = Some(TypeOfAmendment.WriteResponse)
            )
          )
        ) when submitedResponseText(uploadMultipleFiles = false)(testUpscanRequest)(mockUpscanInitiate)(
          responseText
        ) should thenGo(
          EnterCaseReferenceNumber(
            AmendCaseModel(
              caseReferenceNumber = None,
              typeOfAmendment = Some(TypeOfAmendment.WriteResponse),
              responseText = Some(responseText)
            )
          )
        )
      }

      "goto SelectTypeOfAmendment when submitted response text in WriteResponse mode but type of amendment is missing" in {
        val responseText = Random.alphanumeric.take(1000).mkString
        given(
          EnterResponseText(
            AmendCaseModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = None
            )
          )
        ) when submitedResponseText(uploadMultipleFiles = false)(testUpscanRequest)(mockUpscanInitiate)(
          responseText
        ) should thenGo(
          SelectTypeOfAmendment(
            AmendCaseModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = None,
              responseText = Some(responseText)
            )
          )
        )
      }

      "goto UploadMultipleFiles when submited response text in WriteResponseAndUploadDocuments mode and uploadMultipleFiles feature switched on" in {
        val responseText = Random.alphanumeric.take(1000).mkString
        given(
          EnterResponseText(
            AmendCaseModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.WriteResponseAndUploadDocuments)
            )
          )
        ) when submitedResponseText(uploadMultipleFiles = true)(testUpscanRequest)(mockUpscanInitiate)(
          responseText
        ) should thenGo(
          UploadMultipleFiles(
            AmendCaseModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.WriteResponseAndUploadDocuments),
              responseText = Some(responseText)
            ),
            FileUploads()
          )
        )
      }

      "goto UploadFile when submited response text in WriteResponseAndUploadDocuments mode and uploadMultipleFiles feature switched off" in {
        val responseText = Random.alphanumeric.take(1000).mkString
        given(
          EnterResponseText(
            AmendCaseModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.WriteResponseAndUploadDocuments)
            )
          )
        ) when submitedResponseText(uploadMultipleFiles = false)(testUpscanRequest)(mockUpscanInitiate)(
          responseText
        ) should thenGo(
          UploadFile(
            AmendCaseModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.WriteResponseAndUploadDocuments),
              responseText = Some(responseText)
            ),
            "foo-bar-ref",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"         -> "https://foo.bar/callback",
                "successRedirect"     -> "https://foo.bar/success",
                "errorRedirect"       -> "https://foo.bar/failure",
                "minimumFileSize"     -> "0",
                "maximumFileSize"     -> "10485760",
                "expectedContentType" -> "image/jpeg,image/png"
              )
            ),
            FileUploads(files = Seq(FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo-bar-ref")))
          )
        )
      }

      "fail when submited response text in UploadDocuments mode" in {
        val responseText = Random.alphanumeric.take(1000).mkString
        given(
          EnterResponseText(
            AmendCaseModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.UploadDocuments)
            )
          )
        ) shouldFailWhen submitedResponseText(uploadMultipleFiles = false)(testUpscanRequest)(mockUpscanInitiate)(
          responseText
        )
      }

      "retreat to SelectTypeOfAmendment when backToSelectTypeOfAmendment from EnterResponseText" in {
        val model = AmendCaseModel(
          caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
          typeOfAmendment = Some(TypeOfAmendment.WriteResponse)
        )
        given(
          EnterResponseText(model)
        ) when backToSelectTypeOfAmendment should thenGo(
          SelectTypeOfAmendment(model)
        )
      }

      "fail when backToSelectTypeOfAmendment with none typeOfAmendment" in {
        val model = AmendCaseModel(
          caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
          typeOfAmendment = None
        )
        given(
          SelectTypeOfAmendment(model)
        ) shouldFailWhen backToSelectTypeOfAmendment
      }
    }

    val fullAmendCaseStateModel = AmendCaseModel(
      caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
      typeOfAmendment = Some(TypeOfAmendment.WriteResponse),
      responseText = Some(Random.alphanumeric.take(1000).mkString)
    )

    val someFileUploads = FileUploads(files =
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

    "at state UploadMultipleFiles" should {
      "go to AmendCaseSummary when non-empty file uploads and toAmendSummary transition" in {
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            nonEmptyFileUploads
          )
        ) when toAmendSummary should thenGo(
          AmendCaseSummary(
            fullAmendCaseStateModel
              .copy(fileUploads = Some(nonEmptyFileUploads))
          )
        )
      }

      "stay when empty file uploads, and transition toAmendSummary" in {
        val state = UploadMultipleFiles(
          fullAmendCaseStateModel
            .copy(typeOfAmendment = Some(TypeOfAmendment.WriteResponseAndUploadDocuments)),
          FileUploads()
        )
        given(state) when toAmendSummary should thenGo(state)
      }

      "stay when toUploadMultipleFiles transition" in {
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            nonEmptyFileUploads + FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo-2") + FileUpload
              .Posted(Nonce.Any, Timestamp.Any, "foo-3")
          )
        ) when toUploadMultipleFiles should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            nonEmptyFileUploads + FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo-3")
          )
        )
      }

      "initiate new file upload when initiateNextFileUpload transition and empty uploads" in {
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads()
          )
        ) when initiateNextFileUpload("001")(testUpscanRequest)(mockUpscanInitiate) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads() +
              FileUpload.Initiated(
                Nonce.Any,
                Timestamp.Any,
                "foo-bar-ref",
                uploadId = Some("001"),
                uploadRequest = Some(someUploadRequest(testUpscanRequest("")))
              )
          )
        )
      }

      "initiate new file upload when initiateNextFileUpload transition and some uploads exist already" in {
        val fileUploads = FileUploads(files =
          (0 until (maxFileUploadsNumber - 1))
            .map(i => FileUpload.Initiated(Nonce(i), Timestamp.Any, s"foo-bar-ref-$i", uploadId = Some(s"0$i")))
        ) + FileUpload.Rejected(Nonce(9), Timestamp(9), "foo-bar-ref-9", S3UploadError("a", "b", "c"))
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            fileUploads
          )
        ) when initiateNextFileUpload("001")(testUpscanRequest)(mockUpscanInitiate) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            fileUploads +
              FileUpload.Initiated(
                Nonce.Any,
                Timestamp.Any,
                "foo-bar-ref",
                uploadId = Some("001"),
                uploadRequest = Some(someUploadRequest(testUpscanRequest("")))
              )
          )
        )
      }

      "do nothing when initiateNextFileUpload with existing uploadId" in {

        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            nonEmptyFileUploads +
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo-bar-ref", uploadId = Some("101"))
          )
        ) when initiateNextFileUpload("101")(testUpscanRequest)(mockUpscanInitiate) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            nonEmptyFileUploads +
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo-bar-ref", uploadId = Some("101"))
          )
        )
      }

      "do nothing when initiateNextFileUpload and maximum number of uploads already reached" in {

        val fileUploads = FileUploads(files =
          (0 until maxFileUploadsNumber)
            .map(i => FileUpload.Initiated(Nonce(i), Timestamp.Any, s"foo-bar-ref-$i", uploadId = Some(s"0$i")))
        )
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            fileUploads
          )
        ) when initiateNextFileUpload("101")(testUpscanRequest)(mockUpscanInitiate) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            fileUploads
          )
        )
      }

      "mark file upload as POSTED when markUploadAsPosted transition" in {
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when markUploadAsPosted(S3UploadSuccess("foo-bar-ref-2", Some("bucket-123"))) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Posted(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        )
      }

      "do nothing when markUploadAsPosted transition and already in POSTED state" in {
        val state = UploadMultipleFiles(
          fullAmendCaseStateModel,
          FileUploads(files =
            Seq(
              FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
              FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
              FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when markUploadAsPosted(S3UploadSuccess("foo-bar-ref-1", Some("bucket-123"))) should thenGo(state)
      }

      "overwrite upload status when markUploadAsPosted transition and already in ACCEPTED state" in {
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c")),
                FileUpload.Accepted(
                  Nonce(4),
                  Timestamp.Any,
                  "foo-bar-ref-4",
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
        ) when markUploadAsPosted(S3UploadSuccess("foo-bar-ref-4", Some("bucket-123"))) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c")),
                FileUpload.Posted(Nonce(4), Timestamp.Any, "foo-bar-ref-4")
              )
            )
          )
        )
      }

      "do nothing when markUploadAsPosted transition and none matching upload exist" in {
        val state = UploadMultipleFiles(
          fullAmendCaseStateModel,
          FileUploads(files =
            Seq(
              FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
              FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
              FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when markUploadAsPosted(S3UploadSuccess("foo-bar-ref-4", Some("bucket-123"))) should thenGo(state)
      }

      "mark file upload as REJECTED when markUploadAsRejected transition" in {
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when markUploadAsRejected(
          S3UploadError("foo-bar-ref-2", "errorCode1", "errorMessage2")
        ) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Rejected(
                  Nonce(2),
                  Timestamp.Any,
                  "foo-bar-ref-2",
                  S3UploadError("foo-bar-ref-2", "errorCode1", "errorMessage2")
                ),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        )
      }

      "overwrite upload status when markUploadAsRejected transition and already in REJECTED state" in {
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when markUploadAsRejected(
          S3UploadError("foo-bar-ref-3", "errorCode1", "errorMessage2")
        ) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(
                  Nonce(3),
                  Timestamp.Any,
                  "foo-bar-ref-3",
                  S3UploadError("foo-bar-ref-3", "errorCode1", "errorMessage2")
                )
              )
            )
          )
        )
      }

      "overwrite upload status when markUploadAsRejected transition and already in ACCEPTED state" in {
        val state = UploadMultipleFiles(
          fullAmendCaseStateModel,
          FileUploads(files =
            Seq(
              FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
              FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
              FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c")),
              FileUpload.Rejected(
                Nonce(3),
                Timestamp.Any,
                "foo-bar-ref-3",
                S3UploadError("foo-bar-ref-3", "errorCode1", "errorMessage2")
              )
            )
          )
        )
        given(state) when markUploadAsRejected(
          S3UploadError("foo-bar-ref-4", "errorCode1", "errorMessage2")
        ) should thenGo(state)
      }

      "do nothing when markUploadAsRejected transition and none matching file upload found" in {
        val state = UploadMultipleFiles(
          fullAmendCaseStateModel,
          FileUploads(files =
            Seq(
              FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
              FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
              FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when markUploadAsRejected(
          S3UploadError("foo-bar-ref-4", "errorCode1", "errorMessage2")
        ) should thenGo(state)
      }

      "update file upload status to ACCEPTED when positive upscanCallbackArrived transition" in {
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf",
              size = Some(4567890)
            )
          )
        ) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
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
                ),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        )
      }

      "do nothing when positive upscanCallbackArrived transition and none matching file upload found" in {
        val state = UploadMultipleFiles(
          fullAmendCaseStateModel,
          FileUploads(files =
            Seq(
              FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
              FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
              FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when upscanCallbackArrived(Nonce(4))(
          UpscanFileReady(
            reference = "foo-bar-ref-4",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf",
              size = Some(4567890)
            )
          )
        ) should thenGo(state)
      }

      "overwrite upload status when positive upscanCallbackArrived transition and file upload already in ACCEPTED state" in {
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Accepted(
                  Nonce(1),
                  Timestamp.Any,
                  "foo-bar-ref-1",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?0035699",
                  ZonedDateTime.parse("2018-04-24T09:28:00Z"),
                  "786f101dd52e8b2ace0dcf5ed09b1d1ba30e608938510ce46e7a5c7a4e775189",
                  "test.png",
                  "image/png",
                  Some(4567890)
                ),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf",
              size = Some(4567890)
            )
          )
        ) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
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
                ),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        )
      }

      "overwrite upload status when positive upscanCallbackArrived transition and file upload already in REJECTED state" in {
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Rejected(Nonce(1), Timestamp.Any, "foo-bar-ref-1", S3UploadError("a", "b", "c")),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf",
              size = Some(4567890)
            )
          )
        ) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
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
                ),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        )
      }

      "overwrite upload status when positive upscanCallbackArrived transition and file upload already in FAILED state" in {
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Failed(
                  Nonce(1),
                  Timestamp.Any,
                  "foo-bar-ref-1",
                  UpscanNotification.FailureDetails(
                    failureReason = UpscanNotification.QUARANTINE,
                    message = "e.g. This file has a virus"
                  )
                ),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf",
              size = Some(4567890)
            )
          )
        ) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
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
                ),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        )
      }

      "update file upload status to FAILED when negative upscanCallbackArrived transition" in {
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileFailed(
            reference = "foo-bar-ref-1",
            failureDetails = UpscanNotification.FailureDetails(
              failureReason = UpscanNotification.QUARANTINE,
              message = "e.g. This file has a virus"
            )
          )
        ) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Failed(
                  Nonce(1),
                  Timestamp.Any,
                  "foo-bar-ref-1",
                  UpscanNotification.FailureDetails(
                    failureReason = UpscanNotification.QUARANTINE,
                    message = "e.g. This file has a virus"
                  )
                ),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        )
      }

      "do nothing when negative upscanCallbackArrived transition and none matching file upload found" in {
        val state = UploadMultipleFiles(
          fullAmendCaseStateModel,
          FileUploads(files =
            Seq(
              FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
              FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
              FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when upscanCallbackArrived(Nonce(4))(
          UpscanFileFailed(
            reference = "foo-bar-ref-4",
            failureDetails = UpscanNotification.FailureDetails(
              failureReason = UpscanNotification.QUARANTINE,
              message = "e.g. This file has a virus"
            )
          )
        ) should thenGo(state)
      }

      "overwrite upload status when negative upscanCallbackArrived transition and upload already in FAILED state" in {
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Failed(
                  Nonce(1),
                  Timestamp.Any,
                  "foo-bar-ref-1",
                  UpscanNotification.FailureDetails(
                    failureReason = UpscanNotification.REJECTED,
                    message = "e.g. This file has wrong type"
                  )
                ),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileFailed(
            reference = "foo-bar-ref-1",
            failureDetails = UpscanNotification.FailureDetails(
              failureReason = UpscanNotification.QUARANTINE,
              message = "e.g. This file has a virus"
            )
          )
        ) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Failed(
                  Nonce(1),
                  Timestamp.Any,
                  "foo-bar-ref-1",
                  UpscanNotification.FailureDetails(
                    failureReason = UpscanNotification.QUARANTINE,
                    message = "e.g. This file has a virus"
                  )
                ),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        )
      }

      "overwrite upload status when negative upscanCallbackArrived transition and upload already in ACCEPTED state" in {
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Accepted(
                  Nonce(1),
                  Timestamp.Any,
                  "foo-bar-ref-1",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?0035699",
                  ZonedDateTime.parse("2018-04-24T09:28:00Z"),
                  "786f101dd52e8b2ace0dcf5ed09b1d1ba30e608938510ce46e7a5c7a4e775189",
                  "test.png",
                  "image/png",
                  Some(4567890)
                ),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileFailed(
            reference = "foo-bar-ref-1",
            failureDetails = UpscanNotification.FailureDetails(
              failureReason = UpscanNotification.QUARANTINE,
              message = "e.g. This file has a virus"
            )
          )
        ) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Failed(
                  Nonce(1),
                  Timestamp.Any,
                  "foo-bar-ref-1",
                  UpscanNotification.FailureDetails(
                    failureReason = UpscanNotification.QUARANTINE,
                    message = "e.g. This file has a virus"
                  )
                ),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        )
      }

      "remove file upload when removeFileUploadByReference transition and reference exists" in {
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  Nonce(3),
                  Timestamp.Any,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                ),
                FileUpload.Rejected(Nonce(4), Timestamp.Any, "foo-bar-ref-4", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when removeFileUploadByReference("foo-bar-ref-3")(testUpscanRequest)(mockUpscanInitiate) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(4), Timestamp.Any, "foo-bar-ref-4", S3UploadError("a", "b", "c"))
              )
            )
          )
        )
      }

      "do nothing when removeFileUploadByReference transition and none file upload matches" in {
        val state = UploadMultipleFiles(
          fullAmendCaseStateModel,
          FileUploads(files =
            Seq(
              FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
              FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
              FileUpload.Accepted(
                Nonce(3),
                Timestamp.Any,
                "foo-bar-ref-3",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              ),
              FileUpload.Rejected(Nonce(4), Timestamp.Any, "foo-bar-ref-4", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when removeFileUploadByReference("foo-bar-ref-5")(testUpscanRequest)(
          mockUpscanInitiate
        ) should thenGo(state)
      }
    }

    "at state UploadFile" should {
      "go to WaitingForFileVerification when waitForFileVerification and not verified yet" in {
        given(
          UploadFile(
            fullAmendCaseStateModel,
            "foo-bar-ref-2",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  Nonce(3),
                  Timestamp.Any,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                ),
                FileUpload.Failed(
                  Nonce(4),
                  Timestamp.Any,
                  "foo-bar-ref-4",
                  UpscanNotification.FailureDetails(UpscanNotification.REJECTED, "some failure reason")
                )
              )
            )
          )
        ) when waitForFileVerification should thenGo(
          WaitingForFileVerification(
            fullAmendCaseStateModel,
            "foo-bar-ref-2",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Posted(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  Nonce(3),
                  Timestamp.Any,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                ),
                FileUpload.Failed(
                  Nonce(4),
                  Timestamp.Any,
                  "foo-bar-ref-4",
                  UpscanNotification.FailureDetails(UpscanNotification.REJECTED, "some failure reason")
                )
              )
            )
          )
        )
      }

      "go to FileUploaded when waitForFileVerification and accepted already" in {
        given(
          UploadFile(
            fullAmendCaseStateModel,
            "foo-bar-ref-3",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  Nonce(3),
                  Timestamp.Any,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                ),
                FileUpload.Failed(
                  Nonce(4),
                  Timestamp.Any,
                  "foo-bar-ref-4",
                  UpscanNotification.FailureDetails(UpscanNotification.REJECTED, "some failure reason")
                )
              )
            )
          )
        ) when waitForFileVerification should thenGo(
          FileUploaded(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  Nonce(3),
                  Timestamp.Any,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                ),
                FileUpload.Failed(
                  Nonce(4),
                  Timestamp.Any,
                  "foo-bar-ref-4",
                  UpscanNotification.FailureDetails(UpscanNotification.REJECTED, "some failure reason")
                )
              )
            )
          )
        )
      }

      "go to UploadFile when waitForFileVerification and file upload already rejected" in {
        given(
          UploadFile(
            fullAmendCaseStateModel,
            "foo-bar-ref-4",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  Nonce(3),
                  Timestamp.Any,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                ),
                FileUpload.Failed(
                  Nonce(4),
                  Timestamp.Any,
                  "foo-bar-ref-4",
                  UpscanNotification.FailureDetails(UpscanNotification.REJECTED, "some failure reason")
                )
              )
            )
          )
        ) when waitForFileVerification should thenGo(
          UploadFile(
            fullAmendCaseStateModel,
            "foo-bar-ref-4",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  Nonce(3),
                  Timestamp.Any,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                ),
                FileUpload.Failed(
                  Nonce(4),
                  Timestamp.Any,
                  "foo-bar-ref-4",
                  UpscanNotification.FailureDetails(UpscanNotification.REJECTED, "some failure reason")
                )
              )
            ),
            Some(
              FileVerificationFailed(
                UpscanNotification.FailureDetails(UpscanNotification.REJECTED, "some failure reason")
              )
            )
          )
        )
      }

      "goto FileUploaded when upscanCallbackArrived and accepted, and reference matches" in {
        given(
          UploadFile(
            fullAmendCaseStateModel,
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files = Seq(FileUpload.Initiated(Nonce(1), Timestamp.Any, "foo-bar-ref-1")))
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf",
              size = Some(4567890)
            )
          )
        ) should thenGo(
          FileUploaded(
            fullAmendCaseStateModel,
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
      }

      "goto UploadFile when upscanCallbackArrived and failed, and reference matches" in {
        given(
          UploadFile(
            fullAmendCaseStateModel,
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files = Seq(FileUpload.Initiated(Nonce(1), Timestamp.Any, "foo-bar-ref-1")))
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileFailed(
            reference = "foo-bar-ref-1",
            failureDetails = UpscanNotification.FailureDetails(
              failureReason = UpscanNotification.UNKNOWN,
              message = "e.g. This file has a virus"
            )
          )
        ) should thenGo(
          UploadFile(
            fullAmendCaseStateModel,
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files =
              Seq(
                FileUpload.Failed(
                  Nonce(1),
                  Timestamp.Any,
                  "foo-bar-ref-1",
                  UpscanNotification.FailureDetails(UpscanNotification.UNKNOWN, "e.g. This file has a virus")
                )
              )
            ),
            Some(
              FileVerificationFailed(
                UpscanNotification.FailureDetails(UpscanNotification.UNKNOWN, "e.g. This file has a virus")
              )
            )
          )
        )
      }

      "goto UploadFile with error when fileUploadWasRejected" in {
        val state =
          UploadFile(
            fullAmendCaseStateModel,
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files = Seq(FileUpload.Initiated(Nonce(1), Timestamp.Any, "foo-bar-ref-1")))
          )

        given(state) when markUploadAsRejected(
          S3UploadError(
            key = "foo-bar-ref-1",
            errorCode = "a",
            errorMessage = "b",
            errorResource = Some("c"),
            errorRequestId = Some("d")
          )
        ) should thenGo(
          state.copy(
            fileUploads = FileUploads(files =
              Seq(
                FileUpload.Rejected(
                  Nonce(1),
                  Timestamp.Any,
                  "foo-bar-ref-1",
                  S3UploadError(
                    key = "foo-bar-ref-1",
                    errorCode = "a",
                    errorMessage = "b",
                    errorResource = Some("c"),
                    errorRequestId = Some("d")
                  )
                )
              )
            ),
            maybeUploadError = Some(
              FileTransmissionFailed(
                S3UploadError(
                  key = "foo-bar-ref-1",
                  errorCode = "a",
                  errorMessage = "b",
                  errorResource = Some("c"),
                  errorRequestId = Some("d")
                )
              )
            )
          )
        )
      }
    }

    "at state WaitingForFileVerification" should {
      "stay when waitForFileVerification and not verified yet" in {
        val state = WaitingForFileVerification(
          fullAmendCaseStateModel,
          "foo-bar-ref-1",
          UploadRequest(
            href = "https://s3.bucket",
            fields = Map(
              "callbackUrl"     -> "https://foo.bar/callback",
              "successRedirect" -> "https://foo.bar/success",
              "errorRedirect"   -> "https://foo.bar/failure"
            )
          ),
          FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
          FileUploads(files =
            Seq(
              FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1")
            )
          )
        )
        given(state) when waitForFileVerification should thenGo(state)
      }

      "go to UploadFile when waitForFileVerification and reference unknown" in {
        given(
          WaitingForFileVerification(
            fullAmendCaseStateModel,
            "foo-bar-ref-2",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1")
              )
            )
          )
        ) when waitForFileVerification should thenGo(
          UploadFile(
            fullAmendCaseStateModel,
            "foo-bar-ref-2",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1")
              )
            )
          )
        )
      }

      "go to FileUploaded when waitForFileVerification and file already accepted" in {
        given(
          WaitingForFileVerification(
            fullAmendCaseStateModel,
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
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
            ),
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
        ) when waitForFileVerification should thenGo(
          FileUploaded(
            fullAmendCaseStateModel,
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
      }

      "go to UploadFile when waitForFileVerification and file already failed" in {
        given(
          WaitingForFileVerification(
            fullAmendCaseStateModel,
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Failed(
              Nonce(1),
              Timestamp.Any,
              "foo-bar-ref-1",
              UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
            ),
            FileUploads(files =
              Seq(
                FileUpload.Failed(
                  Nonce(1),
                  Timestamp.Any,
                  "foo-bar-ref-1",
                  UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
                )
              )
            )
          )
        ) when waitForFileVerification should thenGo(
          UploadFile(
            fullAmendCaseStateModel,
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files =
              Seq(
                FileUpload.Failed(
                  Nonce(1),
                  Timestamp.Any,
                  "foo-bar-ref-1",
                  UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
                )
              )
            ),
            Some(
              FileVerificationFailed(UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason"))
            )
          )
        )
      }

      "goto FileUploaded when upscanCallbackArrived and accepted, and reference matches" in {
        given(
          WaitingForFileVerification(
            fullAmendCaseStateModel,
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
            FileUploads(files = Seq(FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1")))
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf",
              size = Some(4567890)
            )
          )
        ) should thenGo(
          FileUploaded(
            fullAmendCaseStateModel,
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
      }

      "goto UploadFile when upscanCallbackArrived and failed, and reference matches" in {
        given(
          WaitingForFileVerification(
            fullAmendCaseStateModel,
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
            FileUploads(files = Seq(FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1")))
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileFailed(
            reference = "foo-bar-ref-1",
            failureDetails = UpscanNotification.FailureDetails(
              failureReason = UpscanNotification.QUARANTINE,
              message = "e.g. This file has a virus"
            )
          )
        ) should thenGo(
          UploadFile(
            fullAmendCaseStateModel,
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files =
              Seq(
                FileUpload.Failed(
                  Nonce(1),
                  Timestamp.Any,
                  "foo-bar-ref-1",
                  UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "e.g. This file has a virus")
                )
              )
            ),
            Some(
              FileVerificationFailed(
                UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "e.g. This file has a virus")
              )
            )
          )
        )
      }

      "stay at WaitingForFileVerification when upscanCallbackArrived and reference doesn't match" in {
        given(
          WaitingForFileVerification(
            fullAmendCaseStateModel,
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Posted(Nonce(2), Timestamp.Any, "foo-bar-ref-2")
              )
            )
          )
        ) when upscanCallbackArrived(Nonce(2))(
          UpscanFileFailed(
            reference = "foo-bar-ref-2",
            failureDetails = UpscanNotification.FailureDetails(
              failureReason = UpscanNotification.QUARANTINE,
              message = "e.g. This file has a virus"
            )
          )
        ) should thenGo(
          WaitingForFileVerification(
            fullAmendCaseStateModel,
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Failed(
                  Nonce(2),
                  Timestamp.Any,
                  "foo-bar-ref-2",
                  UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "e.g. This file has a virus")
                )
              )
            )
          )
        )
      }

      "retreat to FileUploaded when some files has been uploaded already" in {
        given(
          WaitingForFileVerification(
            fullAmendCaseStateModel,
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Accepted(
                  Nonce(2),
                  Timestamp.Any,
                  "foo-bar-ref-2",
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
        ) when backToFileUploaded should thenGo(
          FileUploaded(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Accepted(
                  Nonce(2),
                  Timestamp.Any,
                  "foo-bar-ref-2",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                )
              )
            ),
            true
          )
        )
      }

      "retreat to EnterResponseText when none file has been uploaded yet" in {
        given(
          WaitingForFileVerification(
            fullAmendCaseStateModel,
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Posted(Nonce(2), Timestamp.Any, "foo-bar-ref-2")
              )
            )
          )
        ) when backToFileUploaded should thenGo(
          EnterResponseText(
            fullAmendCaseStateModel.copy(fileUploads =
              Some(
                FileUploads(files =
                  Seq(
                    FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                    FileUpload.Posted(Nonce(2), Timestamp.Any, "foo-bar-ref-2")
                  )
                )
              )
            )
          )
        )
      }
    }

    "at state FileUploaded" should {
      "goto acknowledged FileUploaded when waitForFileVerification" in {
        val state = FileUploaded(
          fullAmendCaseStateModel,
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
          ),
          acknowledged = false
        )

        given(state) when
          waitForFileVerification should
          thenGo(state.copy(acknowledged = true))
      }

      "goto AmendCaseSummary when continue after files uploaded" in {
        given(
          FileUploaded(
            fullAmendCaseStateModel,
            someFileUploads,
            acknowledged = false
          )
        ) when toAmendSummary should thenGo(
          AmendCaseSummary(fullAmendCaseStateModel.copy(fileUploads = Some(someFileUploads)))
        )
      }

      "fail when amendCase and received error response" in {
        val updateCaseApi: UpdateCaseApi = { request =>
          Future.successful(
            TraderServicesCaseResponse(correlationId = "", error = Some(ApiError("", Some(""))))
          )
        }
        given(
          FileUploaded(
            fullAmendCaseStateModel,
            someFileUploads,
            acknowledged = false
          )
        ) shouldFailWhen amendCase(updateCaseApi)(eoriNumber)
      }

      "fail when amendCase and received case reference doesn't match" in {
        val updateCaseApi: UpdateCaseApi = { request =>
          Future.successful(
            TraderServicesCaseResponse(
              correlationId = "",
              result = Some(TraderServicesResult("not_the_same_case_reference", generatedAt))
            )
          )
        }
        given(
          FileUploaded(
            fullAmendCaseStateModel,
            someFileUploads,
            acknowledged = false
          )
        ) shouldFailWhen amendCase(updateCaseApi)(eoriNumber)
      }
    }

    "at state AmendCaseSummary" should {
      "goto AmendConfirmation when in AmendSummary mode and response text is entered" in {
        val updateCaseApi: UpdateCaseApi = { request =>
          Future.successful(
            TraderServicesCaseResponse(
              correlationId = "",
              result = Some(TraderServicesResult("PC12010081330XGBNZJO04", generatedAt))
            )
          )
        }
        val responseText = Random.alphanumeric.take(1000).mkString
        val model = AmendCaseModel(
          responseText = Some(responseText),
          caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
          typeOfAmendment = Some(TypeOfAmendment.WriteResponse)
        )
        given(
          AmendCaseSummary(model)
        ) when amendCase(updateCaseApi)(eoriNumber) should
          thenGo(
            AmendCaseConfirmation(TraderServicesResult("PC12010081330XGBNZJO04", generatedAt))
          )
      }
      "goto AmendConfirmation when in AmendSummary mode and both text and files are provided" in {
        val updateCaseApi: UpdateCaseApi = { request =>
          Future.successful(
            TraderServicesCaseResponse(
              correlationId = "",
              result = Some(TraderServicesResult("PC12010081330XGBNZJO04", generatedAt))
            )
          )
        }
        given(
          AmendCaseSummary(fullAmendCaseStateModel)
        ) when amendCase(updateCaseApi)(eoriNumber) should
          thenGo(
            AmendCaseConfirmation(TraderServicesResult("PC12010081330XGBNZJO04", generatedAt))
          )
      }
      "goto AmendConfirmation when in AmendSummary mode and only files are uploaded" in {
        val updateCaseApi: UpdateCaseApi = { request =>
          Future.successful(
            TraderServicesCaseResponse(
              correlationId = "",
              result = Some(TraderServicesResult("PC12010081330XGBNZJO04", generatedAt))
            )
          )
        }
        val model = AmendCaseModel(
          responseText = None,
          caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
          typeOfAmendment = Some(TypeOfAmendment.UploadDocuments),
          fileUploads = Some(someFileUploads)
        )

        given(
          AmendCaseSummary(model)
        ) when amendCase(updateCaseApi)(eoriNumber) should
          thenGo(
            AmendCaseConfirmation(TraderServicesResult("PC12010081330XGBNZJO04", generatedAt))
          )
      }
      "fail when submitted response text in AmendCaseSummary mode and UpdateCase API returned an error" in {
        val updateCaseApi: UpdateCaseApi = { request =>
          Future.successful(TraderServicesCaseResponse(correlationId = "", error = Some(ApiError("", Some("")))))
        }
        val responseText = Random.alphanumeric.take(1000).mkString
        val model = AmendCaseModel(
          responseText = Some(responseText),
          caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
          typeOfAmendment = Some(TypeOfAmendment.WriteResponse)
        )
        given(
          AmendCaseSummary(model)
        ) shouldFailWhen amendCase(updateCaseApi)(eoriNumber)
      }
      "fail when submitted response text in AmendCaseSummary mode and UpdateCase API returned success with different case reference number" in {
        val updateCaseApi: UpdateCaseApi = { request =>
          Future.successful(
            TraderServicesCaseResponse(
              correlationId = "",
              result = Some(TraderServicesResult("not_the_same_case_reference", generatedAt))
            )
          )
        }
        val responseText = Random.alphanumeric.take(1000).mkString
        val model = AmendCaseModel(
          responseText = Some(responseText),
          caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
          typeOfAmendment = Some(TypeOfAmendment.WriteResponse)
        )
        given(
          AmendCaseSummary(model)
        ) shouldFailWhen amendCase(updateCaseApi)(eoriNumber)
      }
    }
  }

  case class given[S <: State: ClassTag](initialState: S)
      extends AmendCaseJourneyService[DummyContext] with InMemoryStore[(State, List[State]), DummyContext] {

    await(save((initialState, Nil)))

    def withBreadcrumbs(breadcrumbs: State*): this.type = {
      val (state, _) = await(fetch).getOrElse((EnterCaseReferenceNumber(), Nil))
      await(save((state, breadcrumbs.toList)))
      this
    }

    def when(transition: Transition): (State, List[State]) =
      await(super.apply(transition))

    def shouldFailWhen(transition: Transition) =
      Try(await(super.apply(transition))).isSuccess shouldBe false

    def when(merger: Merger[S], state: State): (State, List[State]) =
      await(super.modify { s: S => merger.apply((s, state)) })
  }
}
