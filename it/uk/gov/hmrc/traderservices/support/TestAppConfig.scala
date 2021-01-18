package uk.gov.hmrc.traderservices.support

import uk.gov.hmrc.traderservices.wiring.AppConfig

case class TestAppConfig(wireMockBaseUrl: String, wireMockPort: Int, val uploadMultipleFilesFeature: Boolean)
    extends AppConfig {

  override val appName: String = "trader-services-frontend"
  override val baseInternalCallbackUrl: String = s"http://baseInternalCallbackUrl"
  override val baseExternalCallbackUrl: String = s"http://baseExternalCallbackUrl"
  override val authBaseUrl: String = wireMockBaseUrl
  override val traderServicesApiBaseUrl: String = wireMockBaseUrl
  override val upscanInitiateBaseUrl: String = wireMockBaseUrl

  override val createCaseApiPath: String = "/create-case"
  override val updateCaseApiPath: String = "/update-case"

  override val mongoSessionExpiryTime: Int = 3600
  override val authorisedStrideGroup: String = "TBC"

  override val gtmContainerId: Option[String] = None
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
}
