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

import uk.gov.hmrc.traderservices.controllers.DateFieldHelper._
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.traderservices.support.FormMappingMatchers

class DateFieldHelperSpec extends UnitSpec with FormMappingMatchers {

  val `2020-04-21`: LocalDate = LocalDate.parse("2020-04-21")

  "DateFieldHelper" should {

    "format date from fields" in {
      normalizeDateFields("", "", "") shouldBe (("", "", ""))
      normalizeDateFields("2019", "", "") shouldBe (("2019", "", ""))
      normalizeDateFields("19", "1", "") shouldBe (("2019", "01", ""))
      normalizeDateFields("", "05", "7") shouldBe (("", "05", "07"))
      normalizeDateFields("8", "05", "7") shouldBe (("8", "05", "07"))
      normalizeDateFields("208", "05", "17") shouldBe (("208", "05", "17"))
    }

    "split date into fields" in {
      splitDate("2019-01-17") shouldBe (("2019", "01", "17"))
      splitDate("2019") shouldBe (("2019", "", ""))
      splitDate("2019-01") shouldBe (("2019", "01", ""))
      splitDate("2019-01-XX") shouldBe (("2019", "01", "XX"))
      splitDate("2019-XX-XX") shouldBe (("2019", "XX", "XX"))
      splitDate("2019-XX-31") shouldBe (("2019", "XX", "31"))
      splitDate("foo") shouldBe (("foo", "", ""))
      splitDate("2019-foo-bar") shouldBe (("2019", "foo", "bar"))
      splitDate("") shouldBe (("", "", ""))
    }

    "concat fields into a date" in {
      concatDate(("2019", "01", "17")) shouldBe "2019-01-17"
      concatDate(("2019", "", "")) shouldBe "2019--"
      concatDate(("2019", "01", "")) shouldBe "2019-01-"
      concatDate(("2019", "01", "XX")) shouldBe "2019-01-XX"
      concatDate(("2019", "XX", "XX")) shouldBe "2019-XX-XX"
      concatDate(("2019", "XX", "31")) shouldBe "2019-XX-31"
      concatDate(("foo", "", "")) shouldBe "foo--"
      concatDate(("2019", "foo", "bar")) shouldBe "2019-foo-bar"
      concatDate(("", "", "")) shouldBe "--"
    }

    "validate year" in {
      isValidYear("02020") shouldBe false
      isValidYear("2020") shouldBe true
      isValidYear("202") shouldBe false
      isValidYear("20") shouldBe false
      isValidYear("2") shouldBe false
    }

    "validate month" in {
      isValidMonth("-1") shouldBe false
      isValidMonth("0") shouldBe false
      isValidMonth("1") shouldBe true
      isValidMonth("01") shouldBe true
      isValidMonth("02") shouldBe true
      isValidMonth("03") shouldBe true
      isValidMonth("04") shouldBe true
      isValidMonth("05") shouldBe true
      isValidMonth("06") shouldBe true
      isValidMonth("7") shouldBe true
      isValidMonth("8") shouldBe true
      isValidMonth("9") shouldBe true
      isValidMonth("10") shouldBe true
      isValidMonth("11") shouldBe true
      isValidMonth("12") shouldBe true
      isValidMonth("13") shouldBe false
      isValidMonth("31") shouldBe false
      isValidMonth("00") shouldBe false
      isValidMonth("100") shouldBe false
    }

    "validate day of year" in {
      isValidDay("01", "01", "2020") shouldBe true
      isValidDay("31", "12", "2020") shouldBe true
      isValidDay("32", "12", "2020") shouldBe false
      isValidDay("32", "13", "2020") shouldBe false
      isValidDay("00", "05", "2020") shouldBe false
      isValidDay("31", "04", "2020") shouldBe false
      isValidDay("31", "06", "2020") shouldBe false
      isValidDay("31", "09", "2020") shouldBe false
      isValidDay("31", "11", "2020") shouldBe false
    }
  }

