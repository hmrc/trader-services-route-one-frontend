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

import java.net.URL

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.traderservices.wiring.AppConfig
import uk.gov.hmrc.http._

import scala.concurrent.{ExecutionContext, Future}
import akka.actor.ActorSystem
import scala.concurrent.duration._

/** Connects to the backend trader-services-route-one service API.
  */
@Singleton
class TraderServicesApiConnector @Inject() (
  appConfig: AppConfig,
  http: HttpGet with HttpPost,
  metrics: Metrics,
  val actorSystem: ActorSystem
) extends ReadSuccessOrFailure[TraderServicesCaseResponse] with HttpAPIMonitor with Retries {

  val baseUrl: String = appConfig.traderServicesApiBaseUrl
  val createCaseApiPath = appConfig.createCaseApiPath
  val updateCaseApiPath = appConfig.updateCaseApiPath

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def createCase(
    request: TraderServicesCreateCaseRequest
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TraderServicesCaseResponse] =
    retry(1.second, 2.seconds)(TraderServicesCaseResponse.shouldRetry, TraderServicesCaseResponse.errorMessage) {
      monitor(s"ConsumedAPI-trader-services-create-case-api-POST") {
        http
          .POST[TraderServicesCreateCaseRequest, TraderServicesCaseResponse](
            new URL(baseUrl + createCaseApiPath).toExternalForm,
            request
          )
          .recoverWith { case e: Throwable =>
            Future.failed(TraderServicesApiError(e))
          }
      }
    }

  def updateCase(
    request: TraderServicesUpdateCaseRequest
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TraderServicesCaseResponse] =
    monitor(s"ConsumedAPI-trader-services-update-case-api-POST") {
      http
        .POST[TraderServicesUpdateCaseRequest, TraderServicesCaseResponse](
          new URL(baseUrl + updateCaseApiPath).toExternalForm,
          request
        )
        .recoverWith { case e: Throwable =>
          Future.failed(TraderServicesAmendApiError(e))
        }
    }

}

case class TraderServicesApiError(e: Throwable) extends RuntimeException(e)
case class TraderServicesAmendApiError(e: Throwable) extends RuntimeException(e)
