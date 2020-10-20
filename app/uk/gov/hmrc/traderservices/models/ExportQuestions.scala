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

case class ExportQuestions(
  requestType: Option[ExportRequestType] = None,
  routeType: Option[ExportRouteType] = None,
  hasPriorityGoods: Option[Boolean] = None,
  priorityGoods: Option[ExportPriorityGoods] = None,
  freightType: Option[ExportFreightType] = None,
  vesselDetails: Option[VesselDetails] = None,
  contactInfo: Option[ExportContactInfo] = None
) {

  def shouldAskRouteQuestion: Boolean =
    requestType.forall(_ != ExportRequestType.Hold)

  def isVesselDetailsAnswerMandatory: Boolean =
    requestType.contains(ExportRequestType.C1601)

}

object ExportQuestions {

  implicit val formats: Format[ExportQuestions] = Json.format[ExportQuestions]
}
