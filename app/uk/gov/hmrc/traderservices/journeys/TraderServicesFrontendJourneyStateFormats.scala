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
  val answerImportQuestionsFormat = Json.format[AnswerImportQuestions]
  val answerExportQuestionsRequestTypeFormat = Json.format[AnswerExportQuestionsRequestType]
  val answerExportQuestionsRouteTypeFormat = Json.format[AnswerExportQuestionsRouteType]
  val answerExportQuestionsGoodsPriorityFormat = Json.format[AnswerExportQuestionsGoodsPriority]
  val answerExportQuestionsFreightTypeFormat = Json.format[AnswerExportQuestionsFreightType]

  override val serializeStateProperties: PartialFunction[State, JsValue] = {
    case s: EnterDeclarationDetails            => enterDeclarationDetailsFormat.writes(s)
    case s: AnswerExportQuestionsRequestType   => answerExportQuestionsRequestTypeFormat.writes(s)
    case s: AnswerExportQuestionsRouteType     => answerExportQuestionsRouteTypeFormat.writes(s)
    case s: AnswerExportQuestionsGoodsPriority => answerExportQuestionsGoodsPriorityFormat.writes(s)
    case s: AnswerExportQuestionsFreightType   => answerExportQuestionsFreightTypeFormat.writes(s)
    case s: AnswerImportQuestions              => answerImportQuestionsFormat.writes(s)
  }

  override def deserializeState(stateName: String, properties: JsValue): JsResult[State] =
    stateName match {
      case "Start"                              => JsSuccess(Start)
      case "EnterDeclarationDetails"            => enterDeclarationDetailsFormat.reads(properties)
      case "AnswerExportQuestionsRequestType"   => answerExportQuestionsRequestTypeFormat.reads(properties)
      case "AnswerExportQuestionsRouteType"     => answerExportQuestionsRouteTypeFormat.reads(properties)
      case "AnswerExportQuestionsGoodsPriority" => answerExportQuestionsGoodsPriorityFormat.reads(properties)
      case "AnswerExportQuestionsFreightType"   => answerExportQuestionsFreightTypeFormat.reads(properties)
      case "AnswerImportQuestions"              => answerImportQuestionsFormat.reads(properties)
      case "WorkInProgressDeadEnd"              => JsSuccess(WorkInProgressDeadEnd)
      case _                                    => JsError(s"Unknown state name $stateName")
    }
}
