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
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem
import uk.gov.hmrc.traderservices.models.EnumerationFormats

trait RadioItemsHelper {

  def radioItems[A: EnumerationFormats](formName: String, fieldName: String, values: Seq[A], form: Form[_])(implicit
    messages: Messages
  ): Seq[RadioItem] =
    values
      .map(implicitly[EnumerationFormats[A]].keyOf)
      .collect { case Some(k) => k }
      .map { key =>
        RadioItem(
          value = Some(key),
          content = Text(messages(s"form.$formName.$fieldName.$key")),
          checked = form(fieldName).value.contains(key)
        )
      }

}
