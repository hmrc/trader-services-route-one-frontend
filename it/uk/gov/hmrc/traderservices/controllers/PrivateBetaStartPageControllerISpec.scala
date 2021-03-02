package uk.gov.hmrc.traderservices.controllers

import play.api.Application
import uk.gov.hmrc.traderservices.support.ServerISpec

class PrivateBetaStartPageControllerISpec extends PrivateBetaStartPageControllerISpecSetup {

  "PrivateBetaStartPageController" when {
    "GET /start" should {
      "display the private beta start page" in {
        val result = await(requestWithoutJourneyId("/start").get())
        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.beta.title"))
        result.body should include(htmlEscapedMessage("view.beta.heading"))
      }
    }
  }
}

trait PrivateBetaStartPageControllerISpecSetup extends ServerISpec {

  override def fakeApplication: Application = appBuilder.build()

}
