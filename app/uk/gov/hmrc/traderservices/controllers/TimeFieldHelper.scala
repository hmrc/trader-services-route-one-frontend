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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import play.api.data.Mapping
import java.time.format.DateTimeFormatter

import play.api.data.Forms.{mapping, of, optional}
import play.api.data.Mapping
import play.api.data.format.Formats._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import scala.util.Try

object TimeFieldHelper {

  val ukTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

  def timeFieldsMapping(fieldName: String): Mapping[LocalTime] =
    mapping(
      "hour" -> optional(of[String].transform[String](_.trim, identity))
        .transform(_.getOrElse(""), Option.apply[String]),
      "minutes" -> optional(of[String].transform[String](_.trim, identity))
        .transform(_.getOrElse(""), Option.apply[String]),
      "period" -> optional(of[String].transform[String](_.trim.toUpperCase, identity))
        .transform(_.getOrElse(""), Option.apply[String])
    )(normalizeTimeFields)(a => Option(a))
      .verifying(validTimeFields(fieldName, required = true))
      .transform[String](concatTime, splitTime)
      .transform[LocalTime](
        LocalTime.parse(_, ukTimeFormatter),
        ukTimeFormatter.format
      )

  def optionalTimeFieldsMapping(fieldName: String): Mapping[Option[LocalTime]] =
    mapping(
      "hour" -> optional(of[String].transform[String](_.trim, identity))
        .transform(_.getOrElse(""), Option.apply[String]),
      "minutes" -> optional(of[String].transform[String](_.trim, identity))
        .transform(_.getOrElse(""), Option.apply[String]),
      "period" -> optional(of[String].transform[String](_.trim.toUpperCase, identity))
        .transform(_.getOrElse(""), Option.apply[String])
    )(normalizeTimeFields)(a => Option(a))
      .verifying(validTimeFields(fieldName, required = false))
      .transform[String](concatTime, splitTime)
      .transform[Option[LocalTime]](
        time => if (time.startsWith(":")) None else Some(LocalTime.parse(time, ukTimeFormatter)),
        { case Some(time) => ukTimeFormatter.format(time); case None => "" }
      )

  val normalizeTimeFields: (String, String, String) => (String, String, String) = {
    case (h, m, p) =>
      if (h.isEmpty && m.isEmpty && p.isEmpty) (h, m, p)
      else {
        val hour = if (h.isEmpty) "" else if (h.length == 1) "0" + h else h
        val minutes = if (m.isEmpty) "" else if (m.length == 1) "0" + m else m
        normalizeFromIso(hour, minutes, p)
      }
  }

  def normalizeFromIso(hour: String, minutes: String, period: String): (String, String, String) =
    Try(hour.toInt)
      .map { h =>
        if (h == 0) ("12", minutes, "AM")
        else if (h > 12 && h < 24) (f"${h - 12}%02d", minutes, "PM")
        else (hour, minutes, period)
      }
      .getOrElse((hour, minutes, period))

  def validTimeFields(fieldName: String, required: Boolean): Constraint[(String, String, String)] =
    Constraint[(String, String, String)](s"constraint.$fieldName.time-fields") {
      case (h, m, p) if h.isEmpty && m.isEmpty && p.isEmpty =>
        if (required) Invalid(ValidationError(s"error.$fieldName.required")) else Valid
      case (h, m, p) if h.isEmpty && m.isEmpty && isValidPeriod(p) =>
        if (required) Invalid(ValidationError(s"error.$fieldName.required")) else Valid
      case (h, m, p) =>
        val errors = Seq(
          if (h.isEmpty) Some(ValidationError(s"error.$fieldName.required-hour"))
          else if (!h.forall(_.isDigit)) Some(ValidationError(s"error.$fieldName.invalid-hour-digits"))
          else if (isValidHour(h)) None
          else Some(ValidationError(s"error.$fieldName.invalid-hour-value")),
          if (m.isEmpty) Some(ValidationError(s"error.$fieldName.required-minutes"))
          else if (!m.forall(_.isDigit)) Some(ValidationError(s"error.$fieldName.invalid-minutes-digits"))
          else if (isValidMinutes(m)) None
          else Some(ValidationError(s"error.$fieldName.invalid-minutes-value")),
          if (p.isEmpty) Some(ValidationError(s"error.$fieldName.required-period"))
          else if (isValidPeriod(p)) None
          else Some(ValidationError(s"error.$fieldName.invalid-period-value"))
        ).collect { case Some(e) => e }

        if (errors.isEmpty) Valid else Invalid(errors)
    }

  def isValidHour(hour: String): Boolean = {
    val h = hour.toInt
    h >= 1 && h <= 12
  }

  def isValidMinutes(minutes: String): Boolean = {
    val m = minutes.toInt
    m >= 0 && m <= 59
  }

  def isValidPeriod(period: String): Boolean =
    period == "AM" || period == "PM"

  def concatTime(fields: (String, String, String)): String =
    s"${fields._1}:${fields._2} ${fields._3}"

  def splitTime(time: String): (String, String, String) = {
    val ydm: Array[String] = time.split("[: ]") ++ Array("", "")
    (ydm(0), ydm(1), ydm(2))
  }

}
