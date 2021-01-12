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

case class FileVerificationStatus private (fileStatus: String)

object FileVerificationStatus {

  def apply(fileUpload: FileUpload): FileVerificationStatus =
    fileUpload match {
      case f: FileUpload.Accepted  => FileVerificationStatus("ACCEPTED")
      case f: FileUpload.Failed    => FileVerificationStatus("FAILED")
      case f: FileUpload.Posted    => FileVerificationStatus("WAITING")
      case f: FileUpload.Rejected  => FileVerificationStatus("REJECTED")
      case f: FileUpload.Initiated => FileVerificationStatus("NOT_UPLOADED")
      case f: FileUpload.Duplicate => FileVerificationStatus("DUPLICATE")
    }

  implicit val formats: Format[FileVerificationStatus] = Json.format[FileVerificationStatus]
}
