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

package uk.gov.hmrc.traderservices.connectors

import play.api.libs.json.{Format, Json}

import java.time.LocalDateTime
import scala.util.Try
import scala.util.Success
import scala.util.Failure

case class TraderServicesResult(caseId: String, generatedAt: LocalDateTime)

object TraderServicesResult {
  implicit val formats: Format[TraderServicesResult] =
    Json.format[TraderServicesResult]
}
case class TraderServicesCaseResponse(
  // Identifier associated with a request,
  correlationId: String,
  // Represents an error occurred
  error: Option[ApiError] = None,
  // Represents the result
  result: Option[TraderServicesResult] = None
)

object TraderServicesCaseResponse {

  implicit val formats: Format[TraderServicesCaseResponse] =
    Json.format[TraderServicesCaseResponse]

  final def shouldRetry(response: Try[TraderServicesCaseResponse]): Boolean =
    response match {
      case Success(result) if result.error.map(_.errorCode).exists(_.matches("5\\d\\d")) => true
      case Failure(TraderServicesApiError(e))                                            => true
      case _                                                                             => false
    }

  final def errorMessage(response: TraderServicesCaseResponse): String =
    s"${response.error.map(_.errorCode).getOrElse("")} ${response.error.flatMap(_.errorMessage).getOrElse("")}"
}
