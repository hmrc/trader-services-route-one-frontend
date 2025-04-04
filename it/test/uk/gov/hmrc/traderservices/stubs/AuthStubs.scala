/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.traderservices.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.{HttpHeader, HttpHeaders}
import uk.gov.hmrc.traderservices.support.WireMockSupport
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, SessionId}

trait AuthStubs {
  me: WireMockSupport =>

  def givenRequestIsNotAuthorised(mdtpDetail: String): AuthStubs = {
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(401)
            .withHeader("WWW-Authenticate", s"""MDTP detail="$mdtpDetail"""")
        )
    )
    this
  }

  case class Enrolment(serviceName: String, identifierName: String, identifierValue: String)

  def givenAuthorisedForEnrolment(
    enrolment: Enrolment,
    journeyKey: String = "",
    journeyKeyValue: String = ""
  ): AuthStubs = {
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .atPriority(1)
        .withRequestBody(
          equalToJson(
            s"""
               |{
               |  "authorise": [
               |    { "identifiers":[], "state":"Activated", "enrolment": "${enrolment.serviceName}" },
               |    { "authProviders": ["GovernmentGateway"] }
               |  ],
               |  "retrieve":["optionalCredentials","authorisedEnrolments"]
               |}
           """.stripMargin,
            true,
            true
          )
        )
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""
                         |{
                         |"optionalCredentials": {"providerId": "12345-credId", "providerType": "GovernmentGateway"},
                         |"authorisedEnrolments": [
                         |  { "key":"${enrolment.serviceName}", "identifiers": [
                         |    {"key":"${enrolment.identifierName}", "value": "${enrolment.identifierValue}"}
                         |  ]}
                         |]}
          """.stripMargin)
        )
    )

    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .atPriority(2)
        .willReturn(
          aResponse()
            .withStatus(401)
            .withHeader("WWW-Authenticate", "MDTP detail=\"InsufficientEnrolments\"")
        )
    )
    this
  }

  def givenAuthorisedWithoutEnrolments(journeyKey: String = "", journeyKeyValue: String = ""): AuthStubs = {
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .atPriority(1)
        .withRequestBody(
          equalToJson(
            s"""
               |{
               |  "authorise": [
               |    { "authProviders": ["GovernmentGateway"] }
               |  ],
               |  "retrieve":["optionalCredentials"]
               |}
           """.stripMargin,
            true,
            true
          )
        )
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              s"""{"optionalCredentials": {"providerId": "12345-credId", "providerType": "GovernmentGateway"}, "authorisedEnrolments": []}""".stripMargin
            )
        )
    )

    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .atPriority(2)
        .willReturn(
          aResponse()
            .withStatus(401)
            .withHeader("WWW-Authenticate", "MDTP detail=\"InsufficientEnrolments\"")
        )
    )
    this
  }

  def givenDummySubscriptionUrl: AuthStubs = {
    stubFor(
      get(urlEqualTo("/dummy-subscription-url")).willReturn(
        aResponse().withStatus(200)
      )
    )
    this
  }

  def verifyAuthoriseAttempt(): Unit =
    verify(1, postRequestedFor(urlEqualTo("/auth/authorise")))

  def verifySubscriptionAttempt(): Unit =
    verify(1, getRequestedFor(urlEqualTo("/dummy-subscription-url")))

}
