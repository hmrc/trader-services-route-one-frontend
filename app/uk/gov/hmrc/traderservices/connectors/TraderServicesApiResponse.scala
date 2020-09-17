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

package uk.gov.hmrc.traderservices.connectors

import play.api.libs.json.{Format, Json}

case class TraderServicesApiResponse(
  // Identifier associated with a checks,
  // if x-correlation-id is not provided in request headers, a new id generated using token service
  correlationId: String,
  // Represents an error occurred
  error: Option[ApiError] = None,
  // Represents the result
  result: Option[String] = None
)

object TraderServicesApiResponse {
  implicit val formats = Json.format[TraderServicesApiResponse]
}
