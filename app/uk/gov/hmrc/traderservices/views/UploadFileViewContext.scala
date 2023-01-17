/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.traderservices.views

import javax.inject.Singleton
import uk.gov.hmrc.traderservices.models.FileUploadError
import play.api.data.FormError
import uk.gov.hmrc.traderservices.models.FileTransmissionFailed
import uk.gov.hmrc.traderservices.models.FileVerificationFailed
import uk.gov.hmrc.traderservices.models.S3UploadError
import uk.gov.hmrc.traderservices.models.UpscanNotification
import com.google.inject.Inject
import uk.gov.hmrc.traderservices.wiring.AppConfig
import uk.gov.hmrc.traderservices.models.DuplicateFileUpload
import uk.gov.hmrc.traderservices.models.FileVerificationStatus
import uk.gov.hmrc.traderservices.models.FileUpload
import play.api.mvc.Call
import play.api.libs.json.Json
import play.api.i18n.Messages

@Singleton
class UploadFileViewContext @Inject() (appConfig: AppConfig) {

  def initialScriptStateFrom(initialFileUploads: Seq[FileUpload], previewFile: (String, String) => Call)(implicit
    messages: Messages
  ): String =
    Json.stringify(
      Json.toJson(
        initialFileUploads.map(file =>
          FileVerificationStatus(file, this, previewFile, appConfig.fileFormats.maxFileSizeMb)
        )
      )
    )

  def toFormError(error: FileUploadError): FormError =
    error match {
      case FileTransmissionFailed(error) =>
        FormError("file", Seq(toMessageKey(error)), Seq(appConfig.fileFormats.maxFileSizeMb))

      case FileVerificationFailed(details) =>
        FormError("file", Seq(toMessageKey(details)))

      case DuplicateFileUpload(checksum, existingFileName, duplicateFileName) =>
        FormError("file", Seq(duplicateFileMessageKey))
    }

  def toMessageKey(error: S3UploadError): String =
    error.errorCode match {
      case "400" | "InvalidArgument" => "error.file-upload.required"
      case "InternalError"           => "error.file-upload.try-again"
      case "EntityTooLarge"          => "error.file-upload.invalid-size-large"
      case "EntityTooSmall"          => "error.file-upload.invalid-size-small"
      case _                         => "error.file-upload.unknown"
    }

  def toMessageKey(details: UpscanNotification.FailureDetails): String =
    details.failureReason match {
      case UpscanNotification.QUARANTINE => "error.file-upload.quarantine"
      case UpscanNotification.REJECTED   => "error.file-upload.invalid-type"
      case UpscanNotification.UNKNOWN    => "error.file-upload.unknown"
    }

  val duplicateFileMessageKey: String = "error.file-upload.duplicate"
}
