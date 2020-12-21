package uk.gov.hmrc.traderservices.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.traderservices.support.WireMockSupport

import java.time.LocalDateTime

trait TraderServicesApiStubs {
  me: WireMockSupport =>

  def validRequestOfCreateCaseApi(): String =
    requestBodyOfCreateCaseApi

  lazy val generatedAt = LocalDateTime.of(2020, 2, 29, 15, 29, 28)

  val requestBodyOfCreateCaseApi: String =
    s"""{
       |"declarationDetails":{},
       |"questionsAnswers":{},
       |"uploadedFiles":[{}],
       |"eori":"GB123456789012345"
       |}""".stripMargin

  def caseApiSuccessResponseBody(caseReferenceNumber: String = "A1234567890"): String =
    s"""{
       |  "correlationId": "",
       |  "result": {
       |      "caseId": "$caseReferenceNumber",
       |      "generatedAt": "${generatedAt.toString}"
       |  } 
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
    givenCreateCaseApiStub(200, validRequestOfCreateCaseApi(), caseApiSuccessResponseBody())

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

  def validRequestOfUpdateCaseApi(
    caseReferenceNumber: String = "A1234567890",
    typeOfAmendment: String = "WriteResponseAndUploadDocuments",
    description: String = "An example description."
  ): String =
    s"""{
       |"caseReferenceNumber":"$caseReferenceNumber",
       |"typeOfAmendment":"$typeOfAmendment",
       |"responseText":"$description",
       |"uploadedFiles":[]
       |}""".stripMargin

  def givenUpdateCaseApiRequestSucceeds(
    caseReferenceNumber: String = "A1234567890",
    typeOfAmendment: String = "WriteResponseAndUploadDocuments",
    description: String = "An example description."
  ): StubMapping =
    givenUpdateCaseApiStub(
      200,
      validRequestOfUpdateCaseApi(caseReferenceNumber, typeOfAmendment, description),
      caseApiSuccessResponseBody(caseReferenceNumber)
    )

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
