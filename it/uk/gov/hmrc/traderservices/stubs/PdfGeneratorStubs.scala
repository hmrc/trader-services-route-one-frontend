package uk.gov.hmrc.traderservices.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.traderservices.support.WireMockSupport

trait PdfGeneratorStubs {
  me: WireMockSupport =>

  def givenPdfGenerationSucceeds(pdfContent: Array[Byte]): StubMapping =
    stubFor(
      post(urlEqualTo(s"/pdf-generator-service/pdf-generator/generate"))
        .withHeader(HeaderNames.CONTENT_TYPE, containing("application/json"))
        .withRequestBody(
          matchingJsonPath("html", containing("<html"))
        )
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/pdf")
            .withBody(pdfContent)
        )
    )

}
