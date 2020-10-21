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

object Time12FieldHelper {

  type TimeParts = (String, String, String)

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

  val normalizeTimeFields: (String, String, String) => TimeParts = {
    case (h, m, p) =>
      if (h.isEmpty && m.isEmpty && p.isEmpty) (h, m, p)
      else {
        val hour = if (h.isEmpty) "" else if (h.length == 1) "0" + h else h
        val minutes = if (m.isEmpty) "" else if (m.length == 1) "0" + m else m
        (hour, minutes, p)
      }
  }

  def validTimeFields(fieldName: String, required: Boolean): Constraint[TimeParts] =
    Constraint[TimeParts](s"constraint.$fieldName.time-fields") {
      case (h, m, p) if h.isEmpty && m.isEmpty && p.isEmpty =>
        if (required) Invalid(ValidationError(s"error.$fieldName.all.required")) else Valid
      case (h, m, p) if h.isEmpty && m.isEmpty && isValidPeriod(p) =>
        if (required) Invalid(ValidationError(s"error.$fieldName.all.required")) else Valid
      case (h, m, p) =>
        val errors = Seq(
          if (h.isEmpty) Some(ValidationError(s"error.$fieldName.hour.required"))
          else if (!h.forall(_.isDigit)) Some(ValidationError(s"error.$fieldName.hour.invalid-digits"))
          else if (isValidHour(h)) None
          else Some(ValidationError(s"error.$fieldName.hour.invalid-value")),
          if (m.isEmpty) Some(ValidationError(s"error.$fieldName.minutes.required"))
          else if (!m.forall(_.isDigit)) Some(ValidationError(s"error.$fieldName.minutes.invalid-digits"))
          else if (isValidMinutes(m)) None
          else Some(ValidationError(s"error.$fieldName.minutes.invalid-value")),
          if (p.isEmpty) Some(ValidationError(s"error.$fieldName.period.required"))
          else if (isValidPeriod(p)) None
          else Some(ValidationError(s"error.$fieldName.period.invalid-value"))
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

  def concatTime(fields: TimeParts): String =
    s"${fields._1}:${fields._2} ${fields._3}"

  def splitTime(time: String): TimeParts = {
    val ydm: Array[String] = time.split("[: ]") ++ Array("", "")
    (ydm(0), ydm(1), ydm(2))
  }

}
