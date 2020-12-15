package uk.gov.hmrc.traderservices.controllers

import play.api.mvc.Result
import play.api.mvc.Results._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.traderservices.support.AppISpec

import scala.concurrent.Future

class AuthActionsISpec extends AuthActionISpecSetup {

  "withAuthorisedWithStrideGroup" should {

    "call body with a valid authProviderId" in {

      givenAuthorisedForStride("TBC", "StrideUserId")

      val result = TestController.withAuthorisedWithStrideGroup("TBC")

      status(result) shouldBe 200
      bodyOf(result) should include("StrideUserId")
    }

    "redirect to log in page when user not enrolled for the service" in {
      givenAuthorisedForStride("TBC", "StrideUserId")

      val result = TestController.withAuthorisedWithStrideGroup("OTHER")
      status(result) shouldBe 303
      redirectLocation(result).get should include("/stride/sign-in")
    }

    "redirect to log in page when user not authenticated" in {
      givenRequestIsNotAuthorised("SessionRecordNotFound")

      val result = TestController.withAuthorisedWithStrideGroup("TBC")
      status(result) shouldBe 303
      redirectLocation(result).get should include("/stride/sign-in")
    }

    "redirect to log in page when user authenticated with different provider" in {
      givenRequestIsNotAuthorised("UnsupportedAuthProvider")

      val result = TestController.withAuthorisedWithStrideGroup("TBC")
      status(result) shouldBe 303
      redirectLocation(result).get should include("/stride/sign-in")
    }

    "redirect to login page when stride group is 'Any'" in {
      givenAuthorisedForStride("ANY", "StrideUserId")
      val result = TestController.withAuthorisedWithStrideGroup("ANY")
      status(result) shouldBe 303
      redirectLocation(result).get should include("/stride/sign-in")
    }

    "redirect to subscription journey when insufficient enrollments" in {
      givenRequestIsNotAuthorised("InsufficientEnrolments")
      val result = TestController.withAuthorisedEnrolment("serviceName", "serviceKey")
      status(result) shouldBe 303
      redirectLocation(result).get should include("/subscription")
    }

    "redirect to government gateway login when authorization fails" in {
      givenRequestIsNotAuthorised("IncorrectCredentialStrength")
      val result = TestController.withAuthorisedEnrolment("serviceName", "serviceKey")
      status(result) shouldBe 303
      redirectLocation(result).get should include("/gg/sign-in")
    }
  }
}

trait AuthActionISpecSetup extends AppISpec {

  override def fakeApplication: Application = appBuilder.build()

  object TestController extends AuthActions {

    override def authConnector: AuthConnector = app.injector.instanceOf[AuthConnector]

    override def config: Configuration = app.injector.instanceOf[Configuration]

    override def env: Environment = app.injector.instanceOf[Environment]

    import scala.concurrent.ExecutionContext.Implicits.global

    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val request = FakeRequest().withSession(SessionKeys.authToken -> "Bearer XYZ")

    def withAuthorisedWithStrideGroup[A](group: String): Result =
      await(super.authorisedWithStrideGroup(group) { pid =>
        Future.successful(Ok(pid))
      })

    def withAuthorisedEnrolment[A](serviceName: String, identifierKey: String): Result =
      await(super.authorisedWithEnrolment(serviceName, identifierKey) { res =>
        Future.successful(Ok(res))
      })
    override def toSubscriptionJourney(continueUrl: String): Result = Redirect("/subscription")
  }

}
