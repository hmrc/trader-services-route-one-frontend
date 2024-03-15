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

import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.http.scaladsl.model.HttpRequest
import scala.util.Try
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.NotUsed
import org.apache.pekko.http.scaladsl.Http
import play.api.mvc.Result
import org.apache.pekko.http.scaladsl.model.HttpMethods
import org.apache.pekko.actor.ActorSystem
import play.api.mvc.Results
import scala.util.Failure
import scala.util.Success
import org.apache.pekko.stream.scaladsl.Source
import scala.concurrent.Future

trait FileStream {

  implicit val actorSystem: ActorSystem

  private val connectionPool: Flow[(HttpRequest, String), (Try[HttpResponse], String), NotUsed] =
    Http().superPool[String]()

  final def getFileStream(
    url: String,
    fileName: String,
    fileMimeType: String,
    contentDispositionForMimeType: (String, String) => (String, String)
  ): Future[Result] = {
    val httpRequest = HttpRequest(method = HttpMethods.GET, uri = url)
    fileStream(httpRequest, fileName, fileMimeType, contentDispositionForMimeType)
  }

  final def fileStream(
    httpRequest: HttpRequest,
    fileName: String,
    fileMimeType: String,
    contentDispositionForMimeType: (String, String) => (String, String)
  ): Future[Result] =
    Source
      .single((httpRequest, httpRequest.uri.toString()))
      .via(connectionPool)
      .runFold[Result](Results.Ok) {
        case (_, (Success(httpResponse), url)) =>
          if (httpResponse.status.isSuccess())
            Results.Ok
              .streamed(
                content = httpResponse.entity.dataBytes,
                contentLength = httpResponse.entity.contentLengthOption,
                contentType = Some(fileMimeType)
              )
              .withHeaders(contentDispositionForMimeType(fileName, fileMimeType))
          else
            throw new Exception(s"Error status ${httpResponse.status} when accessing file stream url")

        case (_, (Failure(error), url)) =>
          throw new Exception(s"Error when accessing file stream url: ${error.getMessage()}.")
      }

}
