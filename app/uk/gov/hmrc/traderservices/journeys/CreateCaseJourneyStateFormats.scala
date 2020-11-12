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

package uk.gov.hmrc.traderservices.journeys

import play.api.libs.json._
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.State
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.FileUploadState._
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.FileUploadHostData
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.State._
import uk.gov.hmrc.play.fsm.JsonStateFormats
import play.api.libs.functional.syntax._
import uk.gov.hmrc.traderservices.models.UploadRequest
import uk.gov.hmrc.traderservices.models.FileUploads
import uk.gov.hmrc.traderservices.models.FileUploadError
import uk.gov.hmrc.traderservices.models.FileUpload

object CreateCaseJourneyStateFormats extends JsonStateFormats[State] {

  val enterDeclarationDetailsFormat = Json.format[EnterDeclarationDetails]
  val answerExportQuestionsRequestTypeFormat = Json.format[AnswerExportQuestionsRequestType]
  val answerExportQuestionsRouteTypeFormat = Json.format[AnswerExportQuestionsRouteType]
  val answerExportQuestionsHasPriorityGoodsFormat = Json.format[AnswerExportQuestionsHasPriorityGoods]
  val answerExportQuestionsWhichPriorityGoodsFormat = Json.format[AnswerExportQuestionsWhichPriorityGoods]
  val answerExportQuestionsFreightTypeFormat = Json.format[AnswerExportQuestionsFreightType]
  val answerExportQuestionsMandatoryVesselInfoFormat = Json.format[AnswerExportQuestionsMandatoryVesselInfo]
  val answerExportQuestionsOptionalVesselInfoFormat = Json.format[AnswerExportQuestionsOptionalVesselInfo]
  val answerExportQuestionsContactInfoFormat = Json.format[AnswerExportQuestionsContactInfo]
  val exportQuestionsSummaryFormat = Json.format[ExportQuestionsSummary]
  val answerImportQuestionsRequestTypeFormat = Json.format[AnswerImportQuestionsRequestType]
  val answerImportQuestionsRouteTypeFormat = Json.format[AnswerImportQuestionsRouteType]
  val answerImportQuestionsHasPriorityGoodsFormat = Json.format[AnswerImportQuestionsHasPriorityGoods]
  val answerImportQuestionsWhichPriorityGoodsFormat = Json.format[AnswerImportQuestionsWhichPriorityGoods]
  val answerImportQuestionsALVSFormat = Json.format[AnswerImportQuestionsALVS]
  val answerImportQuestionsFreightTypeFormat = Json.format[AnswerImportQuestionsFreightType]
  val answerImportQuestionsOptionalVesselInfoFormat = Json.format[AnswerImportQuestionsOptionalVesselInfo]
  val answerImportQuestionsMandatoryVesselInfoFormat = Json.format[AnswerImportQuestionsMandatoryVesselInfo]
  val answerImportQuestionsContactInfoFormat = Json.format[AnswerImportQuestionsContactInfo]
  val importQuestionsSummaryFormat = Json.format[ImportQuestionsSummary]
  val createCaseConfirmationFormat = Json.format[CreateCaseConfirmation]

  implicit val fileUploadHostDataFormat: Format[FileUploadHostData] = Json.format[FileUploadHostData]

  val uploadFileFormat = Format(
    (
      (__ \ "hostData").read[FileUploadHostData] and
        (__ \ "reference").read[String] and
        (__ \ "uploadRequest").read[UploadRequest] and
        (__ \ "fileUploads").read[FileUploads] and
        (__ \ "maybeUploadError").readNullableWithDefault[FileUploadError](None)
    )(UploadFile.apply _),
    (
      (__ \ "hostData").write[FileUploadHostData] and
        (__ \ "reference").write[String] and
        (__ \ "uploadRequest").write[UploadRequest] and
        (__ \ "fileUploads").write[FileUploads] and
        (__ \ "maybeUploadError").writeNullable[FileUploadError]
    )(unlift(UploadFile.unapply))
  )

  val waitingForFileVerificationFormat = Format(
    (
      (__ \ "hostData").read[FileUploadHostData] and
        (__ \ "reference").read[String] and
        (__ \ "uploadRequest").read[UploadRequest] and
        (__ \ "currentFileUpload").read[FileUpload] and
        (__ \ "fileUploads").read[FileUploads]
    )(WaitingForFileVerification.apply _),
    (
      (__ \ "hostData").write[FileUploadHostData] and
        (__ \ "reference").write[String] and
        (__ \ "uploadRequest").write[UploadRequest] and
        (__ \ "currentFileUpload").write[FileUpload] and
        (__ \ "fileUploads").write[FileUploads]
    )(unlift(WaitingForFileVerification.unapply))
  )

  val fileUploadedFormat = Format(
    (
      (__ \ "hostData").read[FileUploadHostData] and
        (__ \ "fileUploads").read[FileUploads] and
        (__ \ "acknowledged").read[Boolean]
    )(FileUploaded.apply _),
    (
      (__ \ "hostData").write[FileUploadHostData] and
        (__ \ "fileUploads").write[FileUploads] and
        (__ \ "acknowledged").write[Boolean]
    )(unlift(FileUploaded.unapply))
  )

