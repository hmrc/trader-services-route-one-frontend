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

package uk.gov.hmrc.traderservices.wiring

import com.google.inject.ImplementedBy
import play.api.i18n.Lang
import play.api.mvc.{Call, RequestHeader}
import uk.gov.hmrc.play.bootstrap.binders.SafeRedirectUrl
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.Inject
import scala.concurrent.duration.Duration

object AppConfig {
  val vesselArrivalConstraintMonths = 6

  case class FileFormats(maxFileSizeMb: Int, approvedFileTypes: String, approvedFileExtensions: String)
}

@ImplementedBy(classOf[AppConfigImpl])
trait AppConfig {

  val appName: String
  val baseInternalCallbackUrl: String
  val baseExternalCallbackUrl: String

  val authBaseUrl: String
  val traderServicesApiBaseUrl: String
  val upscanInitiateBaseUrl: String
  val pdfGeneratorServiceBaseUrl: String

  val createCaseApiPath: String
  val updateCaseApiPath: String

  val mongoSessionExpiration: Duration

  val authorisedServiceName: String
  val authorisedIdentifierKey: String
  val subscriptionJourneyUrl: String
  val govukStartUrl: String

  val languageMap: Map[String, Lang] = Map(
    "english" -> Lang("en"),
    "cymraeg" -> Lang("cy")
  )

  def routeToSwitchLanguage: String => Call =
    (lang: String) => uk.gov.hmrc.traderservices.controllers.routes.LanguageSwitchController.switchToLanguage(lang)

  val contactHost: String
  val contactFormServiceIdentifier: String
  val exitSurveyUrl: String
  def requestUri(implicit request: RequestHeader): String =
    SafeRedirectUrl(baseExternalCallbackUrl + request.uri).encodedUrl

  def betaFeedbackUrl(implicit request: RequestHeader): String =
    s"$contactHost/contact/beta-feedback?service=$contactFormServiceIdentifier&backUrl=$requestUri"

  def reportProblemUrl(implicit request: RequestHeader): String =
    s"$contactHost/contact/problem_reports_nonjs?newTab=true&service=$contactFormServiceIdentifier&backUrl=$requestUri"

  val signOutUrl: String

  val researchBannerUrl: String
  val fileFormats: AppConfig.FileFormats

  val traceFSM: Boolean = false

  val timeout: Int
  val countdown: Int

  val uploadMultipleFilesFeature: Boolean
  val requireEnrolmentFeature: Boolean

  val workingHourStart: Int
  val workingHourEnd: Int
  val requireOptionalTransportFeature: Boolean

}

class AppConfigImpl @Inject() (config: ServicesConfig) extends AppConfig {

  override val appName: String = config.getString("appName")

  override val baseExternalCallbackUrl: String = config.getString("urls.callback.external")
  override val baseInternalCallbackUrl: String = config.getString("urls.callback.internal")

  override val authBaseUrl: String = config.baseUrl("auth")
  override val traderServicesApiBaseUrl: String = config.baseUrl("trader-services-api")
  override val upscanInitiateBaseUrl: String = config.baseUrl("upscan-initiate")
  override val pdfGeneratorServiceBaseUrl: String = config.baseUrl("pdf-generator-service")

  override val createCaseApiPath: String =
    config.getConfString(
      "trader-services-api.paths.create-case",
      throw new IllegalStateException(
        "Missing configuration property microservice.services.trader-services-api.paths.create-case"
      )
    )

  override val updateCaseApiPath: String =
    config.getConfString(
      "trader-services-api.paths.update-case",
      throw new IllegalStateException(
        "Missing configuration property microservice.services.trader-services-api.paths.update-case"
      )
    )

  override val mongoSessionExpiration: Duration = config.getDuration("mongodb.session.expiration")

  override val authorisedServiceName: String = config.getString("authorisedServiceName")
  override val authorisedIdentifierKey: String = config.getString("authorisedIdentifierKey")

  override val subscriptionJourneyUrl: String = config.getString("urls.subscriptionJourney")

  override val contactHost: String = config.getString("contact-frontend.host")
  override val contactFormServiceIdentifier: String = config.getString("feedback-frontend.formIdentifier")

  private val exitSurveyBaseUrl =
    config.getString("feedback-frontend.host") + config.getString("feedback-frontend.url")

  override val exitSurveyUrl = s"$exitSurveyBaseUrl/$contactFormServiceIdentifier"

  override val signOutUrl: String = config.getString("urls.signOut")
  override val researchBannerUrl: String = config.getString("urls.researchBanner")

  override val timeout: Int = config.getInt("session.timeoutSeconds")
  override val countdown: Int = config.getInt("session.countdownInSeconds")

  val fileFormats: AppConfig.FileFormats = AppConfig.FileFormats(
    maxFileSizeMb = config.getInt("file-formats.max-file-size-mb"),
    approvedFileExtensions = config.getString("file-formats.approved-file-extensions"),
    approvedFileTypes = config.getString("file-formats.approved-file-types")
  )

  override val traceFSM: Boolean = config.getBoolean("trace.fsm")

  override val uploadMultipleFilesFeature: Boolean = config.getBoolean("features.uploadMultipleFiles")
  override val requireEnrolmentFeature: Boolean = config.getBoolean("features.requireEnrolment")
  override val requireOptionalTransportFeature: Boolean = config.getBoolean("features.requireOptionalTransport")

  override val workingHourStart: Int = config.getInt("features.workingHours.start")
  override val workingHourEnd: Int = config.getInt("features.workingHours.end")

  override val govukStartUrl: String = config.getString("govuk.start.url")
}
