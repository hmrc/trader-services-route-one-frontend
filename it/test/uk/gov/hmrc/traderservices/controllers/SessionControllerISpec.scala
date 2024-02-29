package test.uk.gov.hmrc.traderservices.controllers

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, matching, stubFor}
import play.api.Application
import com.github.tomakehurst.wiremock.client.WireMock._
import test.uk.gov.hmrc.traderservices.support.ServerISpec

class SessionControllerISpec extends SessionControllerISpecSetup() {

  "SessionController" when {

    "GET /timedout" should {
      "display the timed out page" in {
        val result = await(requestWithoutJourneyId("/timedout").get())
        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.timedout.title"))
      }
    }

    "GET /sign-out/timeout" should {
      "redirect to the timed out page" in {
        givenSignOutWithContinueToTimedOut()
        val result = await(requestWithoutJourneyId("/sign-out/timeout").get())
        result.status shouldBe 200
      }
    }

    "GET /sign-out" should {
      "redirect to the feedback survey" in {
        givenSignOutWithContinueToFeedbackSurvey()
        val result = await(requestWithoutJourneyId("/sign-out").get())
        result.status shouldBe 200
      }
    }

    "GET /sign-out-no-survey" should {
      "redirect to the feedback survey" in {
        givenSignOut()
        val result = await(requestWithoutJourneyId("/sign-out-no-survey").get())
        result.status shouldBe 200
      }
    }

    "GET /keep-alive" should {
      "respond with an empty json body" in {
        val result = await(requestWithoutJourneyId("/keep-alive").get())
        result.status shouldBe 200
        result.body shouldBe "{}"
      }
    }
  }
}

trait SessionControllerISpecSetup extends ServerISpec {

  override def uploadMultipleFilesFeature: Boolean = false
  override def requireEnrolmentFeature: Boolean = false
  override def requireOptionalTransportFeature: Boolean = false

  override def fakeApplication: Application = appBuilder.build()

  def givenSignOutWithContinueToTimedOut(): Unit =
    stubFor(
      get(urlPathEqualTo("/dummy-sign-out-url"))
        .withQueryParam(
          "continue",
          matching(s"$wireMockBaseUrlAsString/send-documents-for-customs-check/timedout")
        )
        .willReturn(
          aResponse()
            .withStatus(200)
        )
    )

  def givenSignOutWithContinueToFeedbackSurvey(): Unit =
    stubFor(
      get(urlPathEqualTo("/dummy-sign-out-url"))
        .withQueryParam("continue", matching(appConfig.exitSurveyUrl))
        .willReturn(
          aResponse()
            .withStatus(200)
        )
    )

  def givenSignOut(): Unit =
    stubFor(
      get(urlPathEqualTo("/dummy-sign-out-url"))
        .willReturn(
          aResponse()
            .withStatus(200)
        )
    )

}
