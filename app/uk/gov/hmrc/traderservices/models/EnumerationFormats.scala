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

import play.api.libs.json._

/**
  * Helper trait providing JSON formatter based on the set of enum values.
  * @tparam A
  */
trait EnumerationFormats[A] {

  /** Set of enum values recognized by the formatter. */
  val values: Set[A]

  private lazy val valuesMap: Map[String, A] = values.map(v => (normalize(v.getClass.getSimpleName), v)).toMap

  /** Optionally returns enum for a given key, if exists or None */
  def valueOf(key: String): Option[A] = valuesMap.get(key)

  implicit val format: Format[A] = Format(
    Reads {
      case JsString(key) =>
        valueOf(key)
          .map(JsSuccess.apply(_))
          .getOrElse(JsError(s"Unsupported enum key $key, should be one of ${valuesMap.keys.mkString(",")}"))

      case json => JsError(s"Expected json string but got ${normalize(json.getClass.getSimpleName)}")
    },
    Writes.apply(entity =>
      if (values.contains(entity)) JsString(normalize(entity.getClass.getSimpleName))
      else
        throw new IllegalStateException(
          s"Unsupported enum value $entity, should be one of ${valuesMap.values.mkString(",")}"
        )
    )
  )

  private def normalize(name: String): String = if (name.endsWith("$")) name.dropRight(1) else name

}
