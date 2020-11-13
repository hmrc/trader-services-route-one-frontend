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

        val controller = app.injector.instanceOf[TimedOutController]

        implicit val request = FakeRequest()
        val result: Future[Result] = controller.showPage(request)

        status(result) shouldBe OK

        val timedOutViewPage = app.injector.instanceOf[TimedOutView]

        contentAsString(result) shouldBe timedOutViewPage().toString
      }
    }
  }
}
