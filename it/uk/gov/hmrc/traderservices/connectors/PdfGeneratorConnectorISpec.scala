package uk.gov.hmrc.traderservices.connectors

import play.api.Application
import uk.gov.hmrc.traderservices.support.AppISpec
import uk.gov.hmrc.http._

import uk.gov.hmrc.traderservices.stubs.PdfGeneratorStubs
import uk.gov.hmrc.traderservices.stubs.DataStreamStubs
import scala.util.Random
import java.util.UUID

class PdfGeneratorConnectorISpec extends PdfGeneratorConnectorISpecSetup {

  "PdfGeneratorConnector" should {
    for (i <- Seq(0, 1, Random.nextInt(1024), 1024, Random.nextInt(1024) * 1024))
      s"convertHtmlToPdf of size $i" in {
        val pdfContent = Array.ofDim[Byte](i)
        Random.nextBytes(pdfContent)
        givenPdfGenerationSucceeds(pdfContent)

        val result =
          await(connector.convertHtmlToPdf(s"""<html>${Random.alphanumeric.take(i)}</html>""", "test.pdf"))

        result.header.headers.get("Content-Disposition") shouldBe Some("attachment; filename=\"test.pdf\"")
        result.body.consumeData.toArray shouldBe pdfContent
      }
  }

}

trait PdfGeneratorConnectorISpecSetup extends AppISpec with PdfGeneratorStubs with DataStreamStubs {

  implicit val hc: HeaderCarrier =
    HeaderCarrier(
      sessionId = Some(SessionId(UUID.randomUUID().toString())),
      requestId = Some(RequestId(UUID.randomUUID().toString()))
    )

  override def fakeApplication: Application = appBuilder.build()

  lazy val connector: PdfGeneratorConnector =
    app.injector.instanceOf[PdfGeneratorConnector]

}
