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

package uk.gov.hmrc.traderservices.journeys

import uk.gov.hmrc.play.fsm.JourneyModel
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.connectors.UpscanInitiateRequest
import scala.concurrent.Future
import uk.gov.hmrc.traderservices.connectors.UpscanInitiateResponse
import scala.concurrent.ExecutionContext

/**
  * Generic file upload journey model mixin.
  * Defines its own states and transitions.
  *
  * In order to plug into final journey model, do:
  *
  * - define type of the host data to carry over,
  * - define maximum number of files to upload,
  * - mark allowed entry states with [[CanEnterFileUpload]] trait,
  */
trait FileUploadJourneyModelMixin extends JourneyModel {

  type FileUploadHostData

  trait State
  trait IsTransient

  /** Maximum number of files to upload. */
  val maxFileUploadsNumber: Int

  /** Marker trait of permitted entry states. */
  trait CanEnterFileUpload extends State {
    def hostData: FileUploadHostData
    def fileUploadsOpt: Option[FileUploads]
  }

  sealed trait FileUploadState extends State {
    def hostData: FileUploadHostData
    def fileUploads: FileUploads
  }

  object FileUploadState {

    final case class UploadFile(
      hostData: FileUploadHostData,
      reference: String,
      uploadRequest: UploadRequest,
      fileUploads: FileUploads,
      maybeUploadError: Option[FileUploadError] = None
    ) extends FileUploadState

    final case class WaitingForFileVerification(
      hostData: FileUploadHostData,
      reference: String,
      uploadRequest: UploadRequest,
      currentFileUpload: FileUpload,
      fileUploads: FileUploads
    ) extends FileUploadState with IsTransient

    final case class FileUploaded(
      hostData: FileUploadHostData,
      fileUploads: FileUploads,
      acknowledged: Boolean = false
    ) extends FileUploadState

  }

  object FileUploadTransitions {
    import FileUploadState._

    type UpscanInitiateApi = UpscanInitiateRequest => Future[UpscanInitiateResponse]

    final def initiateFileUpload(
      callbackUrl: String,
      successRedirect: String,
      errorRedirect: String,
      maxFileSizeMb: Int
    )(upscanInitiate: UpscanInitiateApi)(user: String)(implicit ec: ExecutionContext) =
      Transition {
        case state: CanEnterFileUpload =>
          val fileUploads = state.fileUploadsOpt.getOrElse(FileUploads())
          if (fileUploads.nonEmpty || fileUploads.acceptedCount >= maxFileUploadsNumber)
            goto(
              FileUploaded(
                state.hostData,
                fileUploads
              )
            )
          else
            for {
              upscanResponse <- upscanInitiate(
                                  UpscanInitiateRequest(
                                    callbackUrl = callbackUrl,
                                    successRedirect = Some(successRedirect),
                                    errorRedirect = Some(errorRedirect),
                                    maximumFileSize = Some(maxFileSizeMb * 1024 * 1024)
                                  )
                                )
            } yield UploadFile(
              state.hostData,
              upscanResponse.reference,
              upscanResponse.uploadRequest,
              fileUploads.copy(files =
                fileUploads.files :+ FileUpload.Initiated(fileUploads.files.size + 1, upscanResponse.reference)
              )
            )

        case WaitingForFileVerification(
              hostData,
              reference,
              uploadRequest,
              currentFileUpload,
              fileUploads
            ) =>
          goto(UploadFile(hostData, reference, uploadRequest, fileUploads))

        case current @ FileUploaded(hostData, fileUploads, _) =>
          if (fileUploads.acceptedCount >= maxFileUploadsNumber)
            goto(current)
          else
            for {
              upscanResponse <- upscanInitiate(
                                  UpscanInitiateRequest(
                                    callbackUrl = callbackUrl,
                                    successRedirect = Some(successRedirect),
                                    errorRedirect = Some(errorRedirect),
                                    maximumFileSize = Some(maxFileSizeMb * 1024 * 1024)
                                  )
                                )
            } yield UploadFile(
              hostData,
              upscanResponse.reference,
              upscanResponse.uploadRequest,
              fileUploads.copy(files =
                fileUploads.files :+ FileUpload.Initiated(fileUploads.files.size + 1, upscanResponse.reference)
              )
            )
      }

    final def fileUploadWasRejected(user: String)(error: S3UploadError) =
      Transition {
        case current @ UploadFile(
              hostData,
              reference,
              uploadRequest,
              fileUploads,
              maybeUploadError
            ) =>
          val updatedFileUploads = fileUploads.copy(files = fileUploads.files.map {
            case FileUpload.Initiated(orderNumber, ref) if ref == error.key =>
              FileUpload.Rejected(orderNumber, reference, error)
            case u => u
          })
          goto(current.copy(fileUploads = updatedFileUploads, maybeUploadError = Some(FileTransmissionFailed(error))))
      }

