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

import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

trait DateTimeFormatHelper {

  val govUkDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy")

  val govUkTimeFormat = DateTimeFormatter.ofPattern("h:mm a")

  def formatDateForDisplay(date: TemporalAccessor): String = govUkDateFormat.format(date)

  def formatTimeForDisplay(time: TemporalAccessor): String = govUkTimeFormat.format(time).toLowerCase()

}
