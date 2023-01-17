/*
 * Copyright 2023 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.traderservices.wiring.AppConfig
import akka.actor.ActorSystem
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.Future
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpMethods
import play.api.mvc.Result
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ContentTypes
import play.api.libs.json.Json
import play.api.http.HeaderNames
import akka.http.scaladsl.model.headers.RawHeader
import uk.gov.hmrc.http.{HeaderNames => MdtpHeaderNames}

@Singleton
class PdfGeneratorConnector @Inject() (appConfig: AppConfig, val actorSystem: ActorSystem) extends FileStream {

  final val url = s"${appConfig.pdfGeneratorServiceBaseUrl}/pdf-generator-service/generate"

  private val headerNames = Seq(
    MdtpHeaderNames.xRequestId,
    MdtpHeaderNames.xSessionId,
    MdtpHeaderNames.xForwardedFor,
    MdtpHeaderNames.xRequestChain,
    MdtpHeaderNames.authorisation,
    MdtpHeaderNames.trueClientIp,
    MdtpHeaderNames.googleAnalyticTokenId,
    MdtpHeaderNames.googleAnalyticUserId,
    MdtpHeaderNames.deviceID,
    MdtpHeaderNames.akamaiReputation
  )

  final def convertHtmlToPdf(html: String, fileName: String)(implicit hc: HeaderCarrier): Future[Result] = {
    val httpRequest = HttpRequest(
      method = HttpMethods.POST,
      uri = url,
      entity = HttpEntity(ContentTypes.`application/json`, Json.stringify(Json.obj("html" -> html)))
    ).withHeaders(
      hc.headers(headerNames).map { case (k, v) => RawHeader(k, v) }.toList
    )
    fileStream(
      httpRequest,
      fileName,
      "application/pdf",
      (fileName, fileMimeType) =>
        fileMimeType match {
          case _ =>
            HeaderNames.CONTENT_DISPOSITION -> s"""attachment; filename="$fileName""""
        }
    )
  }

}