  override val serializeStateProperties: PartialFunction[State, JsValue] = {
    case s: EnterDeclarationDetails                  => enterDeclarationDetailsFormat.writes(s)
    case s: AnswerExportQuestionsRequestType         => answerExportQuestionsRequestTypeFormat.writes(s)
    case s: AnswerExportQuestionsRouteType           => answerExportQuestionsRouteTypeFormat.writes(s)
    case s: AnswerExportQuestionsHasPriorityGoods    => answerExportQuestionsHasPriorityGoodsFormat.writes(s)
    case s: AnswerExportQuestionsWhichPriorityGoods  => answerExportQuestionsWhichPriorityGoodsFormat.writes(s)
    case s: AnswerExportQuestionsFreightType         => answerExportQuestionsFreightTypeFormat.writes(s)
    case s: AnswerExportQuestionsMandatoryVesselInfo => answerExportQuestionsMandatoryVesselInfoFormat.writes(s)
    case s: AnswerExportQuestionsOptionalVesselInfo  => answerExportQuestionsOptionalVesselInfoFormat.writes(s)
    case s: AnswerExportQuestionsContactInfo         => answerExportQuestionsContactInfoFormat.writes(s)
    case s: ExportQuestionsSummary                   => exportQuestionsSummaryFormat.writes(s)
    case s: AnswerImportQuestionsRequestType         => answerImportQuestionsRequestTypeFormat.writes(s)
    case s: AnswerImportQuestionsRouteType           => answerImportQuestionsRouteTypeFormat.writes(s)
    case s: AnswerImportQuestionsHasPriorityGoods    => answerImportQuestionsHasPriorityGoodsFormat.writes(s)
    case s: AnswerImportQuestionsWhichPriorityGoods  => answerImportQuestionsWhichPriorityGoodsFormat.writes(s)
    case s: AnswerImportQuestionsALVS                => answerImportQuestionsALVSFormat.writes(s)
    case s: AnswerImportQuestionsFreightType         => answerImportQuestionsFreightTypeFormat.writes(s)
    case s: AnswerImportQuestionsOptionalVesselInfo  => answerImportQuestionsOptionalVesselInfoFormat.writes(s)
    case s: AnswerImportQuestionsMandatoryVesselInfo => answerImportQuestionsMandatoryVesselInfoFormat.writes(s)
    case s: AnswerImportQuestionsContactInfo         => answerImportQuestionsContactInfoFormat.writes(s)
    case s: ImportQuestionsSummary                   => importQuestionsSummaryFormat.writes(s)
    case s: UploadFile                               => uploadFileFormat.writes(s)
    case s: FileUploaded                             => fileUploadedFormat.writes(s)
    case s: WaitingForFileVerification               => waitingForFileVerificationFormat.writes(s)
    case s: CreateCaseConfirmation                   => createCaseConfirmationFormat.writes(s)
  }

  override def deserializeState(stateName: String, properties: JsValue): JsResult[State] =
    stateName match {
      case "Start"                                   => JsSuccess(Start)
      case "EnterDeclarationDetails"                 => enterDeclarationDetailsFormat.reads(properties)
      case "AnswerExportQuestionsRequestType"        => answerExportQuestionsRequestTypeFormat.reads(properties)
      case "AnswerExportQuestionsRouteType"          => answerExportQuestionsRouteTypeFormat.reads(properties)
      case "AnswerExportQuestionsHasPriorityGoods"   => answerExportQuestionsHasPriorityGoodsFormat.reads(properties)
      case "AnswerExportQuestionsWhichPriorityGoods" => answerExportQuestionsWhichPriorityGoodsFormat.reads(properties)
      case "AnswerExportQuestionsFreightType"        => answerExportQuestionsFreightTypeFormat.reads(properties)
      case "AnswerExportQuestionsMandatoryVesselInfo" =>
        answerExportQuestionsMandatoryVesselInfoFormat.reads(properties)
      case "AnswerExportQuestionsOptionalVesselInfo" => answerExportQuestionsOptionalVesselInfoFormat.reads(properties)
      case "AnswerExportQuestionsContactInfo"        => answerExportQuestionsContactInfoFormat.reads(properties)
      case "ExportQuestionsSummary"                  => exportQuestionsSummaryFormat.reads(properties)
      case "AnswerImportQuestionsRequestType"        => answerImportQuestionsRequestTypeFormat.reads(properties)
      case "AnswerImportQuestionsRouteType"          => answerImportQuestionsRouteTypeFormat.reads(properties)
      case "AnswerImportQuestionsHasPriorityGoods"   => answerImportQuestionsHasPriorityGoodsFormat.reads(properties)
      case "AnswerImportQuestionsWhichPriorityGoods" => answerImportQuestionsWhichPriorityGoodsFormat.reads(properties)
      case "AnswerImportQuestionsALVS"               => answerImportQuestionsALVSFormat.reads(properties)
      case "AnswerImportQuestionsFreightType"        => answerImportQuestionsFreightTypeFormat.reads(properties)
      case "AnswerImportQuestionsOptionalVesselInfo" => answerImportQuestionsOptionalVesselInfoFormat.reads(properties)
      case "AnswerImportQuestionsMandatoryVesselInfo" =>
        answerImportQuestionsMandatoryVesselInfoFormat.reads(properties)
      case "AnswerImportQuestionsContactInfo" => answerImportQuestionsContactInfoFormat.reads(properties)
      case "ImportQuestionsSummary"           => importQuestionsSummaryFormat.reads(properties)
      case "UploadFile"                       => uploadFileFormat.reads(properties)
      case "FileUploaded"                     => fileUploadedFormat.reads(properties)
      case "WaitingForFileVerification"       => waitingForFileVerificationFormat.reads(properties)
      case "CreateCaseConfirmation"           => createCaseConfirmationFormat.reads(properties)
      case "WorkInProgressDeadEnd"            => JsSuccess(WorkInProgressDeadEnd)
      case _                                  => JsError(s"Unknown state name $stateName")
    }
}
