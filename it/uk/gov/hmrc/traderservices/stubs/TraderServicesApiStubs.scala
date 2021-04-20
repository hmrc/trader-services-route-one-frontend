package uk.gov.hmrc.traderservices.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.traderservices.support.WireMockSupport

import java.time.LocalDateTime

trait TraderServicesApiStubs {
  me: WireMockSupport =>

  lazy val generatedAt = LocalDateTime.of(2020, 2, 29, 15, 29, 28)

  def validRequestOfCreateCaseApi(): String =
    requestBodyOfCreateCaseApi

  val requestBodyOfCreateCaseApi: String =
    s"""{
       |"entryDetails":{},
       |"questionsAnswers":{},
       |"uploadedFiles":[{}],
       |"eori":"GB123456789012345"
       |}""".stripMargin

  def validRequestOfCreateCaseApiWithoutEori(): String =
    s"""{
       |"entryDetails":{},
       |"questionsAnswers":{},
       |"uploadedFiles":[{}]
       |}""".stripMargin

  def caseApiSuccessResponseBody(caseReferenceNumber: String = "A1234567890"): String =
    s"""{
       |  "correlationId": "",
       |  "result": {
       |      "caseId": "$caseReferenceNumber",
       |      "generatedAt": "${generatedAt.toString}",
       |      "fileTransferResults": [
       |        {"upscanReference":"foo1","success":true,"httpStatus":201,"transferredAt":"2021-04-18T12:07:36"}
       |      ]
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

  def givenCreateCaseApiRequestSucceedsWithoutEori(): StubMapping =
    givenCreateCaseApiStub(200, validRequestOfCreateCaseApiWithoutEori(), caseApiSuccessResponseBody())

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

  def stubForFileDownload(status: Int, bytes: Array[Byte], fileName: String): String = {
    val url = s"$wireMockBaseUrlAsString/bucket/$fileName"

    stubFor(
      get(urlPathEqualTo(s"/bucket/$fileName"))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/octet-stream")
            .withHeader("Content-Length", s"${bytes.length}")
            .withBody(bytes)
        )
    )

    url
  }

  def stubForFileDownloadFailure(status: Int, fileName: String): String = {
    val url = s"$wireMockBaseUrlAsString/bucket/$fileName"

    stubFor(
      get(urlPathEqualTo(s"/bucket/$fileName"))
        .willReturn(
          aResponse()
            .withStatus(status)
        )
    )

    url
  }

  def verifyCreateCaseRequestHappened(times: Int = 1) {
    verify(times, postRequestedFor(urlEqualTo("/create-case")))
  }

}
