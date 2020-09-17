package uk.gov.hmrc.traderservices.controllers

import scala.concurrent.Future
import play.api.http.Status.OK
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.traderservices.support.NonAuthPageISpec
import uk.gov.hmrc.traderservices.views.html.{AccessibilityStatementView}

class AccessibilityStatementControllerISpec extends NonAuthPageISpec() {

  "AccessibilityStatementController" when {

    "GET /accessibility-statement" should {
      "display the accessibility-statement page" in {

        val controller = app.injector.instanceOf[AccessibilityStatementController]

        implicit val request = FakeRequest()
        val result: Future[Result] = controller.showPage(request)

        status(result) shouldBe OK

        val accessibilityStatementPage = app.injector.instanceOf[AccessibilityStatementView]

        contentAsString(result) shouldBe accessibilityStatementPage().toString
      }
    }
  }
}
