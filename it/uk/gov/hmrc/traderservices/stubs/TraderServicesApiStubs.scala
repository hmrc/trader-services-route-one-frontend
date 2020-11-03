package uk.gov.hmrc.traderservices.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.traderservices.support.WireMockSupport
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.ZoneOffset

trait TraderServicesApiStubs {
  me: WireMockSupport =>

  def validRequestOfCreateCaseApi(): String = {
    val dateTimeOfArrival = LocalDateTime.now.plusDays(1).truncatedTo(ChronoUnit.MINUTES)
    requestBodyOfCreateCaseApi(dateTimeOfArrival)
  }

  def requestBodyOfCreateCaseApi(dateTimeOfArrival: LocalDateTime): String =
    s"""{"declarationDetails":{"epu":"123","entryNumber":"Z00000Z","entryDate":"2020-09-23"},
       |"questionsAnswers":{"export":{"requestType":"New","routeType":"Route3","hasPriorityGoods":true,"priorityGoods":"ExplosivesOrFireworks","freightType":"Air",
       |"vesselDetails":{"vesselName":"Foo","dateOfArrival":"${DateTimeFormatter.ISO_LOCAL_DATE.format(
      dateTimeOfArrival
        .toLocalDate()
    )}","timeOfArrival":"${DateTimeFormatter.ISO_LOCAL_TIME.format(dateTimeOfArrival.toLocalTime())}"},
       |"contactInfo":{"contactName":"Bob","contactEmail":"name@somewhere.com"}}},
       |"uploadedFiles":[
       |{"downloadUrl":"https://bucketName.s3.eu-west-2.amazonaws.com?1235676","uploadTimestamp":"${DateTimeFormatter.ISO_DATE_TIME
      .format(ZonedDateTime.ofLocal(dateTimeOfArrival, ZoneId.of("GMT"), ZoneOffset.ofHours(0)))}",
       |"checksum":"396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100","fileName":"test.pdf","fileMimeType":"application/pdf"}
       |]}""".stripMargin

  val createCaseApiValidResponseBody: String =
    s"""{
       |  "correlationId": "",
       |  "result": "1234567890"
       |}""".stripMargin

  def givenCreateCaseApiRequestSucceeds(): StubMapping =
    givenCreateCaseApiStub(200, validRequestOfCreateCaseApi(), createCaseApiValidResponseBody)

  def givenAnExternalServiceError(): StubMapping =
    givenCreateCaseApiErrorStub(500, validRequestOfCreateCaseApi())

  def givenCreateCaseApiStub(httpResponseCode: Int, requestBody: String, responseBody: String): StubMapping =
    stubFor(
      post(urlEqualTo(s"/v1/create-case"))
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
      post(urlEqualTo(s"/v1/create-case"))
        .withHeader(HeaderNames.CONTENT_TYPE, containing("application/json"))
        .withRequestBody(equalToJson(requestBody, true, true))
        .willReturn(
          aResponse()
            .withStatus(httpResponseCode)
        )
    )

}
