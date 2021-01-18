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

import play.api.libs.json._
import play.api.libs.functional.syntax._
import uk.gov.hmrc.traderservices.models.UploadRequest
import uk.gov.hmrc.traderservices.models.FileUploads
import uk.gov.hmrc.traderservices.models.FileUploadError
import uk.gov.hmrc.traderservices.models.FileUpload

abstract class FileUploadJourneyStateFormats[M <: FileUploadJourneyModelMixin](val model: M) {

  import model.FileUploadState._

  val fileUploadHostDataFormat: Format[model.FileUploadHostData]

  lazy val uploadFileFormat = Format(
    (
      (__ \ "hostData").read[model.FileUploadHostData](fileUploadHostDataFormat) and
        (__ \ "reference").read[String] and
        (__ \ "uploadRequest").read[UploadRequest] and
        (__ \ "fileUploads").read[FileUploads] and
        (__ \ "maybeUploadError").readNullableWithDefault[FileUploadError](None)
    )(UploadFile.apply _),
    (
      (__ \ "hostData").write[model.FileUploadHostData](fileUploadHostDataFormat) and
        (__ \ "reference").write[String] and
        (__ \ "uploadRequest").write[UploadRequest] and
        (__ \ "fileUploads").write[FileUploads] and
        (__ \ "maybeUploadError").writeNullable[FileUploadError]
    )(unlift(UploadFile.unapply))
  )

  lazy val waitingForFileVerificationFormat = Format(
    (
      (__ \ "hostData").read[model.FileUploadHostData](fileUploadHostDataFormat) and
        (__ \ "reference").read[String] and
        (__ \ "uploadRequest").read[UploadRequest] and
        (__ \ "currentFileUpload").read[FileUpload] and
        (__ \ "fileUploads").read[FileUploads]
    )(WaitingForFileVerification.apply _),
    (
      (__ \ "hostData").write[model.FileUploadHostData](fileUploadHostDataFormat) and
        (__ \ "reference").write[String] and
        (__ \ "uploadRequest").write[UploadRequest] and
        (__ \ "currentFileUpload").write[FileUpload] and
        (__ \ "fileUploads").write[FileUploads]
    )(unlift(WaitingForFileVerification.unapply))
  )

  lazy val fileUploadedFormat = Format(
    (
      (__ \ "hostData").read[model.FileUploadHostData](fileUploadHostDataFormat) and
        (__ \ "fileUploads").read[FileUploads] and
        (__ \ "acknowledged").read[Boolean]
    )(FileUploaded.apply _),
    (
      (__ \ "hostData").write[model.FileUploadHostData](fileUploadHostDataFormat) and
        (__ \ "fileUploads").write[FileUploads] and
        (__ \ "acknowledged").write[Boolean]
    )(unlift(FileUploaded.unapply))
  )

  private val uploadRequestMapFormat: Format[Map[String, UploadRequest]] = {
    val uploadRequestFormat = implicitly[Format[UploadRequest]]
    Format(
      Reads[Map[String, UploadRequest]] {
        case obj: JsObject => JsSuccess(obj.fields.map { case (k, v) => (k, uploadRequestFormat.reads(v).get) }.toMap)
        case other         => JsError(s"Expected JsObject, but got ${other.getClass().getSimpleName()}.")
      },
      Writes[Map[String, UploadRequest]] {
        case uploadRequestMap =>
          JsObject(uploadRequestMap.mapValues(uploadRequestFormat.writes(_)))
      }
    )
  }

  lazy val uploadMultipleFilesFormat = Format(
    (
      (__ \ "hostData").read[model.FileUploadHostData](fileUploadHostDataFormat) and
        (__ \ "fileUploads").read[FileUploads]
    )(UploadMultipleFiles.apply _),
    (
      (__ \ "hostData").write[model.FileUploadHostData](fileUploadHostDataFormat) and
        (__ \ "fileUploads").write[FileUploads]
    )(unlift(UploadMultipleFiles.unapply))
  )

  val serializeFileUploadStateProperties: PartialFunction[model.State, JsValue] = {
    case s: UploadFile                 => uploadFileFormat.writes(s)
    case s: FileUploaded               => fileUploadedFormat.writes(s)
    case s: WaitingForFileVerification => waitingForFileVerificationFormat.writes(s)
    case s: UploadMultipleFiles        => uploadMultipleFilesFormat.writes(s)
  }

  def deserializeFileUploadState(stateName: String, properties: JsValue): JsResult[model.State] =
    stateName match {
      case "UploadFile"                 => uploadFileFormat.reads(properties)
      case "FileUploaded"               => fileUploadedFormat.reads(properties)
      case "WaitingForFileVerification" => waitingForFileVerificationFormat.reads(properties)
      case "UploadMultipleFiles"        => uploadMultipleFilesFormat.reads(properties)
    }

}
