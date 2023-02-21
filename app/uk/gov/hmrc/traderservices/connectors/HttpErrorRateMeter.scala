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

package uk.gov.hmrc.traderservices.connectors

import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import com.codahale.metrics.MetricRegistry
import uk.gov.hmrc.http._

trait HttpErrorRateMeter {
  val kenshooRegistry: MetricRegistry
  def meterName[T](serviceName: String, statusCode: Int): String = {
    val group = "http.errors"
    val codeClass = statusCode / 100 match {
      case 4 => "4xx"
      case 5 => "5xx"
      case _ => "other"
    }
    s"$group.$codeClass.$serviceName"
  }

  def countErrors[T](serviceName: String)(future: Future[T])(implicit ec: ExecutionContext): Future[T] =
    future.transform(
      result => {
        result match {
          case Success(response: HttpResponse) if response.status >= 400 =>
            record(meterName(serviceName, response.status))
          case Success(_) =>
          // do nothing
          case Failure(exception: UpstreamErrorResponse) =>
            record(meterName(serviceName, exception.statusCode))
          case Failure(exception: HttpException) =>
            record(meterName(serviceName, exception.responseCode))
          case Failure(exception) =>
            record(meterName(serviceName, 500))
        }
        result
      },
      error => {
        record(meterName(serviceName, 500))
        error
      }
    )

  private def record[T](name: String): Unit = {
    kenshooRegistry.getMeters.getOrDefault(name, kenshooRegistry.meter(name)).mark()
    Logger(getClass).debug(s"kenshoo-event::meter::$name::recorded")
  }
}
