package uk.gov.hmrc.traderservices.controllers

import play.api.Application
import uk.gov.hmrc.traderservices.support.ServerISpec
import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json.Json
import scala.util.Random
import uk.gov.hmrc.traderservices.stubs.AuthStubs

class FileProxyControllerISpec extends FileStreamingControllerISpecSetup() {

  "FileProxyController" when {
    "POST /get-file" should {
      "stream back requested remote file" in {
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val bytes = Array.ofDim[Byte](1024 * 1024)
        Random.nextBytes(bytes)
        val fileUrl = stubForFileDownload(200, bytes, "test.jpeg")
        val result =
          await(
            wsClient
              .url(s"http://localhost:$port/proxy/get-file")
              .post(Json.parse(s"""{"url":"$fileUrl"}"""))
          )
        result.status shouldBe 200
        result.header("Content-Type") shouldBe Some("application/octet-stream")
        result.header("Content-Length") shouldBe Some(s"${bytes.length}")
        result.bodyAsBytes.toArray[Byte] shouldBe bytes
        verifyAuthoriseAttempt()
      }

      "fail when not authorised" in {
        givenRequestIsNotAuthorised("SessionRecordNotFound")
        val bytes = Array.ofDim[Byte](16)
        Random.nextBytes(bytes)
        val fileUrl = stubForFileDownload(200, bytes, "test.jpeg")
        val result =
          await(
            wsClient
              .url(s"http://localhost:$port/proxy/get-file")
              .post(Json.parse(s"""{"url":"$fileUrl"}"""))
          )
        result.status shouldBe 404
      }
    }
  }
}

trait FileStreamingControllerISpecSetup extends ServerISpec with AuthStubs {

  override def fakeApplication: Application = appBuilder.build()

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

}
