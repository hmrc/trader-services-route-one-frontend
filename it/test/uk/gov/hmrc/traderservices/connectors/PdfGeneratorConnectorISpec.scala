/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.traderservices.connectors

import play.api.Application
import uk.gov.hmrc.http._
import uk.gov.hmrc.traderservices.stubs.{DataStreamStubs, PdfGeneratorStubs}
import uk.gov.hmrc.traderservices.support.AppISpec

import java.util.UUID
import scala.util.Random

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
