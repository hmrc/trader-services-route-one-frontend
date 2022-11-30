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

package uk.gov.hmrc.traderservices.controllers
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import play.api.data.Mapping
import java.time.format.DateTimeFormatter

import play.api.data.Forms.{mapping, of, optional}
import play.api.data.Mapping
import play.api.data.format.Formats._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import scala.annotation.tailrec
import scala.util.Try

object Time24FieldHelper {

  type TimeParts = (String, String)

  val isoTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

  // empty LocalTime marker value, safe because we never allow to set seconds and nanoseconds
  val emptyTime = LocalTime.of(13, 13, 13, 13)

  def timeFieldsMapping(fieldName: String): Mapping[LocalTime] =
    mapping(
      "hour" -> optional(of[String].transform[String](_.trim, identity))
        .transform(_.getOrElse(""), Option.apply[String]),
      "minutes" -> optional(of[String].transform[String](_.trim, identity))
        .transform(_.getOrElse(""), Option.apply[String])
    )(normalizeTimeFields)(a => Option(a))
      .verifying(validTimeFields(fieldName, required = true))
      .transform[String](concatTime, splitTime)
      .transform[LocalTime](
        LocalTime.parse(_, isoTimeFormatter),
        time => if (time.equals(emptyTime)) "" else isoTimeFormatter.format(time)
      )

  def optionalTimeFieldsMapping(fieldName: String): Mapping[Option[LocalTime]] =
    mapping(
      "hour" -> optional(of[String].transform[String](_.trim, identity))
        .transform(_.getOrElse(""), Option.apply[String]),
      "minutes" -> optional(of[String].transform[String](_.trim, identity))
        .transform(_.getOrElse(""), Option.apply[String])
    )(normalizeTimeFields)(a => Option(a))
      .verifying(validTimeFields(fieldName, required = false))
      .transform[String](concatTime, splitTime)
      .transform[Option[LocalTime]](
        time => if (time.startsWith(":")) None else Some(LocalTime.parse(time, isoTimeFormatter)),
        { case Some(time) => isoTimeFormatter.format(time); case None => "" }
      )

  val normalizeTimeFields: (String, String) => TimeParts = { case (h, m) =>
    if (h.isEmpty && m.isEmpty) (h, m)
    else {
      val hour =
        if (h.isEmpty) "" else if (h.length == 1) "0" + h else if (h.length > 2) dropLeadindZeroes(h, 2) else h
      val minutes =
        if (m.isEmpty) "" else if (m.length == 1) "0" + m else if (m.length > 2) dropLeadindZeroes(m, 2) else m
      (hour, minutes)
    }
  }

  def validTimeFields(fieldName: String, required: Boolean): Constraint[TimeParts] =
    Constraint[TimeParts](s"constraint.$fieldName.time-fields") { case (h, m) =>
      if (h.isEmpty && m.isEmpty)
        if (required) Invalid(ValidationError(Seq("subfieldFocus=hour", s"error.$fieldName.all.required")))
        else Valid
      else if (h.isEmpty) Invalid(ValidationError(Seq("subfieldFocus=hour", s"error.$fieldName.hour.required")))
      else if (m.isEmpty) Invalid(ValidationError(Seq("subfieldFocus=minutes", s"error.$fieldName.minutes.required")))
      else if (!h.forall(_.isDigit))
        Invalid(ValidationError(Seq("subfieldFocus=hour", s"error.$fieldName.hour.invalid-digits")))
      else if (!m.forall(_.isDigit))
        Invalid(ValidationError(Seq("subfieldFocus=minutes", s"error.$fieldName.minutes.invalid-digits")))
      else if (!isValidHour(h))
        Invalid(ValidationError(Seq("subfieldFocus=hour", s"error.$fieldName.hour.invalid-value")))
      else if (!isValidMinutes(m))
        Invalid(ValidationError(Seq("subfieldFocus=minutes", s"error.$fieldName.minutes.invalid-value")))
      else Valid
    }

  @tailrec
  def dropLeadindZeroes(s: String, minSize: Int): String =
    if (s.length <= minSize) s
    else if (s.startsWith("0")) dropLeadindZeroes(s.drop(1), minSize)
    else s

  def toInt(s: String): Int =
    Try(dropLeadindZeroes(s, 1).toInt).toOption.getOrElse(-1)

  def isValidHour(hour: String): Boolean = {
    val h = toInt(hour)
    h >= 0 && h < 24
  }

  def isValidMinutes(minutes: String): Boolean = {
    val m = toInt(minutes)
    m >= 0 && m <= 59
  }

  def concatTime(fields: TimeParts): String =
    s"${fields._1}:${fields._2}"

  def splitTime(time: String): TimeParts = {
    val ydm: Array[String] = time.split(":") ++ Array("", "")
    (ydm(0), ydm(1))
  }

}
