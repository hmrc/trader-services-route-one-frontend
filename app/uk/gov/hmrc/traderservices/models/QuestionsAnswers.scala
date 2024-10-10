/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.libs.json._

sealed trait QuestionsAnswers

final case class ImportQuestions(
  requestType: Option[ImportRequestType] = None,
  routeType: Option[ImportRouteType] = None,
  reason: Option[String] = None,
  hasPriorityGoods: Option[Boolean] = None,
  priorityGoods: Option[ImportPriorityGoods] = None,
  hasALVS: Option[Boolean] = None,
  freightType: Option[ImportFreightType] = None,
  vesselDetails: Option[VesselDetails] = None,
  contactInfo: Option[ImportContactInfo] = None
) extends QuestionsAnswers

final case class ExportQuestions(
  requestType: Option[ExportRequestType] = None,
  routeType: Option[ExportRouteType] = None,
  reason: Option[String] = None,
  hasPriorityGoods: Option[Boolean] = None,
  priorityGoods: Option[ExportPriorityGoods] = None,
  freightType: Option[ExportFreightType] = None,
  vesselDetails: Option[VesselDetails] = None,
  contactInfo: Option[ExportContactInfo] = None
) extends QuestionsAnswers

object QuestionsAnswers {

  implicit lazy val reads: Reads[QuestionsAnswers] =
    Reads {
      case o: JsObject if (o \ ExportQuestions.tag).isDefined =>
        ExportQuestions.formats.reads((o \ ExportQuestions.tag).get)
      case o: JsObject if (o \ ImportQuestions.tag).isDefined =>
        ImportQuestions.formats.reads((o \ ImportQuestions.tag).get)
      case _ => JsError("Invalid format of QuestionsAnswers")
    }

  implicit lazy val writes: Writes[QuestionsAnswers] =
    Writes {
      case e: ExportQuestions =>
        ExportQuestions.formats.transform(v => Json.obj(ExportQuestions.tag -> v)).writes(e)
      case i: ImportQuestions =>
        ImportQuestions.formats.transform(v => Json.obj(ImportQuestions.tag -> v)).writes(i)
      case _ => throw new IllegalArgumentException("Unknown QuestionsAnswers type")
    }

}

object ImportQuestions {
  val tag = "import"
  implicit val formats: Format[ImportQuestions] = Json.format[ImportQuestions]
}

object ExportQuestions {
  val tag = "export"
  implicit val formats: Format[ExportQuestions] = Json.format[ExportQuestions]
}
