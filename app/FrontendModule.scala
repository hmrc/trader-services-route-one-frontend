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

import com.google.inject.AbstractModule
import com.google.inject.{Inject, Singleton}
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.cache.repository.CacheMongoRepository
import uk.gov.hmrc.traderservices.connectors.FrontendAuthConnector
import uk.gov.hmrc.traderservices.services._
import uk.gov.hmrc.traderservices.repository.JourneyCacheRepository
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import scala.util.matching.Regex
import com.google.inject.name.Named
import uk.gov.hmrc.play.audit.http.HttpAuditing
import play.api.libs.ws.WSClient
import akka.actor.ActorSystem
import com.typesafe.config.Config
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.play.http.ws.WSHttp

class FrontendModule(val environment: Environment, val configuration: Configuration) extends AbstractModule {

  override def configure(): Unit = {

    val appName = "trader-services-route-one-frontend"

    Logger(getClass).info(s"Starting microservice : $appName : in mode : ${environment.mode}")

    bind(classOf[HttpGet]).to(classOf[CustomHttpClient])
    bind(classOf[HttpPost]).to(classOf[CustomHttpClient])

    bind(classOf[AuthConnector]).to(classOf[FrontendAuthConnector])

    bind(classOf[CacheMongoRepository]).to(classOf[JourneyCacheRepository])

    bind(classOf[CreateCaseJourneyServiceWithHeaderCarrier])
      .to(classOf[MongoDBCachedCreateCaseJourneyService])

    bind(classOf[AmendCaseJourneyServiceWithHeaderCarrier])
      .to(classOf[MongoDBCachedAmendCaseJourneyService])
  }
}

@Singleton
class CustomHttpAuditing @Inject() (
  val auditConnector: AuditConnector,
  @Named("appName") val appName: String
) extends HttpAuditing {

  override val auditDisabledForPattern: Regex =
    """.*?\/auth\/authorise$""".r

}

@Singleton
class CustomHttpClient @Inject() (
  config: Configuration,
  val httpAuditing: CustomHttpAuditing,
  override val wsClient: WSClient,
  override protected val actorSystem: ActorSystem
) extends uk.gov.hmrc.http.HttpClient with WSHttp {

  override lazy val configuration: Option[Config] = Option(config.underlying)

  override val hooks: Seq[HttpHook] = Seq(httpAuditing.AuditingHook)
}
