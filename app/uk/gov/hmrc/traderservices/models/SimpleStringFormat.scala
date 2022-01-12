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

import play.api.libs.json.{Format, JsError, JsString, JsSuccess, Reads, Writes}

object SimpleStringFormat {

  def apply[A](fromString: String => A, toString: A => String): Format[A] =
    Format(
      Reads {
        case JsString(value) => JsSuccess(fromString(value))
        case json            => JsError(s"Expected json string but got ${json.getClass.getSimpleName}")
      },
      Writes.apply(entity => JsString(toString(entity)))
    )

}
