package test.uk.gov.hmrc.traderservices.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.{HttpHeader, HttpHeaders}
import test.uk.gov.hmrc.traderservices.support.WireMockSupport
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
