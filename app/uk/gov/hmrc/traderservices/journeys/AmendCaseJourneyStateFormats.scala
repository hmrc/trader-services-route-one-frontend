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
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.State
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.State._
import uk.gov.hmrc.play.fsm.JsonStateFormats
import play.api.libs.functional.syntax._
import uk.gov.hmrc.traderservices.models.UploadRequest
import uk.gov.hmrc.traderservices.models.FileUploads
import uk.gov.hmrc.traderservices.models.FileUploadError
import uk.gov.hmrc.traderservices.models.FileUpload

object AmendCaseJourneyStateFormats extends JsonStateFormats[State] {

  val selectAmendScenarioFormat = Json.format[SelectAmendScenario]

  override val serializeStateProperties: PartialFunction[State, JsValue] = {
    case s: SelectAmendScenario => selectAmendScenarioFormat.writes(s)
  }

  override def deserializeState(stateName: String, properties: JsValue): JsResult[State] =
    stateName match {
      case "EnterCaseReferenceNumber" => JsSuccess(EnterCaseReferenceNumber)
      case "WorkInProgressDeadEnd"    => JsSuccess(WorkInProgressDeadEnd)
      case "SelectAmendScenario"      => selectAmendScenarioFormat.reads(properties)
      case _                          => JsError(s"Unknown state name $stateName")
    }
}
