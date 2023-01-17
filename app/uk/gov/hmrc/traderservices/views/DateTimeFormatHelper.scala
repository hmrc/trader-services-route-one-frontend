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

package uk.gov.hmrc.traderservices.views

import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import play.api.i18n.Messages

trait DateTimeFormatHelper {

  val govUkDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy")
  val govUkTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

  def formatDateForDisplay(date: TemporalAccessor)(implicit messages: Messages): String =
    govUkDateFormat.withLocale(messages.lang.locale).format(date)

  def formatTimeForDisplay(time: TemporalAccessor)(implicit messages: Messages): String =
    govUkTimeFormat.withLocale(messages.lang.locale).format(time).toLowerCase()

}
