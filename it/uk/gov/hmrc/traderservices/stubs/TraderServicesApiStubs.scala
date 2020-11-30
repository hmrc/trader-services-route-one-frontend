package uk.gov.hmrc.traderservices.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.traderservices.support.WireMockSupport

trait TraderServicesApiStubs {
  me: WireMockSupport =>

  def validRequestOfCreateCaseApi(): String =
    requestBodyOfCreateCaseApi

  val requestBodyOfCreateCaseApi: String =
    s"""{
       |"declarationDetails":{},
       |"questionsAnswers":{},
       |"uploadedFiles":[{}],
       |"eori":"GB123456789012345"
       |}""".stripMargin

  val caseApiSuccessResponseBody: String =
    s"""{
       |  "correlationId": "",
       |  "result": "A1234567890"
       |}""".stripMargin

  def createCaseApiErrorResponseBody(errorCode: String, errorMessage: String): String =
    s"""{
       |  "correlationId": "",
       |  "error": {
       |      "errorCode": "$errorCode",
       |      "errorMessage": "$errorMessage"
       |  } 
       |}""".stripMargin

  def givenCreateCaseApiRequestSucceeds(): StubMapping =
    givenCreateCaseApiStub(200, validRequestOfCreateCaseApi(), caseApiSuccessResponseBody)

  def givenAnExternalServiceError(): StubMapping =
    givenCreateCaseApiErrorStub(500, validRequestOfCreateCaseApi())

  def givenCreateCaseApiStub(httpResponseCode: Int, requestBody: String, responseBody: String): StubMapping =
    stubFor(
      post(urlEqualTo(s"/create-case"))
        .withHeader(HeaderNames.CONTENT_TYPE, containing("application/json"))
        .withRequestBody(equalToJson(requestBody, true, true))
        .willReturn(
          aResponse()
            .withStatus(httpResponseCode)
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody)
        )
    )

  def givenCreateCaseApiErrorStub(httpResponseCode: Int, requestBody: String): StubMapping =
    stubFor(
      post(urlEqualTo(s"/create-case"))
        .withHeader(HeaderNames.CONTENT_TYPE, containing("application/json"))
        .withRequestBody(equalToJson(requestBody, true, true))
        .willReturn(
          aResponse()
            .withStatus(httpResponseCode)
        )
    )

  def validRequestOfUpdateCaseApi(): String =
    requestBodyOfUpdateCaseApi

  val requestBodyOfUpdateCaseApi: String =
    s"""{
       |"caseReferenceNumber":"A1234567890",
       |"typeOfAmendment":"WriteResponseAndUploadDocuments",
       |"responseText":"An example description.",
       |"uploadedFiles":[{}]
       |}""".stripMargin

  def givenUpdateCaseApiRequestSucceeds(): StubMapping =
    givenUpdateCaseApiStub(200, validRequestOfUpdateCaseApi(), caseApiSuccessResponseBody)

  def givenUpdateCaseApiStub(httpResponseCode: Int, requestBody: String, responseBody: String): StubMapping =
    stubFor(
      post(urlEqualTo(s"/update-case"))
        .withHeader(HeaderNames.CONTENT_TYPE, containing("application/json"))
        .withRequestBody(equalToJson(requestBody, true, true))
        .willReturn(
          aResponse()
            .withStatus(httpResponseCode)
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody)
        )
    )

}
