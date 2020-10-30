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

case class ImportQuestions(
  requestType: Option[ImportRequestType] = None,
  routeType: Option[ImportRouteType] = None,
  hasPriorityGoods: Option[Boolean] = None,
  priorityGoods: Option[ImportPriorityGoods] = None,
  hasALVS: Option[Boolean] = None,
  freightType: Option[ImportFreightType] = None,
  vesselDetails: Option[VesselDetails] = None,
  contactInfo: Option[ImportContactInfo] = None
) extends QuestionsAnswers

object ImportQuestions {
  val tag = "import"
  implicit val formats: Format[ImportQuestions] = Json.format[ImportQuestions]

  def from(questionsAnswers: QuestionsAnswers): ImportQuestions =
    questionsAnswers match {
      case i: ImportQuestions => i
      case e: ExportQuestions => ImportQuestions()
    }
}
