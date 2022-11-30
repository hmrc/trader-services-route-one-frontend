/*
 * Copyright 2022 HM Revenue & Customs
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

import java.time.ZonedDateTime
import play.api.libs.json._
import play.api.libs.functional.syntax._
import javax.mail.internet.MimeUtility
import scala.util.Try

/** Upscan service notification */
sealed trait UpscanNotification {
  def reference: String
}

/** If these checks pass, the file is made available for retrieval & the Upscan service will make a POST request to the
  * URL specified as the 'callbackUrl' by the consuming service with the following body:
  *
  * @example
  *   { "reference" : "11370e18-6e24-453e-b45a-76d3e32ea33d", "fileStatus" : "READY", "downloadUrl" :
  *   "https://bucketName.s3.eu-west-2.amazonaws.com?1235676", "uploadDetails": { "uploadTimestamp":
  *   "2018-04-24T09:30:00Z", "checksum": "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
  *   "fileName": "test.pdf", "fileMimeType": "application/pdf", "size": "80090" } }
  */
case class UpscanFileReady(reference: String, downloadUrl: String, uploadDetails: UpscanNotification.UploadDetails)
    extends UpscanNotification

/** If these checks fails, the Upscan service will make a POST request to the URL specified as the 'callbackUrl' by the
  * consuming service with the following body:
  *
  * @example
  *   { "reference" : "11370e18-6e24-453e-b45a-76d3e32ea33d", "fileStatus" : "FAILED", "failureDetails": {
  *   "failureReason": "QUARANTINE", "message": "e.g. This file has a virus" } } { "reference" :
  *   "11370e18-6e24-453e-b45a-76d3e32ea33d", "fileStatus" : "FAILED", "failureDetails": { "failureReason": "REJECTED",
  *   "message": "MIME type $mime is not allowed for service $service-name" } } { "reference" :
  *   "11370e18-6e24-453e-b45a-76d3e32ea33d", "fileStatus" : "FAILED", "failureDetails": { "failureReason": "UNKNOWN",
  *   "message": "Something unknown happened" } }
  */
case class UpscanFileFailed(reference: String, failureDetails: UpscanNotification.FailureDetails)
    extends UpscanNotification

object UpscanNotification {

  /** @param uploadTimestamp
    *   The timestamp of the file upload
    * @param checksum
    *   The SHA256 hash of the uploaded file
    * @param fileName
    *   File name as it was provided by the user
    * @param fileMimeType
    *   Detected MIME type of the file. Please note that this refers to actual contents of the file, not to the name (if
    *   user uploads PDF document named data.png, it will be detected as a application/pdf)
    * @param size
    *   file size
    */
  case class UploadDetails(
    uploadTimestamp: ZonedDateTime,
    checksum: String,
    fileName: String,
    fileMimeType: String,
    size: Option[Int]
  )

  case class FailureDetails(
    failureReason: FailureReason,
    message: String
  )

  /** File check failure reason enum, either QUARANTINE, REJECTED, UNKNOWN
    */
  sealed trait FailureReason

  /** The file has failed virus scanning */
  case object QUARANTINE extends FailureReason

  /** The file is not of an allowed file type */
  case object REJECTED extends FailureReason

  /** There is some other problem with the file */
  case object UNKNOWN extends FailureReason

  object UploadDetails {

    implicit val formats: Format[UploadDetails] = Format(
      ((__ \ "uploadTimestamp").read[ZonedDateTime] and
        (__ \ "checksum").read[String] and
        (__ \ "fileName").read[String].map(decodeMimeEncodedWord) and
        (__ \ "fileMimeType").read[String] and
        (__ \ "size").readNullable[Int])(UploadDetails.apply _),
      ((__ \ "uploadTimestamp").write[ZonedDateTime] and
        (__ \ "checksum").write[String] and
        (__ \ "fileName").write[String] and
        (__ \ "fileMimeType").write[String] and
        (__ \ "size").writeNullable[Int])(unlift(UploadDetails.unapply))
    )

    def decodeMimeEncodedWord(word: String): String =
      Try(MimeUtility.decodeText(word)).getOrElse(word)
  }

  object FailureDetails {
    implicit val formats: Format[FailureDetails] = Json.format[FailureDetails]
  }

  object FailureReason extends EnumerationFormats[FailureReason] {

    override val values: Set[FailureReason] = Set(QUARANTINE, REJECTED, UNKNOWN)
  }

  val fileStatus = "fileStatus"
  val READY = "READY"
  val FAILED = "FAILED"

  val upscanFileReadyFormat: Format[UpscanFileReady] = Json.format[UpscanFileReady]
  val upscanFileFailedFormat: Format[UpscanFileFailed] = Json.format[UpscanFileFailed]

  implicit lazy val reads: Reads[UpscanNotification] =
    Reads {
      case o: JsObject if (o \ fileStatus).asOpt[String].contains(READY) =>
        upscanFileReadyFormat.reads(o)
      case o: JsObject if (o \ fileStatus).asOpt[String].contains(FAILED) =>
        upscanFileFailedFormat.reads(o)
      case _ => JsError("Invalid format of UpscanNotification")
    }

  def addFileStatus(value: String): JsValue => JsValue = {
    case o: JsObject =>
      JsObject(o.value.toSeq.take(1) ++ Seq((fileStatus, JsString(value))) ++ o.value.toSeq.drop(1))
    case o => o
  }

  implicit lazy val writes: Writes[UpscanNotification] =
    new Writes[UpscanNotification] {
      override def writes(o: UpscanNotification): JsValue =
        o match {
          case i: UpscanFileReady =>
            upscanFileReadyFormat.transform(addFileStatus(READY)).writes(i)
          case i: UpscanFileFailed =>
            upscanFileFailedFormat.transform(addFileStatus(FAILED)).writes(i)
        }
    }

}
