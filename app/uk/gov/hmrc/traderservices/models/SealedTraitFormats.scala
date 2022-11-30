/*
 * Copyright 2022 HM Revenue & Customs
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
import scala.reflect.ClassTag

/** Helper trait providing JSON formatter based on the set of case classse of the sealed trait. Designed to be mixed in
  * the companion object of the sealed trait and as typeclass.
  * @tparam A
  *   sealed trait type
  */
trait SealedTraitFormats[A] {

  /** Formatters of the member classes. */
  val formats: Set[Case[_ <: A]]

  private lazy val formatMap: Map[String, Case[_ <: A]] =
    formats.map(m => (m.key, m)).toMap

  protected case class Case[T <: A: ClassTag](format: Format[T]) {

    val key = implicitly[ClassTag[T]].runtimeClass.getSimpleName

    def writes(value: A): JsValue =
      format.writes(value.asInstanceOf[T])

    def reads(json: JsValue): JsResult[A] =
      format.reads(json).map(_.asInstanceOf[A])
  }

  implicit val format: Format[A] = Format(
    Reads {
      case o: JsObject =>
        (for {
          (key, value) <- o.fields.headOption
          format       <- formatMap.get(key)
        } yield format.reads(value).map(_.asInstanceOf[A]))
          .getOrElse(
            JsError(s"Failure de-serializing ${Json.stringify(o)}")
          )

      case json => JsError(s"Expected json object but got ${json.getClass.getSimpleName}")
    },
    Writes.apply { value =>
      val key = value.getClass.getSimpleName()
      formatMap
        .get(key)
        .map { format =>
          Json.obj(key -> format.writes(value))
        }
        .getOrElse(
          throw new IllegalStateException(
            s"Error while serializing to JSON, unsupported value $value. " +
              s"Please check if all subtypes of your sealed trait has been registered in formats map."
          )
        )
    }
  )

  /** Instance of a typeclass declaration */
  implicit val sealedTraitFormats: SealedTraitFormats[A] = this

}
