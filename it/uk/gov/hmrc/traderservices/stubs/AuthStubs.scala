package uk.gov.hmrc.traderservices.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.traderservices.support.WireMockSupport

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

  def givenAuthorisedForEnrolment[A](enrolment: Enrolment): AuthStubs = {
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
              |  "retrieve":["authorisedEnrolments"]
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

  def givenAuthorisedForStride(strideGroup: String, strideUserId: String): AuthStubs = {
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .atPriority(1)
        .withRequestBody(
          equalToJson(
            s"""
              |{
              |  "authorise": [
              |    {
              |      "identifiers": [],
              |      "state": "Activated",
              |      "enrolment": "$strideGroup"
              |    },
              |    {
              |      "authProviders": [
              |        "PrivilegedApplication"
              |      ]
              |    }
              |  ],
              |  "retrieve": ["optionalCredentials","allEnrolments"]
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
              |  "optionalCredentials":{
              |    "providerId": "$strideUserId",
              |    "providerType": "PrivilegedApplication"
              |  },
              |  "allEnrolments":[
              |    {"key":"$strideGroup"}
              |  ]
              |}
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

  def verifyAuthoriseAttempt(): Unit =
    verify(1, postRequestedFor(urlEqualTo("/auth/authorise")))

}
