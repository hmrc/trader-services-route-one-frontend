package uk.gov.hmrc.traderservices.connectors

import java.time.{LocalDate, ZoneId}

import play.api.Application
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.stubs.TraderServicesStubs
import uk.gov.hmrc.traderservices.support.AppISpec
import uk.gov.hmrc.http._

import scala.concurrent.ExecutionContext.Implicits.global

class TraderServicesConnectorISpec extends TraderServicesConnectorISpecSetup {

  "TraderServicesApiConnector" when {

    "someApi" should {

      "return status when range provided" in {
        givenSomeApiRequestSucceeds()

        val result: TraderServicesApiResponse =
          await(connector.someApi(request))

        result.result shouldBe defined
        result.error shouldBe None
      }

      "return status when no range provided" in {
        givenSomeApiRequestSucceeds()

        val result: TraderServicesApiResponse =
          await(connector.someApi(request))

        result.result shouldBe defined
        result.error shouldBe None
      }

      "return check error when 400 response ERR_REQUEST_INVALID" in {
        givenSomeApiErrorWhenMissingInputField()

        val result: TraderServicesApiResponse =
          await(connector.someApi(request))

        result.result shouldBe None
        result.error shouldBe defined
        result.error.get.errCode shouldBe "ERR_REQUEST_INVALID"
      }

      "return check error when 404 response ERR_NOT_FOUND" in {
        givenSomeApiErrorWhenStatusNotFound()

        val result: TraderServicesApiResponse =
          await(connector.someApi(request))

        result.result shouldBe None
        result.error shouldBe defined
        result.error.get.errCode shouldBe "ERR_NOT_FOUND"
      }

      "return check error when 400 response ERR_VALIDATION" in {
        givenSomeApiErrorWhenDOBInvalid()

        val result: TraderServicesApiResponse =
          await(connector.someApi(request))

        result.result shouldBe None
        result.error shouldBe defined
        result.error.get.errCode shouldBe "ERR_VALIDATION"
      }

      "throw exception if other 4xx response" in {
        givenSomeApiStub(429, validRequestOfSomeApi(), "")

        an[TraderServicesProxyError] shouldBe thrownBy {
          await(connector.someApi(request))
        }
      }

      "throw exception if 5xx response" in {
        givenSomeApiStub(500, validRequestOfSomeApi(), "")

        an[TraderServicesProxyError] shouldBe thrownBy {
          await(connector.someApi(request))
        }
      }
    }
  }

  val errorGenerator: HttpErrorFunctions = new HttpErrorFunctions {}

  "extractResponseBody" should {
    "return the json notFoundMessage if the prefix present" in {
      val responseBody = """{"bar":"foo"}"""
      val errorMessage = errorGenerator.notFoundMessage("GET", "/test/foo/bar", responseBody)
      TraderServicesApiConnector
        .extractResponseBody(errorMessage, "Response body: '") shouldBe responseBody
    }

    "return the json badRequestMessage if the prefix present" in {
      val responseBody = """{"bar":"foo"}"""
      val errorMessage = errorGenerator.badRequestMessage("GET", "/test/foo/bar", responseBody)
      TraderServicesApiConnector
        .extractResponseBody(errorMessage, "Response body '") shouldBe responseBody
    }

    "return the whole message if prefix missing" in {
      val responseBody = """{"bar":"foo"}"""
      val errorMessage = errorGenerator.notFoundMessage("GET", "/test/foo/bar", responseBody)
      TraderServicesApiConnector
        .extractResponseBody(errorMessage, "::: '") shouldBe s"""{"error":{"errCode":"$errorMessage"}}"""
    }
  }

}

trait TraderServicesConnectorISpecSetup extends AppISpec with TraderServicesStubs {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def fakeApplication: Application = appBuilder.build()

  lazy val connector: TraderServicesApiConnector =
    app.injector.instanceOf[TraderServicesApiConnector]

  val request = TraderServicesApiRequest(
    Nino("RJ301829A"),
    "Doe",
    "Jane",
    "2001-01-31"
  )
}
