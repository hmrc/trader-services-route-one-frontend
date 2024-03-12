/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.traderservices.controllers

import play.api.mvc.Result
import play.api.mvc.Results._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Configuration, Environment}
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.traderservices.support.AppISpec
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.traderservices.controllers.AuthActions

import scala.concurrent.Future

class AuthActionsISpec extends AuthActionISpecSetup {

  "authorisedWithEnrolment" should {

    "authorize when enrolment granted" in {
      givenAuthorisedForEnrolment(Enrolment("serviceName", "serviceKey", "serviceIdentifierFoo"))

      val result =
        TestController.testAuthorizedWithEnrolment("serviceName", "serviceKey")(Ok("12345-credId,serviceIdentifierFoo"))
      status(result) shouldBe 200
      bodyOf(result) should be("12345-credId,serviceIdentifierFoo")
    }

    "redirect to subscription journey when insufficient enrollments" in {
      givenRequestIsNotAuthorised("InsufficientEnrolments")
      val result = TestController.testAuthorizedWithEnrolment("serviceName", "serviceKey")(Ok)
      status(result) shouldBe 303
      redirectLocation(result).get should include("/subscription")
    }

    "redirect to government gateway login when authorization fails" in {
      givenRequestIsNotAuthorised("IncorrectCredentialStrength")
      await(TestController.testAuthorizedWithEnrolment("serviceName", "serviceKey")(_)).andThen { result =>
        status(result) shouldBe 303
        redirectLocation(result).get should include(
          "/bas-gateway/sign-in?continue_url=%2F&origin=trader-services-route-one-frontend"
        )
      }
    }
  }

  "authorisedWithoutEnrolment" should {

    "authorize even when insufficient enrollments" in {
      givenAuthorisedWithoutEnrolments()
      val result = TestController.testAuhorizedWithoutEnrolment(Ok("12345-credId,none"))

      status(result) shouldBe 200
      bodyOf(result) should be("12345-credId,none")

    }

    "redirect to government gateway login when authorization fails" in {
      givenRequestIsNotAuthorised("IncorrectCredentialStrength")
      val result = TestController.testAuhorizedWithoutEnrolment(Ok)
      status(result) shouldBe 303
      redirectLocation(result).get should include(
        "/bas-gateway/sign-in?continue_url=%2F&origin=trader-services-route-one-frontend"
      )
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

    implicit val request = FakeRequest()
      .withSession(SessionKeys.authToken -> "Bearer XYZ")
      .withHeaders(HeaderNames.AUTHORIZATION -> "Bearer XYZ")

    def testAuthorizedWithEnrolment[A](serviceName: String, identifierKey: String)(body: Future[Result]): Result =
      await(super.authorisedWithEnrolment(serviceName, identifierKey)(body))

    def testAuhorizedWithoutEnrolment[A](body: Future[Result]): Result =
      await(super.authorisedWithoutEnrolment(body))

    override def toSubscriptionJourney(continueUrl: String): Result = Redirect("/subscription")
  }

}
