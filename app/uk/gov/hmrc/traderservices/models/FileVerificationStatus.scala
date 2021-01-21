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
  fileStatus: String,
  fileMimeType: Option[String] = None,
  fileName: Option[String] = None,
  previewUrl: Option[String] = None,
  errorMessage: Option[String] = None,
  uploadRequest: Option[UploadRequest] = None
)

object FileVerificationStatus {

  def apply(fileUpload: FileUpload, uploadFileViewContext: UploadFileViewContext, filePreviewUrl: String => Call)(
    implicit messages: Messages
  ): FileVerificationStatus =
    fileUpload match {
      case f: FileUpload.Initiated =>
        FileVerificationStatus("NOT_UPLOADED", uploadRequest = f.uploadRequest)

      case f: FileUpload.Posted =>
        FileVerificationStatus("WAITING")

      case f: FileUpload.Accepted =>
        FileVerificationStatus(
          "ACCEPTED",
          fileMimeType = Some(f.fileMimeType),
          fileName = Some(f.fileName),
          previewUrl = Some(s"${filePreviewUrl(f.reference).url}")
        )

      case f: FileUpload.Failed =>
        FileVerificationStatus(
          "FAILED",
          errorMessage = Some(messages(uploadFileViewContext.toMessageKey(f.details)))
        )

      case f: FileUpload.Rejected =>
        FileVerificationStatus(
          "REJECTED",
          errorMessage = Some(messages(uploadFileViewContext.toMessageKey(f.details)))
        )

      case f: FileUpload.Duplicate =>
        FileVerificationStatus(
          "DUPLICATE",
          errorMessage = Some(messages(uploadFileViewContext.duplicateFileMessageKey))
        )
    }

  implicit val formats: Format[FileVerificationStatus] = Json.format[FileVerificationStatus]
}
