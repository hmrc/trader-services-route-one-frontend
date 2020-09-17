package uk.gov.hmrc.traderservices.support

import uk.gov.hmrc.traderservices.wiring.AppConfig

case class TestAppConfig(wireMockBaseUrl: String, wireMockPort: Int) extends AppConfig {

  override val appName: String = "trader-services-frontend"
  override val authBaseUrl: String = wireMockBaseUrl
  override val traderServicesApiBaseUrl: String = wireMockBaseUrl
  override val mongoSessionExpiryTime: Int = 3600
  override val authorisedStrideGroup: String = "TBC"

  override val host: String = wireMockBaseUrl
  override val gtmContainer: Option[String] = Some("main")
  override val contactHost: String = wireMockBaseUrl
  override val contactFormServiceIdentifier: String = "dummy"
  override val exitSurveyUrl: String = wireMockBaseUrl
  override val signOutUrl: String = wireMockBaseUrl
  override val researchBannerUrl: String = wireMockBaseUrl

  override val authorisedServiceName: String = "HMRC-XYZ"
  override val authorisedIdentifierKey: String = "EORINumber"
  override val subscriptionJourneyUrl: String = "/subscription"
}
