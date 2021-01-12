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
import java.time.ZonedDateTime

case class FileUploads(
  files: Seq[FileUpload] = Seq.empty
) {

  def isEmpty: Boolean = acceptedCount == 0
  def nonEmpty: Boolean = !isEmpty
  def isSingle: Boolean = acceptedCount == 1

  def acceptedCount: Int =
    files
      .count { case _: FileUpload.Accepted => true; case _ => false }

  def toUploadedFiles: Seq[UploadedFile] =
    files.collect {
      case f: FileUpload.Accepted =>
        UploadedFile(f.reference, f.url, f.uploadTimestamp, f.checksum, f.fileName, f.fileMimeType)
    }

}

object FileUploads {
  implicit val formats: Format[FileUploads] = Json.format[FileUploads]
}

/** File upload status */
sealed trait FileUpload {
  def orderNumber: Int
  def reference: String
}

object FileUpload extends SealedTraitFormats[FileUpload] {

  def unapply(fileUpload: FileUpload): Option[(Int, String)] =
    Some((fileUpload.orderNumber, fileUpload.reference))

  /**
    * Status when file upload attributes has been requested from upscan-initiate
    * but the file itself has not been yet transmitted to S3 bucket.
    */
  case class Initiated(
    orderNumber: Int,
    reference: String
  ) extends FileUpload

  /** Status when file transmission has been rejected by AWS S3. */
  case class Rejected(
    orderNumber: Int,
    reference: String,
    details: S3UploadError
  ) extends FileUpload

  /** Status when file has successfully arrived to AWS S3 for verification. */
  case class Posted(
    orderNumber: Int,
    reference: String
  ) extends FileUpload

  /** Status when file has been positively verified and is ready for further actions. */
  case class Accepted(
    orderNumber: Int,
    reference: String,
    url: String,
    uploadTimestamp: ZonedDateTime,
    checksum: String,
    fileName: String,
    fileMimeType: String
  ) extends FileUpload

  /** When file has failed verification and may not be used. */
  case class Failed(
    orderNumber: Int,
    reference: String,
    details: UpscanNotification.FailureDetails
  ) extends FileUpload

  override val formats = Set(
    Case[Initiated](Json.format[Initiated]),
    Case[Rejected](Json.format[Rejected]),
    Case[Posted](Json.format[Posted]),
    Case[Accepted](Json.format[Accepted]),
    Case[Failed](Json.format[Failed])
  )

}
