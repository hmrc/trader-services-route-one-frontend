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

package uk.gov.hmrc.traderservices.controllers.internal

import akka.actor.ActorSystem
import com.fasterxml.jackson.core.JsonParseException
import play.api.mvc._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.traderservices.connectors.{FileStream, FrontendAuthConnector}
import uk.gov.hmrc.traderservices.controllers.BaseJourneyController
import uk.gov.hmrc.traderservices.journeys.State
import uk.gov.hmrc.traderservices.models.{Nonce, UpscanNotification}
import uk.gov.hmrc.traderservices.services.{AmendCaseJourneyServiceWithHeaderCarrier, CreateCaseJourneyServiceWithHeaderCarrier}
import uk.gov.hmrc.traderservices.wiring.AppConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UpscanCallBackAmendCaseController @Inject() (
  amendCaseJourneyService: AmendCaseJourneyServiceWithHeaderCarrier,
  appConfig: AppConfig,
  authConnector: FrontendAuthConnector,
  environment: Environment,
  configuration: Configuration,
  controllerComponents: MessagesControllerComponents,
  val actorSystem: ActorSystem
)(implicit ec: ExecutionContext)
    extends BaseJourneyController(
      amendCaseJourneyService,
      controllerComponents,
      appConfig,
      authConnector,
      environment,
      configuration
    ) with FileStream {

  import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel._

  // POST /callback-from-upscan/add/journey/:journeyId/:nonce
  final def callbackFromUpscan(journeyId: String, nonce: String): Action[AnyContent] =
    Action.async { implicit request =>
      whenInSession(journeyId) {
        val journeyKeyHc: HeaderCarrier = hc.withExtraHeaders((amendCaseJourneyService.journeyKey, journeyId))
        Future(request.body.asJson.flatMap(_.asOpt[UpscanNotification]))
          .flatMap {
            case Some(payload) =>
              amendCaseJourneyService
                .updateSessionState(FileUploadTransitions.upscanCallbackArrived(Nonce(nonce))(payload))(
                  journeyKeyHc,
                  ec
                )
                .map(_ => NoContent)

            case None => BadRequest.asFuture
          }
          .recover {
            case e: JsonParseException => BadRequest(e.getMessage())
            case e                     => InternalServerError
          }
      }
    }

  /** Function mapping FSM states to the endpoint calls. This function is invoked internally when the result of an
    * action is to *redirect* to some state.
    */
  override def getCallFor(state: State)(implicit request: Request[_]): Call = ???
}
