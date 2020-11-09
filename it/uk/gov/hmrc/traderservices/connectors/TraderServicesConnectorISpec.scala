package uk.gov.hmrc.traderservices.connectors

import play.api.Application
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.stubs.TraderServicesApiStubs
import uk.gov.hmrc.traderservices.support.AppISpec
import uk.gov.hmrc.http._
import uk.gov.hmrc.traderservices.support.TestData

import scala.concurrent.ExecutionContext.Implicits.global
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class TraderServicesApiConnectorISpec extends TraderServicesApiConnectorISpecSetup {

  "TraderServicesApiConnector" when {

    "createCase" should {

      "return case reference id if success" in {
        givenCreateCaseApiRequestSucceeds()

        val result: TraderServicesCreateCaseResponse =
          await(connector.createCase(request))

        result.result shouldBe Some("A1234567890")
        result.error shouldBe None
      }

      "return error code and message if failure" in {
        givenCreateCaseApiStub(400, validRequestOfCreateCaseApi(), createCaseApiErrorResponseBody("555", "Foo Bar"))

        val result: TraderServicesCreateCaseResponse =
          await(connector.createCase(request))

        result.result shouldBe None
        result.error shouldBe Some(ApiError("555", Some("Foo Bar")))
      }
    }
  }

}

trait TraderServicesApiConnectorISpecSetup extends AppISpec with TraderServicesApiStubs {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def fakeApplication: Application = appBuilder.build()

  lazy val connector: TraderServicesApiConnector =
    app.injector.instanceOf[TraderServicesApiConnector]

  def request = {
    val dateTimeOfArrival = LocalDateTime.now.plusDays(1).truncatedTo(ChronoUnit.MINUTES)
    TraderServicesCreateCaseRequest(
      declarationDetails = TestData.exportDeclarationDetails,
      questionsAnswers = TestData.fullExportQuestions(dateTimeOfArrival),
      uploadedFiles = Seq(
        UploadedFile(
          "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
          ZonedDateTime.ofLocal(dateTimeOfArrival, ZoneId.of("GMT"), ZoneOffset.ofHours(0)),
          "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
          "test.pdf",
          "application/pdf"
        )
      ),
      "GB123456789012345"
    )
  }
}
