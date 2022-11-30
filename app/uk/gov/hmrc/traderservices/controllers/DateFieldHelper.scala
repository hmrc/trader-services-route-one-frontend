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

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.api.data.Forms.{mapping, of, optional}
import play.api.data.Mapping
import play.api.data.format.Formats._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}

import scala.annotation.tailrec
import scala.util.Try

object DateFieldHelper {

  val govukDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy")

  def isValidYear(year: String) = year.matches("""^\d\d\d\d$""")

  def isValidMonth(month: String) = isInRange(toInt(month), 1, 12)

  def isValidDay(day: String, month: String, year: String) = {
    val invalidYear = year.isEmpty || year.exists(c => !c.isDigit)
    val invalidMonth = month.isEmpty || month.exists(c => !c.isDigit)
    isValidDayOfTheMonth(toInt(day), if (invalidMonth) 1 else toInt(month), if (invalidYear) 2000 else toInt(year))
  }

  def isValidDayOfTheMonth(day: Int, month: Int, year: Int): Boolean =
    month match {
      case 4 | 6 | 9 | 11 => isInRange(day, 1, 30)
      case 2              => isInRange(day, 1, if (isLeapYear(year)) 29 else 28)
      case _              => isInRange(day, 1, 31)
    }

  def isLeapYear(year: Int): Boolean =
    (year % 4 == 0) && (year % 100 != 0) || (year % 400 == 0)

  @tailrec
  def dropLeadindZeroes(s: String, minSize: Int): String =
    if (s.length <= minSize) s
    else if (s.startsWith("0")) dropLeadindZeroes(s.drop(1), minSize)
    else s

  def toInt(s: String): Int =
    Try(dropLeadindZeroes(s, 1).toInt).toOption.getOrElse(-1)

  def isInRange(value: Int, minInc: Int, maxInc: Int): Boolean =
    value >= minInc && value <= maxInc

  def concatDate(fields: (String, String, String)): String =
    s"${fields._1}-${fields._2}-${fields._3}"

  def splitDate(date: String): (String, String, String) = {
    val ydm: Array[String] = date.split('-') ++ Array("", "", "")
    (ydm(0), ydm(1), ydm(2))
  }

  val order: Function3[String, String, String, (String, String, String)] = { (d, m, y) =>
    (y, m, d)
  }

  val reorder: Function[(String, String, String), Option[(String, String, String)]] = { case (y, m, d) =>
    Some((d, m, y))
  }

  val normalizeDateFields: ((String, String, String)) => (String, String, String) = { case (y, m, d) =>
    if (y.isEmpty && m.isEmpty && d.isEmpty) (y, m, d)
    else {
      val year =
        if (y.isEmpty) "" else if (y.length == 2) "20" + y else if (y.length > 4) dropLeadindZeroes(y, 4) else y
      val month =
        if (m.isEmpty) "" else if (m.length == 1) "0" + m else if (m.length > 2) dropLeadindZeroes(m, 2) else m
      val day =
        if (d.isEmpty) "" else if (d.length == 1) "0" + d else if (d.length > 2) dropLeadindZeroes(d, 2) else d
      (year, month, day)
    }
  }

  def validDateFields(fieldName: String, required: Boolean): Constraint[(String, String, String)] =
    Constraint[(String, String, String)](s"constraint.$fieldName.date-fields") { case (y, m, d) =>
      if (y.isEmpty && m.isEmpty && d.isEmpty)
        if (required) Invalid(ValidationError(Seq("subfieldFocus=day", s"error.$fieldName.all.required")))
        else Valid
      else if (d.isEmpty) Invalid(ValidationError(Seq("subfieldFocus=day", s"error.$fieldName.day.required")))
      else if (m.isEmpty) Invalid(ValidationError(Seq("subfieldFocus=month", s"error.$fieldName.month.required")))
      else if (y.isEmpty) Invalid(ValidationError(Seq("subfieldFocus=year", s"error.$fieldName.year.required")))
      else if (atLeastTwoOfThree(!isValidDay(d, m, y), !isValidMonth(m), !isValidYear(y)))
        Invalid(
          ValidationError(
            Seq(
              s"subfieldFocus=${if (!isValidDay(d, m, y)) "day" else if (!isValidMonth(m)) "month" else "year"}",
              s"error.$fieldName.all.invalid-value"
            )
          )
        )
      else if (!isValidDay(d, m, y))
        Invalid(ValidationError(Seq("subfieldFocus=day", s"error.$fieldName.day.invalid-value")))
      else if (!isValidMonth(m))
        Invalid(ValidationError(Seq("subfieldFocus=month", s"error.$fieldName.month.invalid-value")))
      else if (!isValidYear(y))
        Invalid(ValidationError(Seq("subfieldFocus=year", s"error.$fieldName.year.invalid-value")))
      else if (atLeastTwoOfThree(!d.forall(_.isDigit), !m.forall(_.isDigit), !y.forall(_.isDigit)))
        Invalid(
          ValidationError(
            Seq(
              s"subfieldFocus=${if (!d.forall(_.isDigit)) "day" else if (!m.forall(_.isDigit)) "month" else "year"}",
              s"error.$fieldName.all.invalid-digits"
            )
          )
        )
      else if (!d.forall(_.isDigit))
        Invalid(ValidationError(Seq("subfieldFocus=day", s"error.$fieldName.day.invalid-digits")))
      else if (!m.forall(_.isDigit))
        Invalid(ValidationError(Seq("subfieldFocus=month", s"error.$fieldName.month.invalid-digits")))
      else if (!y.forall(_.isDigit))
        Invalid(ValidationError(Seq("subfieldFocus=year", s"error.$fieldName.year.invalid-digits")))
      else Valid
    }

