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
  * - implement [[retreatFromFileUpload]].
  */
trait FileUploadJourneyModelMixin extends JourneyModel {

  type FileUploadHostData

  trait State
  trait IsTransient

  /** Maximum number of files to upload. */
  val maxFileUploadsNumber: Int

  /** Implement to enable backward transition. */
  def retreatFromFileUpload: String => Transition

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

    case class UploadFile(
      hostData: FileUploadHostData,
      reference: String,
      uploadRequest: UploadRequest,
      fileUploads: FileUploads,
      maybeUploadError: Option[FileUploadError] = None
    ) extends FileUploadState

    case class WaitingForFileVerification(
      hostData: FileUploadHostData,
      reference: String,
      uploadRequest: UploadRequest,
      currentFileUpload: FileUpload,
      fileUploads: FileUploads
    ) extends FileUploadState with IsTransient

    case class FileUploaded(
      hostData: FileUploadHostData,
      fileUploads: FileUploads,
      acknowledged: Boolean = false
    ) extends FileUploadState

    case class UploadMultipleFiles(
      hostData: FileUploadHostData,
      fileUploads: FileUploads
    ) extends FileUploadState

  }

  type UpscanInitiateApi = UpscanInitiateRequest => Future[UpscanInitiateResponse]

  /** Common file upload initialization helper. */
  private[journeys] final def gotoFileUploadOrUploaded(
    hostData: FileUploadHostData,
    upscanRequest: UpscanInitiateRequest,
    upscanInitiate: UpscanInitiateApi,
    fileUploadsOpt: Option[FileUploads],
    showUploadSummaryIfAny: Boolean
  )(implicit ec: ExecutionContext): Future[State] = {
    val fileUploads = fileUploadsOpt.getOrElse(FileUploads())
    if ((showUploadSummaryIfAny && fileUploads.nonEmpty) || fileUploads.acceptedCount >= maxFileUploadsNumber)
      goto(
        FileUploadState.FileUploaded(hostData, fileUploads)
      )
    else
      for {
        upscanResponse <- upscanInitiate(upscanRequest)
      } yield FileUploadState.UploadFile(
        hostData,
        upscanResponse.reference,
        upscanResponse.uploadRequest,
        fileUploads + FileUpload.Initiated(fileUploads.files.size + 1, upscanResponse.reference, None, None)
      )
  }

  object FileUploadTransitions {
    import FileUploadState._

    private def resetFileUploadStatusToInitiated(reference: String, fileUploads: FileUploads): FileUploads =
      fileUploads.copy(files = fileUploads.files.map {
        case f if f.reference == reference =>
          FileUpload.Initiated(f.orderNumber, f.reference, None, None)
        case other => other
      })

    final def toUploadMultipleFiles(user: String) =
      Transition {
        case current: UploadMultipleFiles =>
          goto(current)

        case state: CanEnterFileUpload =>
          goto(
            UploadMultipleFiles(
              hostData = state.hostData,
              fileUploads = state.fileUploadsOpt.getOrElse(FileUploads())
            )
          )
      }

    final def initiateNextFileUpload(uploadId: String)(
      upscanRequest: UpscanInitiateRequest
    )(upscanInitiate: UpscanInitiateApi)(user: String)(implicit ec: ExecutionContext) =
      Transition {
        case state: UploadMultipleFiles =>
          if (
            !state.fileUploads.hasUploadId(uploadId) &&
            state.fileUploads.initiatedOrAcceptedCount < maxFileUploadsNumber
          )
            upscanInitiate(upscanRequest)
              .flatMap { upscanResponse =>
                goto(
                  state.copy(fileUploads =
                    state.fileUploads + FileUpload
                      .Initiated(
                        state.fileUploads.files.size + 1,
                        upscanResponse.reference,
                        Some(upscanResponse.uploadRequest),
                        Some(uploadId)
                      )
                  )
                )
              }
          else goto(state)
      }

    final def initiateFileUpload(
      upscanRequest: UpscanInitiateRequest
    )(upscanInitiate: UpscanInitiateApi)(user: String)(implicit ec: ExecutionContext) =
      Transition {
        case state: CanEnterFileUpload =>
          gotoFileUploadOrUploaded(
            state.hostData,
            upscanRequest,
            upscanInitiate,
            state.fileUploadsOpt,
            showUploadSummaryIfAny = true
          )

        case current @ UploadFile(hostData, reference, uploadRequest, fileUploads, maybeUploadError) =>
          if (maybeUploadError.isDefined)
            goto(
              current
                .copy(fileUploads = resetFileUploadStatusToInitiated(reference, fileUploads))
            )
          else
            goto(current)

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
            gotoFileUploadOrUploaded(
              hostData,
              upscanRequest,
              upscanInitiate,
              Some(fileUploads),
              showUploadSummaryIfAny = false
            )
      }

    final def markUploadAsRejected(user: String)(error: S3UploadError) =
      Transition {
        case current @ UploadFile(
              hostData,
              reference,
              uploadRequest,
              fileUploads,
              maybeUploadError
            ) =>
          val updatedFileUploads = fileUploads.copy(files = fileUploads.files.map {
            case FileUpload.Initiated(orderNumber, ref, _, _) if ref == error.key =>
              FileUpload.Rejected(orderNumber, ref, error)
            case u => u
          })
          goto(current.copy(fileUploads = updatedFileUploads, maybeUploadError = Some(FileTransmissionFailed(error))))

        case current @ UploadMultipleFiles(hostData, fileUploads) =>
          val updatedFileUploads = fileUploads.copy(files = fileUploads.files.map {
            case FileUpload.Initiated(orderNumber, ref, _, _) if ref == error.key =>
              FileUpload.Rejected(orderNumber, ref, error)
            case u => u
          })
          goto(current.copy(fileUploads = updatedFileUploads))
      }

    final def markUploadAsPosted(receipt: S3UploadSuccess) =
      Transition {
        case current @ UploadMultipleFiles(hostData, fileUploads) =>
          val updatedFileUploads = fileUploads.copy(files = fileUploads.files.map {
            case FileUpload.Initiated(orderNumber, ref, _, _) if ref == receipt.key =>
              FileUpload.Posted(orderNumber, ref)
            case u => u
          })
          goto(current.copy(fileUploads = updatedFileUploads))
      }

    /** Common transition helper based on the file upload status. */
    private def commonFileUploadStatusHandler(
      hostData: FileUploadHostData,
      fileUploads: FileUploads,
      reference: String,
      uploadRequest: UploadRequest,
      fallbackState: => State
    ): PartialFunction[Option[FileUpload], Future[State]] = {

      case None =>
        goto(fallbackState)

      case Some(initiatedFile: FileUpload.Initiated) =>
        goto(UploadFile(hostData, reference, uploadRequest, fileUploads))

      case Some(postedFile: FileUpload.Posted) =>
        goto(
          WaitingForFileVerification(
            hostData,
            reference,
            uploadRequest,
            postedFile,
            fileUploads
          )
        )

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

      case Some(rejectedFile: FileUpload.Rejected) =>
        goto(
          UploadFile(
            hostData,
            reference,
            uploadRequest,
            fileUploads,
            Some(FileTransmissionFailed(rejectedFile.details))
          )
        )

      case Some(duplicatedFile: FileUpload.Duplicate) =>
        goto(
          UploadFile(
            hostData,
            reference,
            uploadRequest,
            fileUploads,
            Some(
              DuplicateFileUpload(
                duplicatedFile.checksum,
                duplicatedFile.existingFileName,
                duplicatedFile.duplicateFileName
              )
            )
          )
        )
    }

    /** Transition when file has been uploaded and should wait for verification. */
    final def waitForFileVerification(user: String) =
      Transition {
        /** Change file status to posted and wait. */
        case current @ UploadFile(
              hostData,
              reference,
              uploadRequest,
              fileUploads,
              errorOpt
            ) =>
          val updatedFileUploads = fileUploads.copy(files = fileUploads.files.map {
            case FileUpload.Initiated(orderNumber, ref, _, _) if ref == reference =>
              FileUpload.Posted(orderNumber, reference)
            case other => other
          })
          val currentUpload = updatedFileUploads.files.find(_.reference == reference)
          commonFileUploadStatusHandler(
            hostData,
            updatedFileUploads,
            reference,
            uploadRequest,
            current.copy(fileUploads = updatedFileUploads)
          )
            .apply(currentUpload)

        /** If waiting already, keep waiting. */
        case current @ WaitingForFileVerification(
              hostData,
              reference,
              uploadRequest,
              currentFileUpload,
              fileUploads
            ) =>
          val currentUpload = fileUploads.files.find(_.reference == reference)
          commonFileUploadStatusHandler(
            hostData,
            fileUploads,
            reference,
            uploadRequest,
            UploadFile(hostData, reference, uploadRequest, fileUploads)
          )
            .apply(currentUpload)

        /** If file already uploaded, do nothing. */
        case state: FileUploaded =>
          goto(state.copy(acknowledged = true))
      }

    /** Transition when notification arrives from upscan. */
    final def upscanCallbackArrived(notification: UpscanNotification) = {

      def updateFileUploads(fileUploads: FileUploads) =
        fileUploads.copy(files = fileUploads.files.map {
          // update status of the file with matching upscan reference
          case FileUpload(orderNumber, ref) if ref == notification.reference =>
            notification match {
              case UpscanFileReady(_, url, uploadDetails) =>
                //check for existing file uploads with duplicated checksum
                val modifiedFileUpload: FileUpload = fileUploads.files
                  .find(file =>
                    file.checksumOpt.contains(uploadDetails.checksum) && file.reference != notification.reference
                  ) match {
                  case Some(existingFileUpload: FileUpload.Accepted) =>
                    FileUpload.Duplicate(
                      orderNumber,
                      ref,
                      uploadDetails.checksum,
                      existingFileName = existingFileUpload.fileName,
                      duplicateFileName = uploadDetails.fileName
                    )
                  case _ =>
                    FileUpload.Accepted(
                      orderNumber,
                      ref,
                      url,
                      uploadDetails.uploadTimestamp,
                      uploadDetails.checksum,
                      uploadDetails.fileName,
                      uploadDetails.fileMimeType
                    )
                }
                modifiedFileUpload

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
        case current @ WaitingForFileVerification(
              hostData,
              reference,
              uploadRequest,
              currentFileUpload,
              fileUploads
            ) =>
          val updatedFileUploads = updateFileUploads(fileUploads)
          val currentUpload = updatedFileUploads.files.find(_.reference == reference)
          commonFileUploadStatusHandler(
            hostData,
            updatedFileUploads,
            reference,
            uploadRequest,
            current.copy(fileUploads = updatedFileUploads)
          )
            .apply(currentUpload)

        case current @ UploadFile(hostData, reference, uploadRequest, fileUploads, errorOpt) =>
          val updatedFileUploads = updateFileUploads(fileUploads)
          val currentUpload = updatedFileUploads.files.find(_.reference == reference)
          commonFileUploadStatusHandler(
            hostData,
            updatedFileUploads,
            reference,
            uploadRequest,
            current.copy(fileUploads = updatedFileUploads)
          )
            .apply(currentUpload)

      }
    }

    final def submitedUploadAnotherFileChoice(
      upscanRequest: UpscanInitiateRequest
    )(
      upscanInitiate: UpscanInitiateApi
    )(exitFileUpload: String => Transition)(user: String)(uploadAnotherFile: Boolean)(implicit ec: ExecutionContext) =
      Transition {
        case current @ FileUploaded(hostData, fileUploads, acknowledged) =>
          if (uploadAnotherFile && fileUploads.acceptedCount < maxFileUploadsNumber)
            gotoFileUploadOrUploaded(
              hostData,
              upscanRequest,
              upscanInitiate,
              Some(fileUploads),
              showUploadSummaryIfAny = false
            )
          else
            exitFileUpload(user).apply(current)
      }

    final def removeFileUploadByReference(reference: String)(
      upscanRequest: UpscanInitiateRequest
    )(upscanInitiate: UpscanInitiateApi)(user: String)(implicit ec: ExecutionContext) =
      Transition {
        case current: FileUploaded =>
          val updatedFileUploads = current.fileUploads
            .copy(files = current.fileUploads.files.filterNot(_.reference == reference))
          val updatedCurrentState = current.copy(fileUploads = updatedFileUploads)
          if (updatedFileUploads.isEmpty)
            initiateFileUpload(upscanRequest)(upscanInitiate)(user)
              .apply(updatedCurrentState)
          else
            goto(updatedCurrentState)
      }

    final def backToFileUploaded(user: String) =
      Transition {
        case s: FileUploadState =>
          if (s.fileUploads.nonEmpty)
            goto(FileUploaded(s.hostData, s.fileUploads, acknowledged = true))
          else
            retreatFromFileUpload(user).apply(s)

        case s: CanEnterFileUpload =>
          if (s.fileUploadsOpt.exists(_.nonEmpty))
            goto(FileUploaded(s.hostData, s.fileUploadsOpt.get, acknowledged = true))
          else
            retreatFromFileUpload(user).apply(s)
      }
  }

}
