/*
 * Copyright 2025 HM Revenue & Customs
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

import uk.gov.hmrc.traderservices.wiring.AppConfig

import javax.inject.Inject
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl.idFunctor
import uk.gov.hmrc.play.bootstrap.binders.{OnlyRelative, RedirectUrl}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

class SignOutController @Inject() (controllerComponents: MessagesControllerComponents, appConfig: AppConfig)
    extends FrontendController(controllerComponents) {

  def signOut(continueUrl: Option[RedirectUrl]): Action[AnyContent] =
    Action { _ =>
      continueUrl match {
        case Some(url) =>
          Redirect(appConfig.signOutUrl, Map("continue" -> Seq(url.get(OnlyRelative).url)))
        case _ =>
          Redirect(appConfig.signOutUrl, Map("continue" -> Seq(appConfig.exitSurveyUrl)))
      }
    }

  def signOutTimeout(): Action[AnyContent] =
    Action { _ =>
      Redirect(
        appConfig.signOutUrl,
        Map("continue" -> Seq(appConfig.baseExternalCallbackUrl + routes.SessionController.showTimeoutPage.url))
      )
    }

  def signOutNoSurvey: Action[AnyContent] =
    Action { _ =>
      Redirect(appConfig.signOutUrl)
    }
}
