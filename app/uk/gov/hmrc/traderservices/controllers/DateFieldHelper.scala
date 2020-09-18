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

import java.time.LocalDate

import play.api.data.Forms.{mapping, of}
import play.api.data.Mapping
import play.api.data.format.Formats._
import play.api.data.validation.Constraint

import scala.annotation.tailrec
import scala.util.Try

object DateFieldHelper {

  def validateDate(value: String, maxDateIncl: => LocalDate, allowWildcard: Boolean): Boolean = {
    val parts = value.split("-")
    parts.size == 3 && {

      val year = parts(0)
      val month = parts(1)
      val day = parts(2)

      isValidYear(year, maxDateIncl) &&
      isValidMonth(month, day, toInt(year), maxDateIncl, allowWildcard) &&
      isValidDay(day, toInt(month), toInt(year), maxDateIncl, allowWildcard)
    }
  }

  def isValidYear(year: String, maxDateIncl: LocalDate) =
    year.matches("""^\d\d\d\d$""") && toInt(year) >= 1900 &&
      toInt(year) <= maxDateIncl.getYear

  def isValidMonth(month: String, day: String, year: => Int, maxDateIncl: LocalDate, allowWildcard: Boolean) =
    if (allowWildcard && month.contains("X") && day == "XX") month == "XX"
    else
      isInRange(toInt(month), 1, 12) &&
      (year < maxDateIncl.getYear || toInt(month) <= maxDateIncl.getMonthValue)

  def isValidDay(day: String, month: => Int, year: => Int, maxDateIncl: LocalDate, allowWildcard: Boolean) =
    if (allowWildcard && day.contains("X")) day == "XX"
    else
      isValidDayOfTheMonth(toInt(day), month, year) &&
      (year < maxDateIncl.getYear ||
      (year == maxDateIncl.getYear && month < maxDateIncl.getMonthValue) ||
      toInt(day) <= maxDateIncl.getDayOfMonth)

  def isValidDayOfTheMonth(day: Int, month: Int, year: Int): Boolean =
    month match {
      case 4 | 6 | 9 | 11 => isInRange(day, 1, 30)
      case 2              => isInRange(day, 1, if (isLeapYear(year)) 29 else 28)
      case _              => isInRange(day, 1, 31)
    }

  def isLeapYear(year: Int): Boolean =
    (year % 4 == 0) && (year % 100 != 0) || (year % 400 == 0)

  @tailrec
  def toInt(s: String): Int =
    if (s.startsWith("0")) toInt(s.drop(1)) else Try(s.toInt).toOption.getOrElse(-1)

  def isInRange(value: Int, minInc: Int, maxInc: Int): Boolean = value >= minInc && value <= maxInc

  def parseDateIntoFields(date: String): Option[(String, String, String)] = {
    val ydm: Array[String] = date.split('-') ++ Array("", "")
    Some((ydm(0), removeWildcard(ydm(1)), removeWildcard(ydm(2))))
  }

  def removeWildcard(s: String): String = if (s.toUpperCase == "XX") "" else s

  val formatDateFromFields: (String, String, String) => String = {
    case (y, m, d) =>
      if (y.isEmpty && m.isEmpty && d.isEmpty) ""
      else {
        val year = if (y.isEmpty) "" else if (y.length == 2) "19" + y else y
        val month = if (m.isEmpty) "XX" else if (m.length == 1) "0" + m else m
        val day = if (d.isEmpty) "XX" else if (d.length == 1) "0" + d else d
        s"$year-$month-$day"
      }
  }

  val validDobDateFormat: Constraint[String] =
    ValidateHelper
      .validateField("error.dateOfBirth.required", "error.dateOfBirth.invalid-format")(date =>
        validateDate(date, LocalDate.now(), allowWildcard = false)
      )

  def dateFieldsMapping(constraintDate: Constraint[String]): Mapping[String] =
    mapping(
      "year"  -> of[String].transform[String](_.trim, identity),
      "month" -> of[String].transform[String](_.trim.toUpperCase, identity),
      "day"   -> of[String].transform[String](_.trim.toUpperCase, identity)
    )(formatDateFromFields)(parseDateIntoFields).verifying(constraintDate)

}
