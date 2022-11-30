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

package uk.gov.hmrc.traderservices.connectors

import play.api.libs.json.Reads
import uk.gov.hmrc.http.HttpReads
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.JsValidationException
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsError
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.HttpReads.Implicits._

abstract class ReadSuccessOrFailure[A: Reads](implicit mf: Manifest[A]) {

  implicit val readFromJsonSuccessOrFailure: HttpReads[A] =
    HttpReads[HttpResponse]
      .flatMap { response =>
        val status = response.status
        if ((status >= 200 && status < 300) || (status >= 400 && status < 500))
          implicitly[Reads[A]].reads(response.json) match {
            case JsSuccess(value, path) => HttpReads.pure(value)
            case JsError(errors) =>
              HttpReads.ask.map { case (method, url, response) =>
                throw new JsValidationException(method, url, mf.runtimeClass, errors.toString)
              }
          }
        else
          throw UpstreamErrorResponse("Unexpected response status", status)
      }
}
