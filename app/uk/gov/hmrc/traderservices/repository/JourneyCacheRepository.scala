/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.traderservices.repository

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.traderservices.wiring.AppConfig

import scala.concurrent.ExecutionContext
import uk.gov.hmrc.mongo.MongoComponent
import play.api.Configuration
import uk.gov.hmrc.mongo.TimestampSupport

@Singleton
class JourneyCacheRepository @Inject() (
  mongoComponent: MongoComponent,
  configuration: Configuration,
  timestampSupport: TimestampSupport,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends CacheRepository(
      mongoComponent = mongoComponent,
      collectionName = "fsm-journeys",
      ttl = appConfig.mongoSessionExpiration,
      timestampSupport = timestampSupport
    )
