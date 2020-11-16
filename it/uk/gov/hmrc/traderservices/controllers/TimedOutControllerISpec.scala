package uk.gov.hmrc.traderservices.controllers

import play.api.http.Status.OK
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.traderservices.support.NonAuthPageISpec
import uk.gov.hmrc.traderservices.views.html.{AccessibilityStatementView, TimedOutView}

import scala.concurrent.Future

class TimedOutControllerISpec extends NonAuthPageISpec() {

  "AccessibilityStatementController" when {

    "GET /timedout" should {
      "display the timed out page" in {

        val result = await(requestWithoutJourneyId("/timedout").get())
        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.timedout.title"))
      }
    }
  }

  def requestWithoutJourneyId(path: String) =
    ws.url(s"$baseUrl$path")
}
