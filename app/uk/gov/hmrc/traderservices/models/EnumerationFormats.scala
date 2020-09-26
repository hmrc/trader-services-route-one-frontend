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
  * Designed to be mixed in the companion object of the enum type and as typeclass.
  * @tparam A enum type
  */
trait EnumerationFormats[A] {

  /** Set of enum values recognized by the formatter. */
  val values: Set[A]

  private lazy val valuesMap: Map[String, A] =
    values.map(value => (normalize(value.getClass.getSimpleName), value)).toMap

  lazy val keys: Set[String] = values.map(value => normalize(value.getClass.getSimpleName))

  /** Checks if given string is a valid enum key. */
  lazy val isValidKey: String => Boolean = keys.contains

  /** Optionally returns string key for a given enum value, if recognized or None */
  def keyOf(value: A): Option[String] =
    Option(normalize(value.getClass.getSimpleName))
      .filter(isValidKey)

  /** Optionally returns enum for a given key, if exists or None */
  def valueOf(key: String): Option[A] = valuesMap.get(key)

  implicit val format: Format[A] = Format(
    Reads {
      case JsString(key) =>
        valueOf(key)
          .map(JsSuccess.apply(_))
          .getOrElse(JsError(s"Unsupported enum key $key, should be one of ${keys.mkString(",")}"))

      case json => JsError(s"Expected json string but got ${json.getClass.getSimpleName}")
    },
    Writes.apply(value =>
      keyOf(value)
        .map(JsString.apply)
        .getOrElse(
          throw new IllegalStateException(
            s"Unsupported enum value $value, should be one of ${values.mkString(",")}"
          )
        )
    )
  )

  private def normalize(name: String): String = if (name.endsWith("$")) name.dropRight(1) else name

  /** Instance of a typeclass declaration */
  implicit val enumerationFormats: EnumerationFormats[A] = this

}
