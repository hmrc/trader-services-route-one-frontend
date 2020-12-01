package uk.gov.hmrc.traderservices.support

import uk.gov.hmrc.traderservices.wiring.AppConfig

case class TestAppConfig(wireMockBaseUrl: String, wireMockPort: Int) extends AppConfig {

  override val appName: String = "trader-services-frontend"
  override val baseInternalCallbackUrl: String = wireMockBaseUrl
  override val baseExternalCallbackUrl: String = wireMockBaseUrl
  override val authBaseUrl: String = wireMockBaseUrl
  override val traderServicesApiBaseUrl: String = wireMockBaseUrl
  override val upscanInitiateBaseUrl: String = wireMockBaseUrl

  override val createCaseApiPath: String = "/create-case"

  override val mongoSessionExpiryTime: Int = 3600
  override val authorisedStrideGroup: String = "TBC"

  override val gtmContainerId: Option[String] = None
  override val contactHost: String = wireMockBaseUrl
  override val contactFormServiceIdentifier: String = "dummy"
  override val exitSurveyUrl: String = wireMockBaseUrl
  override val signOutUrl: String = wireMockBaseUrl
  override val researchBannerUrl: String = wireMockBaseUrl

  override val authorisedServiceName: String = "HMRC-XYZ"
  override val authorisedIdentifierKey: String = "EORINumber"
  override val subscriptionJourneyUrl: String = "/subscription"

  val fileFormats: AppConfig.FileFormats = AppConfig.FileFormats(10, "", "")
  override val timeout: Int = 10
  override val countdown: Int = 2
  override val timedoutUrl: String = "http://localhost:9379/trader-services/timedout"
}
