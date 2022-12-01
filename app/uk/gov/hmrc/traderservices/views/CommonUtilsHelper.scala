/*
 * Copyright 2022 HM Revenue & Customs
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

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import play.api.i18n.Messages
import java.time.ZoneOffset
import java.time.ZoneId

object CommonUtilsHelper {

  val dateFormat = DateTimeFormatter
    .ofPattern("dd MMMM yyyy")

  val timeFormat = DateTimeFormatter
    .ofPattern("HH:mm")

  implicit class Improvements(s: Int) {
    def format3d = "%03d".format(s)
  }

  implicit class DateTimeUtilities(s: LocalDateTime) {
    def ddMMYYYYAtTimeFormat(implicit messages: Messages) = {
      val dateFormatted = dateFormat.withLocale(messages.lang.locale).format(s)
      val timeFormatted = timeFormat.withLocale(messages.lang.locale).format(s)
      val preposition = messages("site.datetime.preposition")
      s"$dateFormatted $preposition $timeFormatted"
    }

    def asLondonClockTime =
      s.atOffset(ZoneOffset.UTC)
        .atZoneSameInstant(ZoneId.of("Europe/London"))
        .toLocalDateTime()
  }

  /** Mapping, folding and getOrElse on Option[String] for non-empty strings. Commonly used in the Twirl components.
    *
    * @param optString
    */
  implicit class RichOptionString(optString: Option[String]) {
    def mapNonEmpty[T](f: String => T): Option[T] =
      optString.filter(_.nonEmpty).map(f)

    def foldNonEmpty[B](ifEmpty: => B)(f: String => B): B =
      optString.filter(_.nonEmpty).fold(ifEmpty)(f)

    def getNonEmptyOrElse[B >: String](default: => B): B =
      optString.filter(_.nonEmpty).getOrElse(default)
  }
}