  "validate and map date parts into a date" in {
    dateFieldsMapping("foo").bind(Map("year" -> "2020", "month" -> "12", "day" -> "31")) shouldBe Right(
      LocalDate.parse("2020-12-31")
    )
    dateFieldsMapping("foo").bind(Map("year" -> "", "month" -> "", "day" -> "")) should haveOnlyError(
      "error.foo.required"
    )
    dateFieldsMapping("foo").bind(Map("year" -> "", "day" -> "")) should haveOnlyError(
      "error.foo.required"
    )
    dateFieldsMapping("foo").bind(Map("month" -> "", "day" -> "")) should haveOnlyError(
      "error.foo.required"
    )
    dateFieldsMapping("foo").bind(Map("year" -> "", "month" -> "")) should haveOnlyError(
      "error.foo.required"
    )
    dateFieldsMapping("foo").bind(Map()) should haveOnlyError(
      "error.foo.required"
    )
    dateFieldsMapping("foo").bind(Map("year" -> "", "month" -> "12", "day" -> "31")) should haveOnlyError(
      "error.foo.required-year"
    )
    dateFieldsMapping("foo").bind(Map("month" -> "12", "day" -> "31")) should haveOnlyError(
      "error.foo.required-year"
    )
    dateFieldsMapping("foo").bind(Map("year" -> "2020", "month" -> "", "day" -> "31")) should haveOnlyError(
      "error.foo.required-month"
    )
    dateFieldsMapping("foo").bind(Map("year" -> "2020", "day" -> "31")) should haveOnlyError(
      "error.foo.required-month"
    )
    dateFieldsMapping("foo").bind(Map("year" -> "2020", "month" -> "12", "day" -> "")) should haveOnlyError(
      "error.foo.required-day"
    )
    dateFieldsMapping("foo").bind(Map("year" -> "2020", "month" -> "12")) should haveOnlyError(
      "error.foo.required-day"
    )
    dateFieldsMapping("foo").bind(Map("year" -> "2020a", "month" -> "13", "day" -> "0")) should haveOnlyErrors(
      "error.foo.invalid-year-digits",
      "error.foo.invalid-month-value",
      "error.foo.invalid-day-value"
    )
    dateFieldsMapping("foo").bind(Map("year" -> "2020a", "month" -> "13a", "day" -> "a0")) should haveOnlyErrors(
      "error.foo.invalid-year-digits",
      "error.foo.invalid-month-digits",
      "error.foo.invalid-day-digits"
    )
    dateFieldsMapping("foo").bind(Map("year" -> "0", "month" -> "0", "day" -> "0")) should haveOnlyErrors(
      "error.foo.invalid-year-value",
      "error.foo.invalid-month-value",
      "error.foo.invalid-day-value"
    )
    dateFieldsMapping("foo").bind(Map("year" -> "2000", "month" -> "02", "day" -> "30")) should haveOnlyErrors(
      "error.foo.invalid-day-value"
    )
    dateFieldsMapping("foo").bind(Map("year" -> "2001", "month" -> "02", "day" -> "29")) should haveOnlyErrors(
      "error.foo.invalid-day-value"
    )
  }

  "optionally validate and map date parts into a date" in {
    optionalDateFieldsMapping("bar").bind(Map("year" -> "2020", "month" -> "12", "day" -> "31")) shouldBe Right(
      Some(LocalDate.parse("2020-12-31"))
    )
    optionalDateFieldsMapping("bar").bind(Map("year" -> "", "month" -> "", "day" -> "")) shouldBe Right(None)
    optionalDateFieldsMapping("bar").bind(Map("year" -> "", "day" -> "")) shouldBe Right(None)
    optionalDateFieldsMapping("bar").bind(Map("month" -> "", "day" -> "")) shouldBe Right(None)
    optionalDateFieldsMapping("bar").bind(Map("year" -> "", "month" -> "")) shouldBe Right(None)
    optionalDateFieldsMapping("bar").bind(Map()) shouldBe Right(None)
    optionalDateFieldsMapping("bar").bind(Map("year" -> "", "month" -> "12", "day" -> "31")) should haveOnlyError(
      "error.bar.required-year"
    )
    optionalDateFieldsMapping("bar").bind(Map("month" -> "12", "day" -> "31")) should haveOnlyError(
      "error.bar.required-year"
    )
    optionalDateFieldsMapping("bar").bind(Map("year" -> "2020", "month" -> "", "day" -> "31")) should haveOnlyError(
      "error.bar.required-month"
    )
    optionalDateFieldsMapping("bar").bind(Map("year" -> "2020", "day" -> "31")) should haveOnlyError(
      "error.bar.required-month"
    )
    optionalDateFieldsMapping("bar").bind(Map("year" -> "2020", "month" -> "12", "day" -> "")) should haveOnlyError(
      "error.bar.required-day"
    )
    optionalDateFieldsMapping("bar").bind(Map("year" -> "2020", "month" -> "12")) should haveOnlyError(
      "error.bar.required-day"
    )
    optionalDateFieldsMapping("bar").bind(Map("year" -> "2020a", "month" -> "13", "day" -> "0")) should haveOnlyErrors(
      "error.bar.invalid-year-digits",
      "error.bar.invalid-month-value",
      "error.bar.invalid-day-value"
    )
    optionalDateFieldsMapping("bar").bind(
      Map("year" -> "2020a", "month" -> "13a", "day" -> "a0")
    ) should haveOnlyErrors(
      "error.bar.invalid-year-digits",
      "error.bar.invalid-month-digits",
      "error.bar.invalid-day-digits"
    )
    optionalDateFieldsMapping("bar").bind(Map("year" -> "0", "month" -> "0", "day" -> "0")) should haveOnlyErrors(
      "error.bar.invalid-year-value",
      "error.bar.invalid-month-value",
      "error.bar.invalid-day-value"
    )
    optionalDateFieldsMapping("bar").bind(Map("year" -> "2000", "month" -> "02", "day" -> "30")) should haveOnlyErrors(
      "error.bar.invalid-day-value"
    )
    optionalDateFieldsMapping("bar").bind(Map("year" -> "2001", "month" -> "02", "day" -> "29")) should haveOnlyErrors(
      "error.bar.invalid-day-value"
    )
  }

}
