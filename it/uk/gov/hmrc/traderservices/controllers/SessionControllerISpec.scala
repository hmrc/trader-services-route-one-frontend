package uk.gov.hmrc.traderservices.controllers

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, matching, stubFor}
import play.api.Application
import play.api.libs.ws.WSClient
import uk.gov.hmrc.traderservices.support.ServerISpec

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
      "display the timed out page" in {
        givenTimedOut();
        val result = await(requestWithoutJourneyId("/sign-out/timeout").get())
        result.status shouldBe 200
      }
    }

    "GET /sign-out" should {
      "display the signed out page" in {
        val result = await(requestWithoutJourneyId("/sign-out").get())
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

  override def fakeApplication: Application = appBuilder.build()
  override val timedOutURL = s"http://localhost:$port/trader-services/timedout"

  lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]
  val baseUrl: String = s"http://localhost:$port/trader-services"

  def requestWithoutJourneyId(path: String) =
    wsClient
      .url(s"$baseUrl$path")

  def givenTimedOut(): Unit =
    stubFor(
      get(s"/?continue=http%3A%2F%2Flocalhost%3A$port%2Ftrader-services%2Ftimedout")
        .withQueryParam("continue", matching(s"http://localhost:$port/trader-services/timedout"))
        .willReturn(aResponse().withStatus(200))
    )

}
