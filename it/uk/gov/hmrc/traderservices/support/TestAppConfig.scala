//package uk.gov.hmrc.traderservices.support
//
//import uk.gov.hmrc.traderservices.wiring.AppConfig
//import scala.concurrent.duration.Duration
//import scala.concurrent.duration._
//
//case class TestAppConfig(
//  wireMockBaseUrl: String,
//  wireMockPort: Int,
//  val uploadMultipleFilesFeature: Boolean,
//  val requireEnrolmentFeature: Boolean,
//  val requireOptionalTransportFeature: Boolean
//) extends AppConfig {
//
//  override val appName: String = "trader-services-frontend"
//
//  override val baseInternalCallbackUrl: String = wireMockBaseUrl
//  override val baseExternalCallbackUrl: String = wireMockBaseUrl
//
//  override val authBaseUrl: String = wireMockBaseUrl
//  override val traderServicesApiBaseUrl: String = wireMockBaseUrl
//  override val upscanInitiateBaseUrl: String = wireMockBaseUrl
//  override val pdfGeneratorServiceBaseUrl: String = wireMockBaseUrl
//
//  override val createCaseApiPath: String = "/create-case"
//  override val updateCaseApiPath: String = "/update-case"
//
//  override val mongoSessionExpiration: Duration = 1.hour
//
//  override val contactHost: String = wireMockBaseUrl
//  override val contactFormServiceIdentifier: String = "dummy"
//
//  override val exitSurveyUrl: String = wireMockBaseUrl + "/dummy-survey-url"
//  override val signOutUrl: String = wireMockBaseUrl + "/dummy-sign-out-url"
//  override val researchBannerUrl: String = wireMockBaseUrl + "dummy-research-banner-url"
//  override val subscriptionJourneyUrl: String = wireMockBaseUrl + "/dummy-subscription-url"
//
//  override val authorisedServiceName: String = "HMRC-XYZ"
//  override val authorisedIdentifierKey: String = "EORINumber"
//
//  val fileFormats: AppConfig.FileFormats = AppConfig.FileFormats(10, "", "")
//
//  override val timeout: Int = 10
//  override val countdown: Int = 2
//
//  override val workingHourEnd: Int = 8
//  override val workingHourStart: Int = 16
//  override val govukStartUrl: String = wireMockBaseUrl + "/dummy-start-url"
//}
