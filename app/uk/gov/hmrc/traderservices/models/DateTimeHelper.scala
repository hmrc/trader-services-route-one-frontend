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

package uk.gov.hmrc.traderservices.models

import java.time.ZonedDateTime
import java.time.DayOfWeek
import java.time.ZoneId

object DateTimeHelper {

  val londonTimeZone = ZoneId.of("Europe/London")

  def londonTime = ZonedDateTime.now(londonTimeZone)

  def isWorkingHours(datetime: ZonedDateTime, workingHourStart: Int, workingHourEnd: Int): Boolean =
    datetime.getDayOfWeek() match {
      case DayOfWeek.SATURDAY => false
      case DayOfWeek.SUNDAY   => false
      case _                  => datetime.getHour >= workingHourStart && datetime.getHour() < workingHourEnd
    }

}