  def atLeastTwoOfThree(a: Boolean, b: Boolean, c: Boolean): Boolean = (a && b) || (b && c) || (a && c)

  // empty LocalDate marker value
  val emptyDate: LocalDate = LocalDate.of(0, 1, 13)

  def dateFieldsMapping(fieldName: String): Mapping[LocalDate] =
    mapping(
      "day" -> optional(of[String].transform[String](_.trim, identity))
        .transform(_.getOrElse(""), Option.apply[String]),
      "month" -> optional(of[String].transform[String](_.trim, identity))
        .transform(_.getOrElse(""), Option.apply[String]),
      "year" -> optional(of[String].transform[String](_.trim, identity))
        .transform(_.getOrElse(""), Option.apply[String])
    )(order)(reorder)
      .transform(normalizeDateFields, identity[(String, String, String)])
      .verifying(validDateFields(fieldName, required = true))
      .transform[String](concatDate, splitDate)
      .transform[LocalDate](
        LocalDate.parse(_, DateTimeFormatter.ISO_LOCAL_DATE),
        date => if (date.equals(emptyDate)) "" else DateTimeFormatter.ISO_LOCAL_DATE.format(date)
      )

  def optionalDateFieldsMapping(fieldName: String): Mapping[Option[LocalDate]] =
    mapping(
      "day" -> optional(of[String].transform[String](_.trim, identity))
        .transform(_.getOrElse(""), Option.apply[String]),
      "month" -> optional(of[String].transform[String](_.trim, identity))
        .transform(_.getOrElse(""), Option.apply[String]),
      "year" -> optional(of[String].transform[String](_.trim, identity))
        .transform(_.getOrElse(""), Option.apply[String])
    )(order)(reorder)
      .transform(normalizeDateFields, identity[(String, String, String)])
      .verifying(validDateFields(fieldName, required = false))
      .transform[String](concatDate, splitDate)
      .transform[Option[LocalDate]](
        date => if (date == "--") None else Some(LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)),
        { case Some(date) => DateTimeFormatter.ISO_LOCAL_DATE.format(date); case None => "--" }
      )

  def dateIsBefore(
    fieldName: String,
    errorType: String,
    maxDateGen: LocalDate => LocalDate,
    dateFormatter: DateTimeFormatter = govukDateFormat
  ): Constraint[LocalDate] =
    Constraint[LocalDate](s"constraint.$fieldName.$errorType") { date =>
      val maxDate = maxDateGen(LocalDate.now())
      if (date.isBefore(maxDate.plusDays(1))) Valid
      else
        Invalid(
          ValidationError(Seq("subfieldFocus=day", s"error.$fieldName.$errorType"), dateFormatter.format(maxDate))
        )
    }

  def dateIsAfter(
    fieldName: String,
    errorType: String,
    minDateGen: LocalDate => LocalDate,
    dateFormatter: DateTimeFormatter = govukDateFormat
  ): Constraint[LocalDate] =
    Constraint[LocalDate](s"constraint.$fieldName.$errorType") { date =>
      val minDate = minDateGen(LocalDate.now())
      if (date.isAfter(minDate.minusDays(1))) Valid
      else
        Invalid(
          ValidationError(Seq("subfieldFocus=day", s"error.$fieldName.$errorType"), dateFormatter.format(minDate))
        )
    }

  def dateIsBetween(
    fieldName: String,
    errorType: String,
    minDateGen: LocalDate => LocalDate,
    maxDateGen: LocalDate => LocalDate,
    dateFormatter: DateTimeFormatter = govukDateFormat
  ): Constraint[LocalDate] =
    Constraint[LocalDate](s"constraint.$fieldName.$errorType") { date =>
      val now = LocalDate.now()
      val minDate = minDateGen(now)
      val maxDate = maxDateGen(now)
      if (date.isAfter(minDate.minusDays(1)) && date.isBefore(maxDate.plusDays(1))) Valid
      else
        Invalid(
          ValidationError(
            Seq("subfieldFocus=day", s"error.$fieldName.$errorType"),
            dateFormatter.format(minDate),
            dateFormatter.format(maxDate)
          )
        )
    }

  def getValidDateHint(date: LocalDate): String =
    date.getMonthValue match {
      case month if { month >= 2 && month <= 10 } =>
        LocalDate
          .of(date.getYear, date.minusMonths(1).getMonthValue, 23)
          .format(DateTimeFormatter.ofPattern("dd M yyyy"))
      case month if month >= 11 || month == 1 =>
        LocalDate
          .of(date.minusMonths(1).getYear, 9, 23)
          .format(DateTimeFormatter.ofPattern("dd M yyyy"))
    }
}
