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

package uk.gov.hmrc.traderservices.controllers.proxy

import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.Future
import play.api.mvc.Results
import akka.stream.scaladsl.Flow
import akka.http.scaladsl.model.HttpRequest
import akka.NotUsed
import scala.util.Try
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpResponse
import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpMethods
import play.api.libs.json.Format
import play.api.libs.json.Json
import akka.stream.scaladsl.Source
import scala.util.Success
import play.api.mvc.Result
import scala.util.Failure
import play.api.Configuration
import scala.concurrent.ExecutionContext
import uk.gov.hmrc.traderservices.connectors.FrontendAuthConnector
import play.api.Environment
import uk.gov.hmrc.traderservices.wiring.AppConfig
import uk.gov.hmrc.traderservices.controllers.AuthActions
import uk.gov.hmrc.play.HeaderCarrierConverter
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class FileProxyController @Inject() (
  controllerComponents: MessagesControllerComponents,
  val authConnector: FrontendAuthConnector,
  val env: Environment,
  val appConfig: AppConfig
)(implicit
  val actorSystem: ActorSystem,
  val config: Configuration,
  ec: ExecutionContext
) extends FrontendController(controllerComponents) with AuthActions {

  override implicit protected def hc(implicit request: RequestHeader): HeaderCarrier =
    HeaderCarrierConverter.fromHeadersAndSession(request.headers, None)

  private val connectionPool: Flow[(HttpRequest, FileRequest), (Try[HttpResponse], FileRequest), NotUsed] =
    Http().superPool[FileRequest]()

  private def streamFile(fileRequest: FileRequest) = {
    val httpRequest = HttpRequest(method = HttpMethods.GET, uri = fileRequest.url)
    Source
      .single((httpRequest, fileRequest))
      .via(connectionPool)
      .runFold[Result](Ok) {
        case (_, (Success(httpResponse), fileRequest)) =>
          if (httpResponse.status.isSuccess())
            Results.Ok.streamed(
              content = httpResponse.entity.dataBytes,
              contentLength = httpResponse.entity.contentLengthOption,
              contentType = Some(httpResponse.entity.contentType.toString())
            )
          else
            Status(httpResponse.status.intValue())

        case (_, (Failure(error), fileRequest)) =>
          InternalServerError
      }
  }

  // POST /get-file
  final val getFile: Action[AnyContent] = Action.async { implicit request =>
    authorisedWithEnrolment(appConfig.authorisedServiceName, appConfig.authorisedIdentifierKey) { eori =>
      request.body.asJson.map(_.as[FileRequest]) match {
        case None =>
          Future.successful(BadRequest)

        case Some(fileDownloadRequest) =>
          streamFile(fileDownloadRequest)
      }
    }
  }

  override def toSubscriptionJourney(continueUrl: String): Result = Forbidden
}

case class FileRequest(url: String)

object FileRequest {
  implicit val formats: Format[FileRequest] = Json.format[FileRequest]
}
