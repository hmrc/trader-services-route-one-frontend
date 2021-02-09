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

  def initiatedOrAcceptedCount: Int =
    files
      .count {
        case _: FileUpload.Accepted  => true
        case _: FileUpload.Initiated => true
        case _: FileUpload.Posted    => true
        case _                       => false
      }

  def findUploadWithUpscanReference(reference: String): Option[FileUpload] =
    files.find(_.reference == reference)

  def toUploadedFiles: Seq[UploadedFile] =
    files.collect {
      case f: FileUpload.Accepted =>
        UploadedFile(f.reference, f.url, f.uploadTimestamp, f.checksum, f.fileName, f.fileMimeType)
    }

  def +(file: FileUpload): FileUploads = copy(files = files :+ file)

  def hasUploadId(uploadId: String): Boolean =
    files.exists {
      case FileUpload.Initiated(_, _, _, Some(`uploadId`)) => true
      case _                                               => false
    }

  def findReferenceAndUploadRequestForUploadId(uploadId: String): Option[(String, UploadRequest)] =
    files.collectFirst {
      case FileUpload.Initiated(_, reference, Some(uploadRequest), Some(`uploadId`)) =>
        (reference, uploadRequest)
    }

  def filterOutInitiated: FileUploads =
    copy(files = files.filter {
      case _: FileUpload.Initiated => false
      case _                       => true
    })

}

object FileUploads {
  implicit val formats: Format[FileUploads] = Json.format[FileUploads]
}

/** File upload status */
sealed trait FileUpload {
  def orderNumber: Int
  def reference: String
  def isReady: Boolean
  def checksumOpt: Option[String] = None
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
    reference: String,
    uploadRequest: Option[UploadRequest] = None,
    uploadId: Option[String] = None
  ) extends FileUpload {
    override def isReady: Boolean = false
  }

  /** Status when the file has successfully arrived to AWS S3 for verification. */
  case class Posted(
    orderNumber: Int,
    reference: String
  ) extends FileUpload {
    override def isReady: Boolean = false
  }

  /** Status when file transmission has been rejected by AWS S3. */
  case class Rejected(
    orderNumber: Int,
    reference: String,
    details: S3UploadError
  ) extends FileUpload {
    override def isReady: Boolean = true
  }

  /** Status when the file has been positively verified and is ready for further actions. */
  case class Accepted(
    orderNumber: Int,
    reference: String,
    url: String,
    uploadTimestamp: ZonedDateTime,
    checksum: String,
    fileName: String,
    fileMimeType: String
  ) extends FileUpload {

    override def isReady: Boolean = true
    override def checksumOpt: Option[String] = Some(checksum)
  }

  /** Status when the file has failed verification and may not be used. */
  case class Failed(
    orderNumber: Int,
    reference: String,
    details: UpscanNotification.FailureDetails
  ) extends FileUpload {
    override def isReady: Boolean = true
  }

  /** Status when the file is a duplicate of an existing upload. */
  case class Duplicate(
    orderNumber: Int,
    reference: String,
    checksum: String,
    existingFileName: String,
    duplicateFileName: String
  ) extends FileUpload {
    override def isReady: Boolean = true
  }

  override val formats = Set(
    Case[Initiated](Json.format[Initiated]),
    Case[Rejected](Json.format[Rejected]),
    Case[Posted](Json.format[Posted]),
    Case[Accepted](Json.format[Accepted]),
    Case[Failed](Json.format[Failed]),
    Case[Duplicate](Json.format[Duplicate])
  )

}