    final def waitForFileVerification(user: String) =
      Transition {
        case current @ UploadFile(
              hostData,
              reference,
              uploadRequest,
              fileUploads,
              errorOpt
            ) =>
          val updatedFileUploads = fileUploads.copy(files = fileUploads.files.map {
            case f: FileUpload.Accepted => f
            case FileUpload(orderNumber, ref) if ref == reference =>
              FileUpload.Posted(orderNumber, reference)
            case f => f
          })
          updatedFileUploads.files.find(_.reference == reference) match {
            case Some(upload: FileUpload.Posted) =>
              goto(
                WaitingForFileVerification(
                  hostData,
                  reference,
                  uploadRequest,
                  upload,
                  updatedFileUploads
                )
              )

            case Some(acceptedFile: FileUpload.Accepted) =>
              goto(FileUploaded(hostData, updatedFileUploads))

            case Some(failedFile: FileUpload.Failed) =>
              goto(
                UploadFile(
                  hostData,
                  reference,
                  uploadRequest,
                  updatedFileUploads,
                  Some(FileVerificationFailed(failedFile.details))
                )
              )

            case _ =>
              goto(UploadFile(hostData, reference, uploadRequest, updatedFileUploads))
          }

        case current @ WaitingForFileVerification(
              hostData,
              reference,
              uploadRequest,
              currentFileUpload,
              fileUploads
            ) =>
          fileUploads.files.find(_.reference == reference) match {
            case Some(upload: FileUpload.Posted) =>
              goto(current)

            case Some(acceptedFile: FileUpload.Accepted) =>
              goto(FileUploaded(hostData, fileUploads))

            case Some(failedFile: FileUpload.Failed) =>
              goto(
                UploadFile(
                  hostData,
                  reference,
                  uploadRequest,
                  fileUploads,
                  Some(FileVerificationFailed(failedFile.details))
                )
              )

            case _ =>
              goto(UploadFile(hostData, reference, uploadRequest, fileUploads))
          }

        case state: FileUploaded =>
          goto(state.copy(acknowledged = true))
      }

    final def upscanCallbackArrived(notification: UpscanNotification) = {

      def updateFileUploads(fileUploads: FileUploads) =
        fileUploads.copy(files = fileUploads.files.map {
          case FileUpload(orderNumber, ref) if ref == notification.reference =>
            notification match {
              case UpscanFileReady(_, url, uploadDetails) =>
                FileUpload.Accepted(
                  orderNumber,
                  ref,
                  url,
                  uploadDetails.uploadTimestamp,
                  uploadDetails.checksum,
                  uploadDetails.fileName,
                  uploadDetails.fileMimeType
                )
              case UpscanFileFailed(_, failureDetails) =>
                FileUpload.Failed(
                  orderNumber,
                  ref,
                  failureDetails
                )
            }
          case u => u
        })

      Transition {
        case WaitingForFileVerification(
              hostData,
              reference,
              uploadRequest,
              currentFileUpload,
              fileUploads
            ) =>
          val updatedFileUploads = updateFileUploads(fileUploads)
          updatedFileUploads.files.find(_.reference == reference) match {
            case None =>
              goto(
                WaitingForFileVerification(
                  hostData,
                  reference,
                  uploadRequest,
                  currentFileUpload,
                  updatedFileUploads
                )
              )

            case Some(upload: FileUpload.Posted) =>
              goto(
                WaitingForFileVerification(
                  hostData,
                  reference,
                  uploadRequest,
                  upload,
                  updatedFileUploads
                )
              )

            case Some(acceptedFile: FileUpload.Accepted) =>
              goto(FileUploaded(hostData, updatedFileUploads))

            case Some(failedFile: FileUpload.Failed) =>
              goto(
                UploadFile(
                  hostData,
                  reference,
                  uploadRequest,
                  updatedFileUploads,
                  Some(FileVerificationFailed(failedFile.details))
                )
              )

            case _ =>
              goto(UploadFile(hostData, reference, uploadRequest, updatedFileUploads))

          }

        case UploadFile(hostData, reference, uploadRequest, fileUploads, errorOpt) =>
          val updatedFileUploads = updateFileUploads(fileUploads)
          updatedFileUploads.files.find(_.reference == reference) match {
            case Some(acceptedFile: FileUpload.Accepted) =>
              goto(FileUploaded(hostData, updatedFileUploads))

            case Some(failedFile: FileUpload.Failed) =>
              goto(
                UploadFile(
                  hostData,
                  reference,
                  uploadRequest,
                  updatedFileUploads,
                  Some(FileVerificationFailed(failedFile.details))
                )
              )

            case _ =>
              goto(UploadFile(hostData, reference, uploadRequest, updatedFileUploads))

          }
      }
    }

    final def submitedUploadAnotherFileChoice(
      callbackUrl: String,
      successRedirect: String,
      errorRedirect: String,
      maxFileSizeMb: Int
    )(
      upscanInitiate: UpscanInitiateApi
    )(exitFileUpload: String => Transition)(user: String)(uploadAnotherFile: Boolean)(implicit ec: ExecutionContext) =
      Transition {
        case current @ FileUploaded(hostData, fileUploads, acknowledged) =>
          if (uploadAnotherFile && fileUploads.acceptedCount < maxFileUploadsNumber)
            initiateFileUpload(callbackUrl, successRedirect, errorRedirect, maxFileSizeMb)(upscanInitiate)(user)
              .apply(current)
          else
            exitFileUpload(user).apply(current)
      }

    final def removeFileUploadByReference(reference: String)(
      callbackUrl: String,
      successRedirect: String,
      errorRedirect: String,
      maxFileSizeMb: Int
    )(upscanInitiate: UpscanInitiateApi)(user: String)(implicit ec: ExecutionContext) =
      Transition {
        case current: FileUploaded =>
          val updatedFileUploads = current.fileUploads
            .copy(files = current.fileUploads.files.filterNot(_.reference == reference))
          val updatedCurrentState = current.copy(fileUploads = updatedFileUploads)
          if (updatedFileUploads.isEmpty)
            initiateFileUpload(callbackUrl, successRedirect, errorRedirect, maxFileSizeMb)(upscanInitiate)(user)
              .apply(updatedCurrentState)
          else
            goto(updatedCurrentState)
      }
  }

}
