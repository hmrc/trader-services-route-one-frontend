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

package uk.gov.hmrc.traderservices.journeys

import play.api.libs.json.{Format, JsError, JsNull, JsObject, JsResult, JsValue, Json, Reads, Writes}
import uk.gov.hmrc.traderservices.utils.IdentityUtils

trait StateFormats {

  val serializeStateProperties: PartialFunction[State, JsValue]
  def deserializeState(stateName: String, properties: JsValue): JsResult[State]

  final val reads: Reads[State] = (json: JsValue) =>
    json match {
      case obj: JsObject =>
        (obj \ "state")
          .asOpt[String]
          .map(s => (obj \ "properties").asOpt[JsValue].map(p => (s, p)).getOrElse((s, JsNull))) match {
          case Some((stateName, properties)) => deserializeState(stateName, properties)
          case None                          => JsError("Missing state field")
        }

      case o => JsError(s"Cannot parse State from $o, must be JsObject.")
    }

  final val writes: Writes[State] = (state: State) =>
    if (serializeStateProperties.isDefinedAt(state)) serializeStateProperties(state) match {
      case JsNull => Json.obj("state" -> IdentityUtils.identityOf(state))
      case properties =>
        Json.obj("state" -> IdentityUtils.identityOf(state), "properties" -> properties)
    }
    else Json.obj("state" -> IdentityUtils.identityOf(state))

  final def formats: Format[State] = Format(reads, writes)
}
