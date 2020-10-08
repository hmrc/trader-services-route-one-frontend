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
import uk.gov.hmrc.traderservices.journeys.TraderServicesFrontendJourneyModel.State
import uk.gov.hmrc.traderservices.journeys.TraderServicesFrontendJourneyModel.State._
import uk.gov.hmrc.play.fsm.JsonStateFormats

object TraderServicesFrontendJourneyStateFormats extends JsonStateFormats[State] {

  val enterDeclarationDetailsFormat = Json.format[EnterDeclarationDetails]
  val answerExportQuestionsRequestTypeFormat = Json.format[AnswerExportQuestionsRequestType]
  val answerExportQuestionsRouteTypeFormat = Json.format[AnswerExportQuestionsRouteType]
  val answerExportQuestionsHasPriorityGoodsFormat = Json.format[AnswerExportQuestionsHasPriorityGoods]
  val answerExportQuestionsWhichPriorityGoodsFormat = Json.format[AnswerExportQuestionsWhichPriorityGoods]
  val answerExportQuestionsFreightTypeFormat = Json.format[AnswerExportQuestionsFreightType]
  val answerExportQuestionsVesselInfoFormat = Json.format[AnswerExportQuestionsVesselInfo]
  val answerExportQuestionsContactInfoFormat = Json.format[AnswerExportQuestionsContactInfo]
  val answerImportQuestionsRequestTypeFormat = Json.format[AnswerImportQuestionsRequestType]
  val answerImportQuestionsRouteTypeFormat = Json.format[AnswerImportQuestionsRouteType]
  val answerImportQuestionsHasPriorityGoodsFormat = Json.format[AnswerImportQuestionsHasPriorityGoods]
  val answerImportQuestionsWhichPriorityGoodsFormat = Json.format[AnswerImportQuestionsWhichPriorityGoods]
  val answerImportQuestionsALVSFormat = Json.format[AnswerImportQuestionsALVS]
  val answerImportQuestionsFreightTypeFormat = Json.format[AnswerImportQuestionsFreightType]
  val answerImportQuestionsVesselInfoFormat = Json.format[AnswerImportQuestionsVesselInfo]
  val answerImportQuestionsContactInfoFormat = Json.format[AnswerImportQuestionsContactInfo]

  override val serializeStateProperties: PartialFunction[State, JsValue] = {
    case s: EnterDeclarationDetails                 => enterDeclarationDetailsFormat.writes(s)
    case s: AnswerExportQuestionsRequestType        => answerExportQuestionsRequestTypeFormat.writes(s)
    case s: AnswerExportQuestionsRouteType          => answerExportQuestionsRouteTypeFormat.writes(s)
    case s: AnswerExportQuestionsHasPriorityGoods   => answerExportQuestionsHasPriorityGoodsFormat.writes(s)
    case s: AnswerExportQuestionsWhichPriorityGoods => answerExportQuestionsWhichPriorityGoodsFormat.writes(s)
    case s: AnswerExportQuestionsFreightType        => answerExportQuestionsFreightTypeFormat.writes(s)
    case s: AnswerExportQuestionsVesselInfo         => answerExportQuestionsVesselInfoFormat.writes(s)
    case s: AnswerExportQuestionsContactInfo        => answerExportQuestionsContactInfoFormat.writes(s)
    case s: AnswerImportQuestionsRequestType        => answerImportQuestionsRequestTypeFormat.writes(s)
    case s: AnswerImportQuestionsRouteType          => answerImportQuestionsRouteTypeFormat.writes(s)
    case s: AnswerImportQuestionsHasPriorityGoods   => answerImportQuestionsHasPriorityGoodsFormat.writes(s)
    case s: AnswerImportQuestionsWhichPriorityGoods => answerImportQuestionsWhichPriorityGoodsFormat.writes(s)
    case s: AnswerImportQuestionsALVS               => answerImportQuestionsALVSFormat.writes(s)
    case s: AnswerImportQuestionsFreightType        => answerImportQuestionsFreightTypeFormat.writes(s)
    case s: AnswerImportQuestionsVesselInfo         => answerImportQuestionsVesselInfoFormat.writes(s)
    case s: AnswerImportQuestionsContactInfo        => answerImportQuestionsContactInfoFormat.writes(s)
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
      case "AnswerExportQuestionsVesselInfo"         => answerExportQuestionsVesselInfoFormat.reads(properties)
      case "AnswerExportQuestionsContactInfo"        => answerExportQuestionsContactInfoFormat.reads(properties)
      case "AnswerImportQuestionsRequestType"        => answerImportQuestionsRequestTypeFormat.reads(properties)
      case "AnswerImportQuestionsRouteType"          => answerImportQuestionsRouteTypeFormat.reads(properties)
      case "AnswerImportQuestionsHasPriorityGoods"   => answerImportQuestionsHasPriorityGoodsFormat.reads(properties)
      case "AnswerImportQuestionsWhichPriorityGoods" => answerImportQuestionsWhichPriorityGoodsFormat.reads(properties)
      case "AnswerImportQuestionsALVS"               => answerImportQuestionsALVSFormat.reads(properties)
      case "AnswerImportQuestionsFreightType"        => answerImportQuestionsFreightTypeFormat.reads(properties)
      case "AnswerImportQuestionsVesselInfo"         => answerImportQuestionsVesselInfoFormat.reads(properties)
      case "AnswerImportQuestionsContactInfo"        => answerImportQuestionsContactInfoFormat.reads(properties)
      case "WorkInProgressDeadEnd"                   => JsSuccess(WorkInProgressDeadEnd)
      case _                                         => JsError(s"Unknown state name $stateName")
    }
}
