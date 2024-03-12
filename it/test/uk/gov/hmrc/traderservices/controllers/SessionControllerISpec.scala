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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, matching, stubFor}
import play.api.Application
import com.github.tomakehurst.wiremock.client.WireMock._
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
