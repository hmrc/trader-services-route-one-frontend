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

import play.api.Logger
import uk.gov.hmrc.traderservices.connectors.TraderServicesCreateCaseRequest
import uk.gov.hmrc.traderservices.connectors.TraderServicesCaseResponse
import uk.gov.hmrc.traderservices.connectors.TraderServicesUpdateCaseRequest
import play.api.libs.json.Json
import uk.gov.hmrc.traderservices.models._
import play.api.libs.json.Writes

object JourneyLog {

  sealed trait CreateCaseLog

  case class ExportCreateCaseLog(
    action: String = "create",
    success: Boolean,
    `type`: String,
    request: Option[ExportRequestType],
    route: Option[ExportRouteType],
    transport: Option[ExportFreightType],
    hasPriority: Option[Boolean],
    priorityGoods: Option[ExportPriorityGoods],
    uploadsSuccess: Option[Int] = None,
    uploadsFailure: Option[Int] = None,
    errorCode: Option[String] = None
  ) extends CreateCaseLog

  case class ImportCreateCaseLog(
    action: String = "create",
    success: Boolean,
    `type`: String,
    request: Option[ImportRequestType],
    route: Option[ImportRouteType],
    transport: Option[ImportFreightType],
    hasPriority: Option[Boolean],
    priorityGoods: Option[ImportPriorityGoods],
    hasALVS: Option[Boolean],
    uploadsSuccess: Option[Int] = None,
    uploadsFailure: Option[Int] = None,
    errorCode: Option[String] = None
  ) extends CreateCaseLog

  object CreateCaseLog {
    def apply(request: TraderServicesCreateCaseRequest, response: TraderServicesCaseResponse): CreateCaseLog =
      request.questionsAnswers match {
        case q: ExportQuestions =>
          ExportCreateCaseLog(
            success = response.isSuccess,
            `type` = "export",
            request = q.requestType,
            route = q.routeType,
            transport = q.freightType,
            hasPriority = q.hasPriorityGoods,
            priorityGoods = q.priorityGoods,
            uploadsSuccess = response.result.map(_.fileTransferResults.count(_.success)),
            uploadsFailure = response.result.map(_.fileTransferResults.count(f => !f.success)),
            errorCode = response.error.map(_.errorCode)
          )

        case q: ImportQuestions =>
          ImportCreateCaseLog(
            success = response.isSuccess,
            `type` = "import",
            request = q.requestType,
            route = q.routeType,
            hasPriority = q.hasPriorityGoods,
            priorityGoods = q.priorityGoods,
            transport = q.freightType,
            hasALVS = q.hasALVS,
            uploadsSuccess = response.result.map(_.fileTransferResults.count(_.success)),
            uploadsFailure = response.result.map(_.fileTransferResults.count(f => !f.success)),
            errorCode = response.error.map(_.errorCode)
          )
      }

    val formatExportCreateCaseLog = Json.format[ExportCreateCaseLog]
    val formatImportCreateCaseLog = Json.format[ImportCreateCaseLog]

    implicit val writer = Writes.apply[CreateCaseLog] {
      case l: ExportCreateCaseLog => formatExportCreateCaseLog.writes(l)
      case l: ImportCreateCaseLog => formatImportCreateCaseLog.writes(l)
    }
  }

  case class UpdateCaseLog(
    success: Boolean,
    action: String = "update",
    `type`: TypeOfAmendment,
    uploadsSuccess: Option[Int] = None,
    uploadsFailure: Option[Int] = None,
    errorCode: Option[String] = None
  )

  object UpdateCaseLog {
    def apply(request: TraderServicesUpdateCaseRequest, response: TraderServicesCaseResponse): UpdateCaseLog =
      UpdateCaseLog(
        response.isSuccess,
        `type` = request.typeOfAmendment,
        uploadsSuccess = response.result.map(_.fileTransferResults.count(_.success)),
        uploadsFailure = response.result.map(_.fileTransferResults.count(f => !f.success)),
        errorCode = response.error.map(_.errorCode)
      )

    implicit val formatUpdateCaseLog = Json.format[UpdateCaseLog]
  }

  final def logCreateCase(
    userId: Option[String],
    request: TraderServicesCreateCaseRequest,
    response: TraderServicesCaseResponse
  ): Unit =
    Logger(getClass).info(s"json:${Json.stringify(Json.toJson(CreateCaseLog(request, response)))}")

  final def logUpdateCase(
    userId: Option[String],
    request: TraderServicesUpdateCaseRequest,
    response: TraderServicesCaseResponse
  ): Unit =
    Logger(getClass).info(s"json:${Json.stringify(Json.toJson(UpdateCaseLog(request, response)))}")

}
