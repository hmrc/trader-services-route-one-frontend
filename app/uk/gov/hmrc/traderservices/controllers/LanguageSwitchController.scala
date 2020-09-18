/*
 * Copyright 2020 HM Revenue & Customs
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

import com.google.inject.Inject
import uk.gov.hmrc.traderservices.wiring.AppConfig
import play.api.i18n.{I18nSupport, Lang, MessagesApi}
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

class LanguageSwitchController @Inject() (
  appConfig: AppConfig,
  override implicit val messagesApi: MessagesApi,
  controllerComponents: MessagesControllerComponents
) extends FrontendController(controllerComponents) with I18nSupport {

  private def fallbackURL: String = routes.TraderServicesFrontendController.showStart.url

  private def languageMap: Map[String, Lang] = appConfig.languageMap

  def switchToLanguage(language: String): Action[AnyContent] =
    Action { implicit request =>
      val lang = languageMap.getOrElse(language, Lang.defaultLang)

      val redirectURL = request.headers.get(REFERER).getOrElse(fallbackURL)
      Redirect(redirectURL).withLang(Lang.apply(lang.code))
    }
}
