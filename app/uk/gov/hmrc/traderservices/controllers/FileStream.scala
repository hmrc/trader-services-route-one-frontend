/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.traderservices.controllers

import akka.stream.scaladsl.Flow
import akka.http.scaladsl.model.HttpRequest
import scala.util.Try
import akka.http.scaladsl.model.HttpResponse
import akka.NotUsed
import akka.http.scaladsl.Http
import play.api.mvc.Result
import akka.http.scaladsl.model.HttpMethods
import akka.actor.ActorSystem
import play.api.mvc.Results
import scala.util.Failure
import scala.util.Success
import akka.stream.scaladsl.Source
import play.mvc.Http.HeaderNames
import scala.concurrent.Future
import play.api.Logger

trait FileStream {

  implicit val actorSystem: ActorSystem

  private val connectionPool: Flow[(HttpRequest, String), (Try[HttpResponse], String), NotUsed] =
    Http().superPool[String]()

  final def fileStream(url: String, fileName: String, fileMimeType: String): Future[Result] = {
    val httpRequest = HttpRequest(method = HttpMethods.GET, uri = url)
    Source
      .single((httpRequest, url))
      .via(connectionPool)
      .runFold[Result](Results.Ok) {
        case (_, (Success(httpResponse), fileRequest)) =>
          if (httpResponse.status.isSuccess())
            Results.Ok
              .streamed(
                content = httpResponse.entity.dataBytes,
                contentLength = httpResponse.entity.contentLengthOption,
                contentType = Some(fileMimeType)
              )
              .withHeaders(contentDispositionForMimeType(fileMimeType, fileName))
          else {
            Logger(getClass).error(s"Error status ${httpResponse.status} when accessing uploaded file.")
            Results.InternalServerError
          }

        case (_, (Failure(error), fileRequest)) =>
          Logger(getClass).error(s"Error when accessing uploaded file: ${error.getMessage()}.")
          Results.InternalServerError
      }
  }

  final def contentDispositionForMimeType(mimeType: String, fileName: String): (String, String) =
    mimeType match {
      case _ =>
        HeaderNames.CONTENT_DISPOSITION -> s"""inline; filename="$fileName""""
    }

}
