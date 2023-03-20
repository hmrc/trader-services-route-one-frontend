//package uk.gov.hmrc.traderservices.controllers
//
//import play.api.mvc.Result
//import play.api.mvc.Results._
//import play.api.test.FakeRequest
//import play.api.test.Helpers._
//import play.api.{Application, Configuration, Environment}
//import play.mvc.Http.HeaderNames
//import uk.gov.hmrc.auth.core.AuthConnector
//import uk.gov.hmrc.http.SessionKeys
//import uk.gov.hmrc.traderservices.support.AppISpec
//
//import scala.concurrent.Future
//
//class AuthActionsISpec extends AuthActionISpecSetup {
//
//  "authorisedWithEnrolment" should {
//
//    "authorize when enrolment granted" in {
//      givenAuthorisedForEnrolment(Enrolment("serviceName", "serviceKey", "serviceIdentifierFoo"))
//      val result = TestController.testAuthorizedWithEnrolment("serviceName", "serviceKey")
//      status(result) shouldBe 200
//      bodyOf(result) should be("12345-credId,serviceIdentifierFoo")
//    }
//
//    "redirect to subscription journey when insufficient enrollments" in {
//      givenRequestIsNotAuthorised("InsufficientEnrolments")
//      val result = TestController.testAuthorizedWithEnrolment("serviceName", "serviceKey")
//      status(result) shouldBe 303
//      redirectLocation(result).get should include("/subscription")
//    }
//
//    "redirect to government gateway login when authorization fails" in {
//      givenRequestIsNotAuthorised("IncorrectCredentialStrength")
//      val result = TestController.testAuthorizedWithEnrolment("serviceName", "serviceKey")
//      status(result) shouldBe 303
//      redirectLocation(result).get should include(
//        "/bas-gateway/sign-in?continue_url=%2F&origin=trader-services-route-one-frontend"
//      )
//    }
//  }
//
//  "authorisedWithoutEnrolment" should {
//
//    "authorize even when insufficient enrollments" in {
//      givenAuthorisedWithoutEnrolments
//      val result = TestController.testAuhorizedWithoutEnrolment
//      status(result) shouldBe 200
//      bodyOf(result) should be("12345-credId,none")
//    }
//
//    "redirect to government gateway login when authorization fails" in {
//      givenRequestIsNotAuthorised("IncorrectCredentialStrength")
//      val result = TestController.testAuhorizedWithoutEnrolment
//      status(result) shouldBe 303
//      redirectLocation(result).get should include(
//        "/bas-gateway/sign-in?continue_url=%2F&origin=trader-services-route-one-frontend"
//      )
//    }
//  }
//}
//
//trait AuthActionISpecSetup extends AppISpec {
//
//  override def fakeApplication: Application = appBuilder.build()
//
//  object TestController extends AuthActions {
//
//    override def authConnector: AuthConnector = app.injector.instanceOf[AuthConnector]
//
//    override def config: Configuration = app.injector.instanceOf[Configuration]
//
//    override def env: Environment = app.injector.instanceOf[Environment]
//
//    import scala.concurrent.ExecutionContext.Implicits.global
//
//    implicit val request = FakeRequest()
//      .withSession(SessionKeys.authToken -> "Bearer XYZ")
//      .withHeaders(HeaderNames.AUTHORIZATION -> "Bearer XYZ")
//
//    def testAuthorizedWithEnrolment[A](serviceName: String, identifierKey: String): Result =
//      await(super.authorisedWithEnrolment(serviceName, identifierKey) { case (uid, res) =>
//        Future.successful(Ok(uid.getOrElse("none") + "," + res.getOrElse("none")))
//      })
//
//    def testAuhorizedWithoutEnrolment[A]: Result =
//      await(super.authorisedWithoutEnrolment { case (uid, res) =>
//        Future.successful(Ok(uid.getOrElse("none") + "," + res.getOrElse("none")))
//      })
//
//    override def toSubscriptionJourney(continueUrl: String): Result = Redirect("/subscription")
//  }
//
//}
