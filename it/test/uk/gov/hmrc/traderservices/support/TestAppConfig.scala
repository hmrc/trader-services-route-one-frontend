/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.traderservices.support

import uk.gov.hmrc.traderservices.wiring.AppConfig
import scala.concurrent.duration.Duration
import scala.concurrent.duration._

case class TestAppConfig(
  wireMockBaseUrl: String,
  wireMockPort: Int,
  val uploadMultipleFilesFeature: Boolean,
  val requireEnrolmentFeature: Boolean,
  val requireOptionalTransportFeature: Boolean
) extends AppConfig {

  override val appName: String = "trader-services-frontend"

  override val baseInternalCallbackUrl: String = wireMockBaseUrl
  override val baseExternalCallbackUrl: String = wireMockBaseUrl

  override val authBaseUrl: String = wireMockBaseUrl
  override val traderServicesApiBaseUrl: String = wireMockBaseUrl
  override val upscanInitiateBaseUrl: String = wireMockBaseUrl

  override val createCaseApiPath: String = "/create-case"
  override val updateCaseApiPath: String = "/update-case"

  override val mongoSessionExpiration: Duration = 1.hour

  override val contactHost: String = wireMockBaseUrl
  override val contactFormServiceIdentifier: String = "dummy"

  override val exitSurveyUrl: String = wireMockBaseUrl + "/dummy-survey-url"
  override val signOutUrl: String = wireMockBaseUrl + "/dummy-sign-out-url"
  override val researchBannerUrl: String = wireMockBaseUrl + "dummy-research-banner-url"
  override val subscriptionJourneyUrl: String = wireMockBaseUrl + "/dummy-subscription-url"

  override val authorisedServiceName: String = "HMRC-XYZ"
  override val authorisedIdentifierKey: String = "EORINumber"

  val fileFormats: AppConfig.FileFormats = AppConfig.FileFormats(10, "", "")

  override val timeout: Int = 10
  override val countdown: Int = 2

  override val workingHourEnd: Int = 8
  override val workingHourStart: Int = 16
  override val govukStartUrl: String = wireMockBaseUrl + "/dummy-start-url"
}
