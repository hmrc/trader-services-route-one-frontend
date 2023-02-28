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

package uk.gov.hmrc.traderservices.services

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Format
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.traderservices.journeys.{CreateCaseJourneyModel, CreateCaseJourneyStateFormats, State}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.traderservices.wiring.AppConfig
import uk.gov.hmrc.traderservices.repository.CacheRepository
import akka.actor.ActorSystem
import com.typesafe.config.Config

trait CreateCaseJourneyService extends SessionStateService {

  val journeyKey = "CreateCaseJourney"

  val model = CreateCaseJourneyModel

  // do not keep errors or transient states in the journey history
  override val breadcrumbsRetentionStrategy: Breadcrumbs => Breadcrumbs =
    _.filterNot(s => s.isInstanceOf[model.IsError] || s.isInstanceOf[model.IsTransient])
      .take(10) // retain last 10 states as a breadcrumbs

  override def updateBreadcrumbs(
    newState: State,
    currentState: State,
    currentBreadcrumbs: Breadcrumbs
  ): Breadcrumbs =
    if (newState.getClass == currentState.getClass)
      currentBreadcrumbs
    else if (currentBreadcrumbs.nonEmpty && currentBreadcrumbs.head.getClass() == newState.getClass())
      currentBreadcrumbs.tail
    else currentState :: breadcrumbsRetentionStrategy(currentBreadcrumbs)
}

trait CreateCaseJourneyServiceWithHeaderCarrier extends CreateCaseJourneyService

@Singleton
case class MongoDBCachedCreateCaseJourneyService @Inject() (
  cacheRepository: CacheRepository,
  config: Config,
  appConfig: AppConfig,
  actorSystem: ActorSystem,
  applicationCrypto: ApplicationCrypto
) extends EncryptedSessionCache[State, HeaderCarrier] with SessionStateService
    with CreateCaseJourneyServiceWithHeaderCarrier {

  override val root = model.root
  override val default: State = root

  override val stateFormats: Format[State] =
    CreateCaseJourneyStateFormats.formats

  final def getJourneyId(hc: HeaderCarrier): Option[String] =
    hc.extraHeaders.find(_._1 == journeyKey).map(_._2)

  final val baseKeyProvider: KeyProvider = KeyProvider(config)

  override final val keyProviderFromContext: HeaderCarrier => KeyProvider =
    hc => KeyProvider(baseKeyProvider, None)

  override val trace: Boolean = appConfig.traceFSM
}
