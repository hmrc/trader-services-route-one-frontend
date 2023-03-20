//package uk.gov.hmrc.traderservices.controllers
//
//import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlPathEqualTo}
//import play.api.Application
//import uk.gov.hmrc.traderservices.support.ServerISpec
//
//class StartPageControllerISpec extends StartPageControllerISpecSetup {
//
//  "StartPageController" when {
//    "GET /start" should {
//      "display the gov uk start page" in {
//        stubFor(
//          get(urlPathEqualTo("/dummy-start-url"))
//            .willReturn(
//              aResponse()
//                .withStatus(200)
//            )
//        )
//        val result = await(requestWithoutJourneyId("/start").get())
//        result.status shouldBe 200
//      }
//    }
//  }
//}
//
//trait StartPageControllerISpecSetup extends ServerISpec {
//
//  override def uploadMultipleFilesFeature: Boolean = false
//  override def requireEnrolmentFeature: Boolean = false
//  override def requireOptionalTransportFeature: Boolean = false
//
//  override def fakeApplication: Application = appBuilder.build()
//}
