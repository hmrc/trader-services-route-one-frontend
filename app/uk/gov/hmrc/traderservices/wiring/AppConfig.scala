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

package uk.gov.hmrc.traderservices.wiring

import com.google.inject.ImplementedBy
import javax.inject.Inject
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.binders.SafeRedirectUrl
import play.api.i18n.Lang
import play.api.mvc.{Call, RequestHeader}
import scala.util.Try

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

  val createCaseApiPath: String
  val updateCaseApiPath: String

  val mongoSessionExpiryTime: Int
  val authorisedServiceName: String
  val authorisedIdentifierKey: String
  val authorisedStrideGroup: String
  val subscriptionJourneyUrl: String

  val languageMap: Map[String, Lang] = Map(
    "english" -> Lang("en"),
    "cymraeg" -> Lang("cy")
  )

  val gtmContainerId: Option[String]

  def routeToSwitchLanguage: String => Call =
    (lang: String) => uk.gov.hmrc.traderservices.controllers.routes.LanguageSwitchController.switchToLanguage(lang)

  val contactHost: String
  val contactFormServiceIdentifier: String
  val exitSurveyUrl: String
  def requestUri(implicit request: RequestHeader): String =
    SafeRedirectUrl(baseExternalCallbackUrl + request.uri).encodedUrl

  def feedbackUrl(implicit request: RequestHeader): String =
    s"$contactHost/contact/beta-feedback?service=$contactFormServiceIdentifier&backUrl=$requestUri"

  val signOutUrl: String

  val researchBannerUrl: String
  val fileFormats: AppConfig.FileFormats

  val traceFSM: Boolean = false

  val timeout: Int
  val countdown: Int

  val uploadMultipleFilesFeature: Boolean
  val requireEnrolmentFeature: Boolean
}

class AppConfigImpl @Inject() (config: ServicesConfig) extends AppConfig {

  override val appName: String = config.getString("appName")

  override val baseExternalCallbackUrl: String = config.getString("urls.callback.external")
  override val baseInternalCallbackUrl: String = config.getString("urls.callback.internal")
  override val authBaseUrl: String = config.baseUrl("auth")
  override val traderServicesApiBaseUrl: String = config.baseUrl("trader-services-api")
  override val upscanInitiateBaseUrl: String = config.baseUrl("upscan-initiate")

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

  override val mongoSessionExpiryTime: Int = config.getInt("mongodb.session.expireAfterSeconds")

  override val authorisedServiceName: String = config.getString("authorisedServiceName")
  override val authorisedIdentifierKey: String = config.getString("authorisedIdentifierKey")

  override val authorisedStrideGroup: String = config.getString("authorisedStrideGroup")

  override val subscriptionJourneyUrl: String = config.getString("subscriptionJourneyUrl")

  override val gtmContainerId: Option[String] = Try(config.getString("gtm.containerId")).toOption

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
}
