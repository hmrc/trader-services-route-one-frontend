package uk.gov.hmrc.traderservices.stubs

import java.time.{LocalDate, ZoneId}

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.matching.AnythingPattern
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.traderservices.support.WireMockSupport

trait TraderServicesStubs {
  me: WireMockSupport =>

  val queryMonths: Int = 6

  def validRequestOfSomeApi(): String = {
    val date = LocalDate.now(ZoneId.of("UTC"))
    requestBodyOfSomeApi(date.minusMonths(queryMonths).toString, date.toString)
  }

  def requestBodyOfSomeApi(startDate: String, endDate: String): String =
    s"""{
       |  "dateOfBirth": "2001-01-31",
       |  "familyName": "Jane",
       |  "givenName": "Doe",
       |  "nino": "RJ301829A"
       |}""".stripMargin

  val invalidNinoRequestBody: String =
    """{
      |  "dateOfBirth": "2001-01-31",
      |  "familyName": "Jane",
      |  "givenName": "Doe",
      |  "nino": "invalid"
      |}""".stripMargin

  val someApiValidResponseBody: String =
    s"""{
       |  "correlationId": "",
       |  "result": "Dummy"
       |}""".stripMargin

  def givenSomeApiRequestSucceeds(): StubMapping =
    givenSomeApiStub(200, validRequestOfSomeApi(), someApiValidResponseBody)

  def givenSomeApiErrorWhenMissingInputField(): StubMapping = {

    val errorResponseBody: String =
      s"""{
         |  "correlationId": "",
         |  "error": {
         |    "errCode": "ERR_REQUEST_INVALID"
         |  }
         |}""".stripMargin

    givenSomeApiStub(400, validRequestOfSomeApi(), errorResponseBody)
  }

  def givenSomeApiErrorWhenStatusNotFound(): StubMapping = {

    val errorResponseBody: String =
      s"""{
         |  "correlationId": "",
         |  "error": {
         |    "errCode": "ERR_NOT_FOUND"
         |  }
         |}""".stripMargin

    givenSomeApiStub(404, validRequestOfSomeApi(), errorResponseBody)
  }

  def givenStatusCheckErrorWhenConflict(): StubMapping = {

    val errorResponseBody: String =
      s"""{
         |  "correlationId": "",
         |  "error": {
         |    "errCode": "ERR_CONFLICT"
         |  }
         |}""".stripMargin

    givenSomeApiStub(409, validRequestOfSomeApi(), errorResponseBody)
  }

  def givenAnExternalServiceError(): StubMapping =
    givenSomeApiErrorStub(500, validRequestOfSomeApi())

  def givenSomeApiErrorWhenDOBInvalid(): StubMapping = {

    val errorResponseBody: String =
      s"""{
         |  "correlationId": "",
         |  "error": {
         |    "errCode": "ERR_VALIDATION",
         |    "fields": [
         |      {
         |        "code": "ERR_INVALID_DOB",
         |        "name": "dateOfBirth"
         |      }
         |    ]
         |  }
         |}""".stripMargin

    givenSomeApiStub(400, validRequestOfSomeApi(), errorResponseBody)

  }

  def givenSomeApiStub(httpResponseCode: Int, requestBody: String, responseBody: String): StubMapping =
    stubFor(
      post(urlEqualTo(s"/v1/some-api"))
        .withHeader("X-Correlation-Id", new AnythingPattern())
        .withHeader(HeaderNames.CONTENT_TYPE, containing("application/json"))
        .withRequestBody(equalToJson(requestBody, true, true))
        .willReturn(
          aResponse()
            .withStatus(httpResponseCode)
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody)
        )
    )

  def givenSomeApiErrorStub(httpResponseCode: Int, requestBody: String): StubMapping =
    stubFor(
      post(urlEqualTo(s"/v1/some-api"))
        .withHeader("X-Correlation-Id", new AnythingPattern())
        .withHeader(HeaderNames.CONTENT_TYPE, containing("application/json"))
        .withRequestBody(equalToJson(requestBody, true, true))
        .willReturn(
          aResponse()
            .withStatus(httpResponseCode)
        )
    )

}
