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

package uk.gov.hmrc.traderservices.views

import play.api.data.Form
import play.api.i18n.Messages

object ViewHelpers {

  /** Key of the prefix to the page's title when the form has errors. */
  val errorBrowserTitlePrefixKey = "error.browser.title.prefix"

  def pageTitle(key: String)(implicit messages: Messages): Option[String] =
    Some(messages(key))

  def pageTitle(key: String, form: Form[_])(implicit messages: Messages): Option[String] =
    if (form.hasErrors) Some(messages(errorBrowserTitlePrefixKey) + messages(key)) else Some(messages(key))

}
