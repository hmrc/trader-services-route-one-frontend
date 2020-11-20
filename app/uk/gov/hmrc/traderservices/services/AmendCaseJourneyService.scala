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

package uk.gov.hmrc.traderservices.services

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Format
import uk.gov.hmrc.cache.repository.CacheMongoRepository
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.traderservices.journeys.{AmendCaseJourneyModel, AmendCaseJourneyStateFormats}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.fsm.PersistentJourneyService
import uk.gov.hmrc.traderservices.wiring.AppConfig

trait AmendCaseJourneyService[RequestContext] extends PersistentJourneyService[RequestContext] {

  val journeyKey = "AmendCaseJourney"

  override val model = AmendCaseJourneyModel

  // do not keep errors or transient states in the journey history
  override val breadcrumbsRetentionStrategy: Breadcrumbs => Breadcrumbs =
    _.filterNot(s => s.isInstanceOf[model.IsError] || s.isInstanceOf[model.IsTransient])
      .take(2) // retain last 3 states as a breadcrumbs
}

trait AmendCaseJourneyServiceWithHeaderCarrier extends AmendCaseJourneyService[HeaderCarrier]

@Singleton
case class MongoDBCachedAmendCaseJourneyService @Inject() (
  cacheMongoRepository: CacheMongoRepository,
  applicationCrypto: ApplicationCrypto,
  appConfig: AppConfig
) extends MongoDBCachedJourneyService[HeaderCarrier] with AmendCaseJourneyServiceWithHeaderCarrier {

  override val stateFormats: Format[model.State] =
    AmendCaseJourneyStateFormats.formats

  override def getJourneyId(hc: HeaderCarrier): Option[String] =
    hc.extraHeaders.find(_._1 == journeyKey).map(_._2)

  override val traceFSM: Boolean = appConfig.traceFSM
}
