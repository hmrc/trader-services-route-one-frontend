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
import org.bson.json.JsonParseException
import play.api.mvc._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.traderservices.models.{Nonce, UpscanNotification}
import uk.gov.hmrc.traderservices.services.AmendCaseJourneyServiceWithHeaderCarrier
import uk.gov.hmrc.traderservices.wiring.AppConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UpscanCallBackAmendCaseController @Inject() (
  amendCaseJourneyService: AmendCaseJourneyServiceWithHeaderCarrier,
  appConfig: AppConfig,
  val controllerComponents: MessagesControllerComponents,
  val actorSystem: ActorSystem
)(implicit ec: ExecutionContext)
    extends FrontendBaseController {

  import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel._

  // POST /callback-from-upscan/add/journey/:journeyId/:nonce
  final def callbackFromUpscan(journeyId: String, nonce: String): Action[AnyContent] =
    Action.async { implicit request =>
      whenInSession(journeyId, appConfig) {
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

            case None => Future.successful(BadRequest)
          }
          .recover {
            case e: JsonParseException => BadRequest(e.getMessage)
            case e                     => InternalServerError
          }
      }
    }

}
