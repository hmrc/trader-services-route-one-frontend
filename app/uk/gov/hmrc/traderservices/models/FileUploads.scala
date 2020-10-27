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

package uk.gov.hmrc.traderservices.models

import play.api.libs.json.{Format, Json}
import play.api.libs.json.JsObject
import play.api.libs.json.JsError
import play.api.libs.json.Writes
import play.api.libs.json.Reads
import play.api.libs.json.JsValue
import java.time.ZonedDateTime

case class FileUploads(
  files: Seq[FileUpload] = Seq.empty
) {

  def isFirst: Boolean = files.size == 1

  def acceptedCount: Int = files.count { case _: FileUpload.Accepted => true; case _ => false }
}

object FileUploads {
  implicit val formats: Format[FileUploads] = Json.format[FileUploads]
}

sealed trait FileUpload {
  def orderNumber: Int
  def reference: String
}

object FileUpload {

  def unapply(fileUpload: FileUpload): Option[(Int, String)] =
    Some((fileUpload.orderNumber, fileUpload.reference))

  case class Initiated(
    orderNumber: Int,
    reference: String
  ) extends FileUpload

  object Initiated {
    val tag = "initiated"
    implicit val formats: Format[Initiated] = Json.format[Initiated]
  }

  case class Posted(
    orderNumber: Int,
    reference: String
  ) extends FileUpload

  object Posted {
    val tag = "posted"
    implicit val formats: Format[Posted] = Json.format[Posted]
  }

  case class Accepted(
    orderNumber: Int,
    reference: String,
    url: String,
    uploadTimestamp: ZonedDateTime,
    checksum: String,
    fileName: String,
    fileMimeType: String
  ) extends FileUpload

  object Accepted {
    val tag = "accepted"
    implicit val formats: Format[Accepted] = Json.format[Accepted]
  }

  case class Rejected(
    orderNumber: Int,
    reference: String,
    failureReason: UpscanNotification.FailureReason,
    failureMessage: String
  ) extends FileUpload

  object Rejected {
    val tag = "rejected"
    implicit val formats: Format[Rejected] = Json.format[Rejected]
  }

  implicit def reads: Reads[FileUpload] =
    Reads {
      case o: JsObject if (o \ Initiated.tag).isDefined => Initiated.formats.reads((o \ Initiated.tag).get)
      case o: JsObject if (o \ Posted.tag).isDefined    => Posted.formats.reads((o \ Posted.tag).get)
      case o: JsObject if (o \ Accepted.tag).isDefined  => Accepted.formats.reads((o \ Accepted.tag).get)
      case o: JsObject if (o \ Rejected.tag).isDefined  => Rejected.formats.reads((o \ Rejected.tag).get)
      case _                                            => JsError("Invalid format of FileUpload")
    }

  implicit def writes: Writes[FileUpload] =
    new Writes[FileUpload] {
      override def writes(o: FileUpload): JsValue =
        o match {
          case i: Initiated => Initiated.formats.transform(v => Json.obj(Initiated.tag -> v)).writes(i)
          case i: Posted    => Posted.formats.transform(v => Json.obj(Posted.tag -> v)).writes(i)
          case i: Accepted  => Accepted.formats.transform(v => Json.obj(Accepted.tag -> v)).writes(i)
          case i: Rejected  => Rejected.formats.transform(v => Json.obj(Rejected.tag -> v)).writes(i)
        }
    }

}
