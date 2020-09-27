package uk.gov.hmrc.traderservices.models

import play.api.libs.json.{Format, Json}

case class ExportQuestions(
  requestType: ExportRequestType,
  routeType: ExportRouteType,
  goodsPriority: ExportGoodsPriority)

object ExportQuestions {

  implicit val formats: Format[ExportQuestions] = Json.format[ExportQuestions]
}
