/*
 * Copyright 2020 HM Revenue & Customs
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

import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.State._
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.FileUploadState._
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.Transitions._
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.FileUploadTransitions._
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.{Merger, State, Transition, UpscanInitiateApi}
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.services.AmendCaseJourneyService
import uk.gov.hmrc.traderservices.support.{InMemoryStore, StateMatchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag
import scala.concurrent.Future
import scala.util.Random
import scala.util.Try
import java.time.ZonedDateTime
import uk.gov.hmrc.traderservices.connectors.UpscanInitiateResponse
import uk.gov.hmrc.traderservices.connectors.UpscanInitiateRequest

class AmendCaseJourneyModelSpec extends UnitSpec with StateMatchers[State] with TestData {

  import scala.concurrent.duration._
  override implicit val defaultTimeout: FiniteDuration = 60 seconds

  // dummy journey context
  case class DummyContext()
  implicit val dummyContext: DummyContext = DummyContext()

  val mockUpscanInitiate: UpscanInitiateApi = request =>
    Future.successful(
      UpscanInitiateResponse(
        reference = "foo-bar-ref",
        uploadRequest = UploadRequest(
          href = "https://s3.bucket",
          fields = Map(
            "callbackUrl"         -> request.callbackUrl,
            "successRedirect"     -> request.successRedirect.getOrElse(""),
            "errorRedirect"       -> request.errorRedirect.getOrElse(""),
            "minimumFileSize"     -> request.minimumFileSize.getOrElse(0).toString,
            "maximumFileSize"     -> request.maximumFileSize.getOrElse(0).toString,
            "expectedContentType" -> request.expectedContentType.getOrElse("")
          )
        )
      )
    )

  val upscanRequest =
    UpscanInitiateRequest(
      callbackUrl = "https://foo.bar/callback",
      successRedirect = Some("https://foo.bar/success"),
      errorRedirect = Some("https://foo.bar/failure"),
      minimumFileSize = Some(0),
      maximumFileSize = Some(10 * 1024 * 1024),
      expectedContentType = Some("image/jpeg,image/png")
    )

  "AmendCaseJourneyModel" when {
    "at state EnterCaseReferenceNumber" should {
      "stay at EnterCaseReferenceNumber when enterCaseReferenceNumber" in {
        given(EnterCaseReferenceNumber()) when enterCaseReferenceNumber(eoriNumber) should thenGo(
          EnterCaseReferenceNumber()
        )
      }

      "go to SelectTypeOfAmendment when sumbited case reference number" in {
        given(EnterCaseReferenceNumber()) when submitedCaseReferenceNumber(eoriNumber)(
          "PC12010081330XGBNZJO04"
        ) should thenGo(
          SelectTypeOfAmendment(AmendCaseModel(caseReferenceNumber = Some("PC12010081330XGBNZJO04")))
        )
      }
    }

    "at state SelectTypeOfAmendment" should {
      "go to EnterResponseText when sumbited type of amendment WriteResponse" in {
        given(
          SelectTypeOfAmendment(AmendCaseModel(caseReferenceNumber = Some("PC12010081330XGBNZJO04")))
        ) when submitedTypeOfAmendment(upscanRequest)(mockUpscanInitiate)(eoriNumber)(
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

      "go to EnterResponseText when sumbited type of amendment WriteResponseAndUploadDocuments" in {
        given(
          SelectTypeOfAmendment(AmendCaseModel(caseReferenceNumber = Some("PC12010081330XGBNZJO04")))
        ) when submitedTypeOfAmendment(upscanRequest)(mockUpscanInitiate)(eoriNumber)(
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

      "go to UploadFile when sumbited type of amendment UploadDocuments" in {
        given(
          SelectTypeOfAmendment(AmendCaseModel(caseReferenceNumber = Some("PC12010081330XGBNZJO04")))
        ) when submitedTypeOfAmendment(upscanRequest)(mockUpscanInitiate)(eoriNumber)(
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
      "goto AmendCaseConfirmation when submited response text in WriteResponse mode" in {
        val responseText = Random.alphanumeric.take(1000).mkString
        given(
          EnterResponseText(
            AmendCaseModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.WriteResponse)
            )
          )
        ) when submitedResponseText(upscanRequest)(mockUpscanInitiate)(eoriNumber)(
          responseText
        ) should thenGo(
          AmendCaseConfirmation(
            AmendCaseModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.WriteResponse),
              responseText = Some(responseText),
              fileUploads = None
            )
          )
        )
      }

      "goto UploadFile when submited response text in WriteResponseAndUploadDocuments mode" in {
        val responseText = Random.alphanumeric.take(1000).mkString
        given(
          EnterResponseText(
            AmendCaseModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.WriteResponseAndUploadDocuments)
            )
          )
        ) when submitedResponseText(upscanRequest)(mockUpscanInitiate)(eoriNumber)(
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
        ) shouldFailWhen submitedResponseText(upscanRequest)(mockUpscanInitiate)(eoriNumber)(responseText)
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

      "go to UploadFile when waitForFileVerification and rejected already" in {
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
          WaitingForFileVerification(
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
            FileUpload.Posted(4, "foo-bar-ref-4"),
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
                FileUpload.Posted(4, "foo-bar-ref-4")
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

        given(state) when fileUploadWasRejected(eoriNumber)(
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

      "goto AmendCaseConfirmation when amendCase" in {
        given(
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
            ),
            acknowledged = false
          )
        ) when
          amendCase(eoriNumber) should
          thenGo(
            AmendCaseConfirmation(
              fullAmendCaseStateModel.copy(fileUploads =
                Some(
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
            )
          )
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
