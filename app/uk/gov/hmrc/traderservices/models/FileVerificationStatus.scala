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

package uk.gov.hmrc.traderservices.models

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.traderservices.views.UploadFileViewContext
import play.api.i18n.Messages
import play.api.mvc.Call

case class FileVerificationStatus(
  reference: String,
  fileStatus: String,
  fileMimeType: Option[String] = None,
  fileName: Option[String] = None,
  fileSize: Option[Int] = None,
  previewUrl: Option[String] = None,
  errorMessage: Option[String] = None,
  uploadRequest: Option[UploadRequest] = None
)

object FileVerificationStatus {

  def apply(
    fileUpload: FileUpload,
    uploadFileViewContext: UploadFileViewContext,
    filePreviewUrl: String => Call,
    maxFileSizeMb: Int
  )(implicit
    messages: Messages
  ): FileVerificationStatus =
    fileUpload match {
      case f: FileUpload.Initiated =>
        FileVerificationStatus(fileUpload.reference, "NOT_UPLOADED", uploadRequest = f.uploadRequest)

      case f: FileUpload.Posted =>
        FileVerificationStatus(fileUpload.reference, "WAITING")

      case f: FileUpload.Accepted =>
        FileVerificationStatus(
          fileUpload.reference,
          "ACCEPTED",
          fileMimeType = Some(f.fileMimeType),
          fileName = Some(f.fileName),
          fileSize = f.fileSize,
          previewUrl = Some(s"${filePreviewUrl(f.reference).url}")
        )

      case f: FileUpload.Failed =>
        FileVerificationStatus(
          fileUpload.reference,
          "FAILED",
          errorMessage = Some(messages(uploadFileViewContext.toMessageKey(f.details)))
        )

      case f: FileUpload.Rejected =>
        FileVerificationStatus(
          fileUpload.reference,
          "REJECTED",
          errorMessage = Some(messages(uploadFileViewContext.toMessageKey(f.details), maxFileSizeMb))
        )

      case f: FileUpload.Duplicate =>
        FileVerificationStatus(
          fileUpload.reference,
          "DUPLICATE",
          errorMessage = Some(messages(uploadFileViewContext.duplicateFileMessageKey))
        )
    }

  implicit val formats: Format[FileVerificationStatus] = Json.format[FileVerificationStatus]
}
