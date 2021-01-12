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

package uk.gov.hmrc.traderservices.views.components

import play.api.i18n.Messages
import play.api.mvc.Request
import uk.gov.hmrc.govukfrontend.views.viewmodels.footer.FooterItem

object FooterLinks {

  val govukHelp = "https://www.gov.uk/help"
  val termsConditions = "https://www.gov.uk/help/terms-conditions"
  val privacy = "https://www.gov.uk/help/privacy-notice"
  val cookies = "https://www.gov.uk/help/cookies"

  def cookieLink(implicit messages: Messages) =
    FooterItem(
      Some(messages("footer.cookies")),
      Some(cookies)
    )

  def privacyLink(implicit messages: Messages) =
    FooterItem(
      Some(messages("footer.privacy")),
      Some(privacy)
    )

  def termsConditionsLink(implicit messages: Messages) =
    FooterItem(
      Some(messages("footer.termsConditions")),
      Some(termsConditions)
    )

  def govukHelpLink(implicit messages: Messages) =
    FooterItem(
      Some(messages("footer.govukHelp")),
      Some(govukHelp)
    )

  def accessibilityStatement(implicit messages: Messages, request: Request[_]) =
    FooterItem(
      Some(messages("footer.accessibilityStatement")),
      Some(uk.gov.hmrc.traderservices.controllers.routes.AccessibilityStatementController.showPage().url)
    )

  def items(implicit messages: Messages, request: Request[_]) =
    Seq(
      cookieLink,
      accessibilityStatement,
      privacyLink,
      termsConditionsLink,
      govukHelpLink
    )
}
