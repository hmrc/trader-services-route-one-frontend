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

package uk.gov.hmrc.traderservices.journey

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
import uk.gov.hmrc.traderservices.models.TypeOfAmendment.UploadDocuments
class AmendCaseJourneyModelSpec extends UnitSpec with StateMatchers[State] with TestData {

  import scala.concurrent.duration._
  override implicit val defaultTimeout: FiniteDuration = 60 seconds

  // dummy journey context
  case class DummyContext()
  implicit val dummyContext: DummyContext = DummyContext()

  "AmendCaseJourneyModel" when {
    "at state Start" should {
      "go to Start when start" in {
        given(Start) when start(eoriNumber) should thenGo(Start)
      }

      "go to EnterCaseReferenceNumber when enterCaseReferenceNumber" in {
        given(Start) when enterCaseReferenceNumber(eoriNumber) should thenGo(EnterCaseReferenceNumber())
      }
    }

    "at state EnterCaseReferenceNumber" should {
      "stay at EnterCaseReferenceNumber when enterCaseReferenceNumber" in {
        given(EnterCaseReferenceNumber()) when enterCaseReferenceNumber(eoriNumber) should thenGo(
          EnterCaseReferenceNumber()
        )
      }

      "stay at EnterCaseReferenceNumber when enterCaseReferenceNumber and keep former answers" in {
        val model = AmendCaseModel(caseReferenceNumber = Some("PC12010081330XGBNZJO04"))
        given(
          EnterCaseReferenceNumber(model)
        ) when enterCaseReferenceNumber(
          eoriNumber
        ) should thenGo(
          EnterCaseReferenceNumber(model)
        )
      }

      "go to SelectTypeOfAmendment when submited case reference number" in {
        given(EnterCaseReferenceNumber()) when submitedCaseReferenceNumber(eoriNumber)(
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
        ) when submitedTypeOfAmendment(uploadMultipleFiles = false)(testUpscanRequest)(mockUpscanInitiate)(eoriNumber)(
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
        ) when submitedTypeOfAmendment(uploadMultipleFiles = false)(testUpscanRequest)(mockUpscanInitiate)(eoriNumber)(
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
        ) when submitedTypeOfAmendment(uploadMultipleFiles = true)(testUpscanRequest)(mockUpscanInitiate)(eoriNumber)(
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
        ) when submitedTypeOfAmendment(uploadMultipleFiles = false)(testUpscanRequest)(mockUpscanInitiate)(eoriNumber)(
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
            FileUploads(files = Seq(FileUpload.Initiated(1, "foo-bar-ref")))
          )
        )
      }

      "retreat to EnterCaseReferenceNumber when enterCaseReferenceNumber" in {
        val model = AmendCaseModel(caseReferenceNumber = Some("PC12010081330XGBNZJO04"))
        given(
          SelectTypeOfAmendment(model)
        ) when enterCaseReferenceNumber(eoriNumber) should thenGo(
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
        ) when submitedResponseText(uploadMultipleFiles = false)(testUpscanRequest)(mockUpscanInitiate)(eoriNumber)(
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
        ) when submitedResponseText(uploadMultipleFiles = false)(testUpscanRequest)(mockUpscanInitiate)(eoriNumber)(
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
        ) when submitedResponseText(uploadMultipleFiles = false)(testUpscanRequest)(mockUpscanInitiate)(eoriNumber)(
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
        ) when submitedResponseText(uploadMultipleFiles = true)(testUpscanRequest)(mockUpscanInitiate)(eoriNumber)(
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
        ) when submitedResponseText(uploadMultipleFiles = false)(testUpscanRequest)(mockUpscanInitiate)(eoriNumber)(
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
            FileUploads(files = Seq(FileUpload.Initiated(1, "foo-bar-ref")))
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
          eoriNumber
        )(
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
        ) when backToSelectTypeOfAmendment(eoriNumber) should thenGo(
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
        ) shouldFailWhen backToSelectTypeOfAmendment(eoriNumber)
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
          1,
          "foo-bar-ref-1",
          "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
          ZonedDateTime.parse("2018-04-24T09:30:00Z"),
          "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
          "test.pdf",
          "application/pdf"
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
        ) when toAmendSummary(eoriNumber) should thenGo(
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
        given(state) when toAmendSummary(eoriNumber) should thenGo(state)
      }

      "stay when toUploadMultipleFiles transition" in {
        val state = UploadMultipleFiles(
          fullAmendCaseStateModel,
          nonEmptyFileUploads
        )
        given(state) when toUploadMultipleFiles(eoriNumber) should thenGo(state)
      }

      "initiate new file upload when initiateNextFileUpload transition and empty uploads" in {
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads()
          )
        ) when initiateNextFileUpload("001")(testUpscanRequest)(mockUpscanInitiate)(eoriNumber) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads() +
              FileUpload.Initiated(
                1,
                "foo-bar-ref",
                uploadId = Some("001"),
                uploadRequest = Some(someUploadRequest(testUpscanRequest))
              )
          )
        )
      }

      "initiate new file upload when initiateNextFileUpload transition and some uploads exist already" in {
        val fileUploads = FileUploads(files =
          (0 until (maxFileUploadsNumber - 1))
            .map(i => FileUpload.Initiated(i, s"foo-bar-ref-$i", uploadId = Some(s"0$i")))
        ) + FileUpload.Rejected(9, "foo-bar-ref-9", S3UploadError("a", "b", "c"))
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            fileUploads
          )
        ) when initiateNextFileUpload("001")(testUpscanRequest)(mockUpscanInitiate)(eoriNumber) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            fileUploads +
              FileUpload.Initiated(
                fileUploads.files.size + 1,
                "foo-bar-ref",
                uploadId = Some("001"),
                uploadRequest = Some(someUploadRequest(testUpscanRequest))
              )
          )
        )
      }

      "do nothing when initiateNextFileUpload with existing uploadId" in {

        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            nonEmptyFileUploads +
              FileUpload.Initiated(2, "foo-bar-ref", uploadId = Some("101"))
          )
        ) when initiateNextFileUpload("101")(testUpscanRequest)(mockUpscanInitiate)(eoriNumber) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            nonEmptyFileUploads +
              FileUpload.Initiated(2, "foo-bar-ref", uploadId = Some("101"))
          )
        )
      }

      "do nothing when initiateNextFileUpload and maximum number of uploads already reached" in {

        val fileUploads = FileUploads(files =
          (0 until maxFileUploadsNumber)
            .map(i => FileUpload.Initiated(i, s"foo-bar-ref-$i", uploadId = Some(s"0$i")))
        )
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            fileUploads
          )
        ) when initiateNextFileUpload("101")(testUpscanRequest)(mockUpscanInitiate)(eoriNumber) should thenGo(
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
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Initiated(2, "foo-bar-ref-2"),
                FileUpload.Rejected(3, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when markUploadAsPosted(S3UploadSuccess("foo-bar-ref-2", Some("bucket-123"))) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Posted(2, "foo-bar-ref-2"),
                FileUpload.Rejected(3, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
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
              FileUpload.Posted(1, "foo-bar-ref-1"),
              FileUpload.Initiated(2, "foo-bar-ref-2"),
              FileUpload.Rejected(3, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when markUploadAsPosted(S3UploadSuccess("foo-bar-ref-1", Some("bucket-123"))) should thenGo(state)
      }

      "do nothing when markUploadAsPosted transition and already in ACCEPTED state" in {
        val state = UploadMultipleFiles(
          fullAmendCaseStateModel,
          FileUploads(files =
            Seq(
              FileUpload.Posted(1, "foo-bar-ref-1"),
              FileUpload.Initiated(2, "foo-bar-ref-2"),
              FileUpload.Rejected(3, "foo-bar-ref-3", S3UploadError("a", "b", "c")),
              FileUpload.Accepted(
                4,
                "foo-bar-ref-4",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf"
              )
            )
          )
        )
        given(state) when markUploadAsPosted(S3UploadSuccess("foo-bar-ref-4", Some("bucket-123"))) should thenGo(state)
      }

      "do nothing when markUploadAsPosted transition and none matching upload exist" in {
        val state = UploadMultipleFiles(
          fullAmendCaseStateModel,
          FileUploads(files =
            Seq(
              FileUpload.Posted(1, "foo-bar-ref-1"),
              FileUpload.Initiated(2, "foo-bar-ref-2"),
              FileUpload.Rejected(3, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
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
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Initiated(2, "foo-bar-ref-2"),
                FileUpload.Rejected(3, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when markUploadAsRejected(eoriNumber)(
          S3UploadError("foo-bar-ref-2", "errorCode1", "errorMessage2")
        ) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Rejected(2, "foo-bar-ref-2", S3UploadError("foo-bar-ref-2", "errorCode1", "errorMessage2")),
                FileUpload.Rejected(3, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        )
      }

      "do nothing when markUploadAsRejected transition and already in REJECTED state" in {
        val state = UploadMultipleFiles(
          fullAmendCaseStateModel,
          FileUploads(files =
            Seq(
              FileUpload.Posted(1, "foo-bar-ref-1"),
              FileUpload.Initiated(2, "foo-bar-ref-2"),
              FileUpload.Rejected(3, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when markUploadAsRejected(eoriNumber)(
          S3UploadError("foo-bar-ref-3", "errorCode1", "errorMessage2")
        ) should thenGo(state)
      }

      "do nothing when markUploadAsRejected transition and already in ACCEPTED state" in {
        val state = UploadMultipleFiles(
          fullAmendCaseStateModel,
          FileUploads(files =
            Seq(
              FileUpload.Posted(1, "foo-bar-ref-1"),
              FileUpload.Initiated(2, "foo-bar-ref-2"),
              FileUpload.Rejected(3, "foo-bar-ref-3", S3UploadError("a", "b", "c")),
              FileUpload.Accepted(
                4,
                "foo-bar-ref-4",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf"
              )
            )
          )
        )
        given(state) when markUploadAsRejected(eoriNumber)(
          S3UploadError("foo-bar-ref-4", "errorCode1", "errorMessage2")
        ) should thenGo(state)
      }

      "do nothing when markUploadAsRejected transition and none matching file upload found" in {
        val state = UploadMultipleFiles(
          fullAmendCaseStateModel,
          FileUploads(files =
            Seq(
              FileUpload.Posted(1, "foo-bar-ref-1"),
              FileUpload.Initiated(2, "foo-bar-ref-2"),
              FileUpload.Rejected(3, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when markUploadAsRejected(eoriNumber)(
          S3UploadError("foo-bar-ref-4", "errorCode1", "errorMessage2")
        ) should thenGo(state)
      }

      "update file upload status to ACCEPTED when positive upscanCallbackArrived transition" in {
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Initiated(2, "foo-bar-ref-2"),
                FileUpload.Rejected(3, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when upscanCallbackArrived(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf"
            )
          )
        ) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Accepted(
                  1,
                  "foo-bar-ref-1",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
                ),
                FileUpload.Initiated(2, "foo-bar-ref-2"),
                FileUpload.Rejected(3, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
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
              FileUpload.Posted(1, "foo-bar-ref-1"),
              FileUpload.Initiated(2, "foo-bar-ref-2"),
              FileUpload.Rejected(3, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when upscanCallbackArrived(
          UpscanFileReady(
            reference = "foo-bar-ref-4",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf"
            )
          )
        ) should thenGo(state)
      }

      "do nothing when positive upscanCallbackArrived transition and file upload already in ACCEPTED state" in {
        val state = UploadMultipleFiles(
          fullAmendCaseStateModel,
          FileUploads(files =
            Seq(
              FileUpload.Accepted(
                1,
                "foo-bar-ref-1",
                "https://bucketName.s3.eu-west-2.amazonaws.com?0035699",
                ZonedDateTime.parse("2018-04-24T09:28:00Z"),
                "786f101dd52e8b2ace0dcf5ed09b1d1ba30e608938510ce46e7a5c7a4e775189",
                "test.png",
                "image/png"
              ),
              FileUpload.Initiated(2, "foo-bar-ref-2"),
              FileUpload.Rejected(3, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when upscanCallbackArrived(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf"
            )
          )
        ) should thenGo(state)
      }

      "do nothing when positive upscanCallbackArrived transition and file upload already in REJECTED state" in {
        val state = UploadMultipleFiles(
          fullAmendCaseStateModel,
          FileUploads(files =
            Seq(
              FileUpload.Rejected(1, "foo-bar-ref-1", S3UploadError("a", "b", "c")),
              FileUpload.Initiated(2, "foo-bar-ref-2"),
              FileUpload.Rejected(3, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when upscanCallbackArrived(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf"
            )
          )
        ) should thenGo(state)
      }

      "do nothing when positive upscanCallbackArrived transition and file upload already in FAILED state" in {
        val state = UploadMultipleFiles(
          fullAmendCaseStateModel,
          FileUploads(files =
            Seq(
              FileUpload.Failed(
                1,
                "foo-bar-ref-1",
                UpscanNotification.FailureDetails(
                  failureReason = UpscanNotification.QUARANTINE,
                  message = "e.g. This file has a virus"
                )
              ),
              FileUpload.Initiated(2, "foo-bar-ref-2"),
              FileUpload.Rejected(3, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when upscanCallbackArrived(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf"
            )
          )
        ) should thenGo(state)
      }

      "update file upload status to FAILED when negative upscanCallbackArrived transition" in {
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Initiated(2, "foo-bar-ref-2"),
                FileUpload.Rejected(3, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when upscanCallbackArrived(
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
                  1,
                  "foo-bar-ref-1",
                  UpscanNotification.FailureDetails(
                    failureReason = UpscanNotification.QUARANTINE,
                    message = "e.g. This file has a virus"
                  )
                ),
                FileUpload.Initiated(2, "foo-bar-ref-2"),
                FileUpload.Rejected(3, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
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
              FileUpload.Posted(1, "foo-bar-ref-1"),
              FileUpload.Initiated(2, "foo-bar-ref-2"),
              FileUpload.Rejected(3, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when upscanCallbackArrived(
          UpscanFileFailed(
            reference = "foo-bar-ref-4",
            failureDetails = UpscanNotification.FailureDetails(
              failureReason = UpscanNotification.QUARANTINE,
              message = "e.g. This file has a virus"
            )
          )
        ) should thenGo(state)
      }

      "do nothing when negative upscanCallbackArrived transition and upload already in FAILED state" in {
        val state = UploadMultipleFiles(
          fullAmendCaseStateModel,
          FileUploads(files =
            Seq(
              FileUpload.Failed(
                1,
                "foo-bar-ref-1",
                UpscanNotification.FailureDetails(
                  failureReason = UpscanNotification.REJECTED,
                  message = "e.g. This file has wrong type"
                )
              ),
              FileUpload.Initiated(2, "foo-bar-ref-2"),
              FileUpload.Rejected(3, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when upscanCallbackArrived(
          UpscanFileFailed(
            reference = "foo-bar-ref-1",
            failureDetails = UpscanNotification.FailureDetails(
              failureReason = UpscanNotification.QUARANTINE,
              message = "e.g. This file has a virus"
            )
          )
        ) should thenGo(state)
      }

      "do nothing when negative upscanCallbackArrived transition and upload already in ACCEPTED state" in {
        val state = UploadMultipleFiles(
          fullAmendCaseStateModel,
          FileUploads(files =
            Seq(
              FileUpload.Accepted(
                1,
                "foo-bar-ref-1",
                "https://bucketName.s3.eu-west-2.amazonaws.com?0035699",
                ZonedDateTime.parse("2018-04-24T09:28:00Z"),
                "786f101dd52e8b2ace0dcf5ed09b1d1ba30e608938510ce46e7a5c7a4e775189",
                "test.png",
                "image/png"
              ),
              FileUpload.Initiated(2, "foo-bar-ref-2"),
              FileUpload.Rejected(3, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when upscanCallbackArrived(
          UpscanFileFailed(
            reference = "foo-bar-ref-1",
            failureDetails = UpscanNotification.FailureDetails(
              failureReason = UpscanNotification.QUARANTINE,
              message = "e.g. This file has a virus"
            )
          )
        ) should thenGo(state)
      }

      "remove file upload when removeFileUploadByReference transition and reference exists" in {
        given(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Initiated(2, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  3,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
                ),
                FileUpload.Rejected(4, "foo-bar-ref-4", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when removeFileUploadByReference("foo-bar-ref-3")(testUpscanRequest)(mockUpscanInitiate)(
          eoriNumber
        ) should thenGo(
          UploadMultipleFiles(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Initiated(2, "foo-bar-ref-2"),
                FileUpload.Rejected(4, "foo-bar-ref-4", S3UploadError("a", "b", "c"))
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
              FileUpload.Posted(1, "foo-bar-ref-1"),
              FileUpload.Initiated(2, "foo-bar-ref-2"),
              FileUpload.Accepted(
                3,
                "foo-bar-ref-3",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf"
              ),
              FileUpload.Rejected(4, "foo-bar-ref-4", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when removeFileUploadByReference("foo-bar-ref-5")(testUpscanRequest)(mockUpscanInitiate)(
          eoriNumber
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
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Initiated(2, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  3,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
                ),
                FileUpload.Failed(
                  4,
                  "foo-bar-ref-4",
                  UpscanNotification.FailureDetails(UpscanNotification.REJECTED, "some failure reason")
                )
              )
            )
          )
        ) when waitForFileVerification(
          eoriNumber
        ) should thenGo(
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
            FileUpload.Posted(2, "foo-bar-ref-2"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Posted(2, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  3,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
                ),
                FileUpload.Failed(
                  4,
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
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Initiated(2, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  3,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
                ),
                FileUpload.Failed(
                  4,
                  "foo-bar-ref-4",
                  UpscanNotification.FailureDetails(UpscanNotification.REJECTED, "some failure reason")
                )
              )
            )
          )
        ) when waitForFileVerification(
          eoriNumber
        ) should thenGo(
          FileUploaded(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Initiated(2, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  3,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
                ),
                FileUpload.Failed(
                  4,
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
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Initiated(2, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  3,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
                ),
                FileUpload.Failed(
                  4,
                  "foo-bar-ref-4",
                  UpscanNotification.FailureDetails(UpscanNotification.REJECTED, "some failure reason")
                )
              )
            )
          )
        ) when waitForFileVerification(
          eoriNumber
        ) should thenGo(
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
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Initiated(2, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  3,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
                ),
                FileUpload.Failed(
                  4,
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
            FileUploads(files = Seq(FileUpload.Initiated(1, "foo-bar-ref-1")))
          )
        ) when upscanCallbackArrived(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf"
            )
          )
        ) should thenGo(
          FileUploaded(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Accepted(
                  1,
                  "foo-bar-ref-1",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
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
            FileUploads(files = Seq(FileUpload.Initiated(1, "foo-bar-ref-1")))
          )
        ) when upscanCallbackArrived(
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
                  1,
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
            FileUploads(files = Seq(FileUpload.Initiated(1, "foo-bar-ref-1")))
          )

        given(state) when markUploadAsRejected(eoriNumber)(
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
                  1,
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
          FileUpload.Posted(1, "foo-bar-ref-1"),
          FileUploads(files =
            Seq(
              FileUpload.Posted(1, "foo-bar-ref-1")
            )
          )
        )
        given(state) when waitForFileVerification(
          eoriNumber
        ) should thenGo(state)
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
            FileUpload.Posted(1, "foo-bar-ref-1"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1")
              )
            )
          )
        ) when waitForFileVerification(
          eoriNumber
        ) should thenGo(
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
                FileUpload.Posted(1, "foo-bar-ref-1")
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
              1,
              "foo-bar-ref-1",
              "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
              ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              "test.pdf",
              "application/pdf"
            ),
            FileUploads(files =
              Seq(
                FileUpload.Accepted(
                  1,
                  "foo-bar-ref-1",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
                )
              )
            )
          )
        ) when waitForFileVerification(
          eoriNumber
        ) should thenGo(
          FileUploaded(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Accepted(
                  1,
                  "foo-bar-ref-1",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
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
              1,
              "foo-bar-ref-1",
              UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
            ),
            FileUploads(files =
              Seq(
                FileUpload.Failed(
                  1,
                  "foo-bar-ref-1",
                  UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
                )
              )
            )
          )
        ) when waitForFileVerification(
          eoriNumber
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
                  1,
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
            FileUpload.Posted(1, "foo-bar-ref-1"),
            FileUploads(files = Seq(FileUpload.Posted(1, "foo-bar-ref-1")))
          )
        ) when upscanCallbackArrived(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf"
            )
          )
        ) should thenGo(
          FileUploaded(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Accepted(
                  1,
                  "foo-bar-ref-1",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
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
            FileUpload.Posted(1, "foo-bar-ref-1"),
            FileUploads(files = Seq(FileUpload.Posted(1, "foo-bar-ref-1")))
          )
        ) when upscanCallbackArrived(
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
                  1,
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
            FileUpload.Posted(1, "foo-bar-ref-1"),
            FileUploads(files = Seq(FileUpload.Posted(1, "foo-bar-ref-1"), FileUpload.Posted(2, "foo-bar-ref-2")))
          )
        ) when upscanCallbackArrived(
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
            FileUpload.Posted(1, "foo-bar-ref-1"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Failed(
                  2,
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
            FileUpload.Posted(1, "foo-bar-ref-1"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Accepted(
                  2,
                  "foo-bar-ref-2",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
                )
              )
            )
          )
        ) when backToFileUploaded(eoriNumber) should thenGo(
          FileUploaded(
            fullAmendCaseStateModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Accepted(
                  2,
                  "foo-bar-ref-2",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
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
            FileUpload.Posted(1, "foo-bar-ref-1"),
            FileUploads(files = Seq(FileUpload.Posted(1, "foo-bar-ref-1"), FileUpload.Posted(2, "foo-bar-ref-2")))
          )
        ) when backToFileUploaded(eoriNumber) should thenGo(
          EnterResponseText(
            fullAmendCaseStateModel.copy(fileUploads =
              Some(
                FileUploads(files = Seq(FileUpload.Posted(1, "foo-bar-ref-1"), FileUpload.Posted(2, "foo-bar-ref-2")))
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
                1,
                "foo-bar-ref-1",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf"
              )
            )
          ),
          acknowledged = false
        )

        given(state) when
          waitForFileVerification(eoriNumber) should
          thenGo(state.copy(acknowledged = true))
      }

      "goto AmendCaseSummary when continue after files uploaded" in {
        given(
          FileUploaded(
            fullAmendCaseStateModel,
            someFileUploads,
            acknowledged = false
          )
        ) when
          toAmendSummary(eoriNumber) should
          thenGo(
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
