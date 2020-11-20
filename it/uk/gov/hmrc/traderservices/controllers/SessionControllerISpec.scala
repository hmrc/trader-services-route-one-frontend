package uk.gov.hmrc.traderservices.controllers

class SessionControllerISpec extends TraderServicesFrontendISpecSetup() {

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
