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

  val host: String
  val appName: String
  val baseCallbackUrl: String
  val authBaseUrl: String
  val traderServicesApiBaseUrl: String
  val upscanInitiateBaseUrl: String

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
  def requestUri(implicit request: RequestHeader): String = SafeRedirectUrl(host + request.uri).encodedUrl
  def feedbackUrl(implicit request: RequestHeader): String =
    s"$contactHost/contact/beta-feedback?service=$contactFormServiceIdentifier&backUrl=$requestUri"

  val signOutUrl: String
  val researchBannerUrl: String

  val fileFormats: AppConfig.FileFormats

}

class AppConfigImpl @Inject() (config: ServicesConfig) extends AppConfig {

  val host: String = config.getString("host")

  val appName: String = config.getString("appName")

  val baseCallbackUrl: String = config.getString("urls.callback")

  val authBaseUrl: String = config.baseUrl("auth")

  val traderServicesApiBaseUrl: String = config.baseUrl("trader-services-api")

  val upscanInitiateBaseUrl: String = config.baseUrl("upscan-initiate")

  val mongoSessionExpiryTime: Int = config.getInt("mongodb.session.expireAfterSeconds")

  val authorisedServiceName: String = config.getString("authorisedServiceName")

  val authorisedIdentifierKey: String = config.getString("authorisedIdentifierKey")

  val authorisedStrideGroup: String = config.getString("authorisedStrideGroup")

  val subscriptionJourneyUrl: String = config.getString("subscriptionJourneyUrl")

  val gtmContainerId: Option[String] = Try(config.getString("gtm.containerId")).toOption

  val contactHost: String = config.getString("contact-frontend.host")
  val contactFormServiceIdentifier: String = config.getString("feedback-frontend.formIdentifier")
  val exitSurveyBaseUrl = config.getString("feedback-frontend.host") + config.getString("feedback-frontend.url")
  val exitSurveyUrl = s"$exitSurveyBaseUrl/$contactFormServiceIdentifier"

  val signOutUrl: String = config.getString("urls.signOut")
  val researchBannerUrl: String = config.getString("urls.researchBanner")

  val fileFormats: AppConfig.FileFormats = AppConfig.FileFormats(
    maxFileSizeMb = config.getInt("file-formats.max-file-size-mb"),
    approvedFileTypes = config.getString("file-formats.approved-file-extensions"),
    approvedFileExtensions = config.getString("file-formats.approved-file-types")
  )

}
