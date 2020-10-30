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

package uk.gov.hmrc.traderservices.views

import javax.inject.Singleton
import play.api.data.Form
import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem
import uk.gov.hmrc.traderservices.models.FileUploads
import uk.gov.hmrc.traderservices.models.FileUpload
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryList
import play.api.mvc.Call
import uk.gov.hmrc.traderservices.models.FileUploadError
import play.api.data.FormError
import uk.gov.hmrc.traderservices.models.FileTransmissionFailed
import uk.gov.hmrc.traderservices.models.FileVerificationFailed
import uk.gov.hmrc.traderservices.models.S3UploadError
import uk.gov.hmrc.traderservices.models.UpscanNotification

@Singleton
class UploadFileViewContext {

  def toFormError(error: FileUploadError): FormError =
    error match {
      case FileTransmissionFailed(error) =>
        FormError("file", Seq(toMessageKey(error)))

      case FileVerificationFailed(details) =>
        FormError("file", Seq(toMessageKey(details)))
    }

  def toMessageKey(error: S3UploadError): String =
    error.errorCode match {
      case "InternalError"  => "error.file-upload.try-again"
      case "EntityTooLarge" => "error.file-upload.invalid-size-large"
      case "EntityTooSmall" => "error.file-upload.invalid-size-small"
      case _                => "error.file-upload.unknown"
    }

  def toMessageKey(details: UpscanNotification.FailureDetails): String =
    details.failureReason match {
      case UpscanNotification.QUARANTINE => "error.file-upload.quarantine"
      case UpscanNotification.REJECTED   => "error.file-upload.invalid-type"
      case UpscanNotification.UNKNOWN    => "error.file-upload.unknown"
    }
}
