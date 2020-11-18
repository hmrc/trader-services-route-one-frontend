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
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.FileUploadState._
import uk.gov.hmrc.play.fsm.JsonStateFormats
import uk.gov.hmrc.traderservices.models.AmendCaseStateModel

object AmendCaseJourneyStateFormats
    extends FileUploadJourneyStateFormats(AmendCaseJourneyModel) with JsonStateFormats[State] {

  val enterCaseReferenceNumberFormat = Json.format[EnterCaseReferenceNumber]
  val selectTypeOfAmendmentFormat = Json.format[SelectTypeOfAmendment]
  val enterResponseTextFormat = Json.format[EnterResponseText]
  val amendCaseConfirmationFormat = Json.format[AmendCaseConfirmation]

  override val fileUploadHostDataFormat: Format[AmendCaseStateModel] =
    AmendCaseStateModel.formats

  override val serializeStateProperties: PartialFunction[State, JsValue] = {
    case s: EnterCaseReferenceNumber   => enterCaseReferenceNumberFormat.writes(s)
    case s: SelectTypeOfAmendment      => selectTypeOfAmendmentFormat.writes(s)
    case s: EnterResponseText          => enterResponseTextFormat.writes(s)
    case s: UploadFile                 => uploadFileFormat.writes(s)
    case s: FileUploaded               => fileUploadedFormat.writes(s)
    case s: WaitingForFileVerification => waitingForFileVerificationFormat.writes(s)
    case s: AmendCaseConfirmation      => amendCaseConfirmationFormat.writes(s)
  }

  override def deserializeState(stateName: String, properties: JsValue): JsResult[State] =
    stateName match {
      case "EnterCaseReferenceNumber"   => enterCaseReferenceNumberFormat.reads(properties)
      case "SelectTypeOfAmendment"      => selectTypeOfAmendmentFormat.reads(properties)
      case "EnterResponseText"          => enterResponseTextFormat.reads(properties)
      case "UploadFile"                 => uploadFileFormat.reads(properties)
      case "FileUploaded"               => fileUploadedFormat.reads(properties)
      case "WaitingForFileVerification" => waitingForFileVerificationFormat.reads(properties)
      case "AmendCaseConfirmation"      => amendCaseConfirmationFormat.reads(properties)
      case "WorkInProgressDeadEnd"      => JsSuccess(WorkInProgressDeadEnd)
      case _                            => JsError(s"Unknown state name $stateName")
    }
}
