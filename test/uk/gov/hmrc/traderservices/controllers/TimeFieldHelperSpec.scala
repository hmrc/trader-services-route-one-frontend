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

import uk.gov.hmrc.traderservices.controllers.TimeFieldHelper._
import uk.gov.hmrc.play.test.UnitSpec
import java.time.LocalTime
import uk.gov.hmrc.traderservices.support.FormMappingMatchers
import play.api.data.validation.Valid
import play.api.data.validation.ValidationError
import play.api.data.validation.Invalid

class TimeFieldHelperSpec extends UnitSpec with FormMappingMatchers {

  "TimeFieldHelper" should {

    "format time using uk standard" in {
      ukTimeFormatter.format(LocalTime.parse("07:00")) shouldBe "07:00 AM"
      ukTimeFormatter.format(LocalTime.parse("00:00")) shouldBe "12:00 AM"
      ukTimeFormatter.format(LocalTime.parse("23:59")) shouldBe "11:59 PM"
      ukTimeFormatter.format(LocalTime.parse("12:00")) shouldBe "12:00 PM"
    }

    "normalize time fields" in {
      normalizeTimeFields("1", "1", "AM") shouldBe (("01", "01", "AM"))
      normalizeTimeFields("12", "59", "AM") shouldBe (("12", "59", "AM"))
      normalizeTimeFields("a", "b", "PM") shouldBe (("0a", "0b", "PM"))
      normalizeTimeFields("10", "1", "AM") shouldBe (("10", "01", "AM"))
      normalizeTimeFields("1", "11", "AM") shouldBe (("01", "11", "AM"))
    }

    "validate hour of day" in {
      isValidHour("00") shouldBe false
      isValidHour("01") shouldBe true
      isValidHour("12") shouldBe true
      isValidHour("13") shouldBe false
    }

    "validate minutes of hour" in {
      isValidMinutes("00") shouldBe true
      isValidMinutes("59") shouldBe true
      isValidMinutes("60") shouldBe false
    }

    "validate period of day" in {
      isValidPeriod("AM") shouldBe true
      isValidPeriod("PM") shouldBe true
      isValidPeriod("MM") shouldBe false
      isValidPeriod("P") shouldBe false
      isValidPeriod("A") shouldBe false
    }

    "split the time into parts" in {
      splitTime("12:00 AM") shouldBe (("12", "00", "AM"))
      splitTime("12:00 PM") shouldBe (("12", "00", "PM"))
      splitTime(":00 ") shouldBe (("", "00", ""))
      splitTime(":1 ") shouldBe (("", "1", ""))
    }

    "concatenate time parts" in {
      concatTime(("12", "00", "AM")) shouldBe "12:00 AM"
      concatTime(("1", "0", "PM")) shouldBe "1:0 PM"
      concatTime(("", "", "PM")) shouldBe ": PM"
    }

    "validate time fields" in {
      val validate = t => validTimeFields("foo", required = true)(t)
      validate(("12", "00", "AM")) shouldBe Valid
      validate(("12", "00", "PM")) shouldBe Valid
      validate(("", "", "")) shouldBe Invalid(ValidationError("error.foo.required"))
      validate(("", "", "AM")) shouldBe Invalid(
        Seq(ValidationError("error.foo.required-hour"), ValidationError("error.foo.required-minutes"))
      )
      validate(("12", "", "AM")) shouldBe Invalid(
        Seq(ValidationError("error.foo.required-minutes"))
      )
      validate(("", "59", "AM")) shouldBe Invalid(
        Seq(ValidationError("error.foo.required-hour"))
      )
      validate(("", "59", "")) shouldBe Invalid(
        Seq(ValidationError("error.foo.required-hour"), ValidationError("error.foo.required-period"))
      )
      validate(("00", "", "SM")) shouldBe Invalid(
        Seq(
          ValidationError("error.foo.invalid-hour-value"),
          ValidationError("error.foo.required-minutes"),
          ValidationError("error.foo.invalid-period-value")
        )
      )
      validate(("00", "60", "")) shouldBe Invalid(
        Seq(
          ValidationError("error.foo.invalid-hour-value"),
          ValidationError("error.foo.invalid-minutes-value"),
          ValidationError("error.foo.required-period")
        )
      )
      validate(("01", "00", "M")) shouldBe Invalid(
        Seq(
          ValidationError("error.foo.invalid-period-value")
        )
      )
      validate(("0a", "b0", "M")) shouldBe Invalid(
        Seq(
          ValidationError("error.foo.invalid-hour-digits"),
          ValidationError("error.foo.invalid-minutes-digits"),
          ValidationError("error.foo.invalid-period-value")
        )
      )
    }

    "validate optional time fields" in {
      val validate = t => validTimeFields("foo", required = false)(t)
      validate(("12", "00", "AM")) shouldBe Valid
      validate(("12", "00", "PM")) shouldBe Valid
      validate(("", "", "")) shouldBe Valid
      validate(("", "", "AM")) shouldBe Invalid(
        Seq(ValidationError("error.foo.required-hour"), ValidationError("error.foo.required-minutes"))
      )
      validate(("12", "", "AM")) shouldBe Invalid(
        Seq(ValidationError("error.foo.required-minutes"))
      )
      validate(("", "59", "AM")) shouldBe Invalid(
        Seq(ValidationError("error.foo.required-hour"))
      )
      validate(("", "59", "")) shouldBe Invalid(
        Seq(ValidationError("error.foo.required-hour"), ValidationError("error.foo.required-period"))
      )
      validate(("00", "", "SM")) shouldBe Invalid(
        Seq(
          ValidationError("error.foo.invalid-hour-value"),
          ValidationError("error.foo.required-minutes"),
          ValidationError("error.foo.invalid-period-value")
        )
      )
      validate(("00", "60", "")) shouldBe Invalid(
        Seq(
          ValidationError("error.foo.invalid-hour-value"),
          ValidationError("error.foo.invalid-minutes-value"),
          ValidationError("error.foo.required-period")
        )
      )
      validate(("01", "00", "M")) shouldBe Invalid(
        Seq(
          ValidationError("error.foo.invalid-period-value")
        )
      )
      validate(("0a", "b0", "M")) shouldBe Invalid(
        Seq(
          ValidationError("error.foo.invalid-hour-digits"),
          ValidationError("error.foo.invalid-minutes-digits"),
          ValidationError("error.foo.invalid-period-value")
        )
      )
    }

    "validate and map time parts into a time" in {
      timeFieldsMapping("bar").bind(Map("hour" -> "12", "minutes" -> "45", "period" -> "AM")) shouldBe Right(
        LocalTime.parse("00:45")
      )
      timeFieldsMapping("bar").bind(Map("hour" -> "12", "minutes" -> "00", "period" -> "PM")) shouldBe Right(
        LocalTime.parse("12:00")
      )
      timeFieldsMapping("bar").bind(Map("hour" -> "3", "minutes" -> "3", "period" -> "AM")) shouldBe Right(
        LocalTime.parse("03:03")
      )
      timeFieldsMapping("bar").bind(Map("hour" -> "3", "minutes" -> "3", "period" -> "PM")) shouldBe Right(
        LocalTime.parse("15:03")
      )
      timeFieldsMapping("bar").bind(Map()) should haveError("error.bar.required")
      timeFieldsMapping("bar").bind(Map("hour" -> "", "minutes" -> "", "period" -> "")) should haveError(
        "error.bar.required"
      )
      timeFieldsMapping("bar").bind(Map("hour" -> "", "minutes" -> "")) should haveError(
        "error.bar.required"
      )
      timeFieldsMapping("bar").bind(Map("hour" -> "12")) should haveOnlyErrors(
        "error.bar.required-minutes",
        "error.bar.required-period"
      )
      timeFieldsMapping("bar").bind(Map("hour" -> "00", "minutes" -> "60")) should haveOnlyErrors(
        "error.bar.invalid-hour-value",
        "error.bar.invalid-minutes-value",
        "error.bar.required-period"
      )
    }

    "validate and optionally map time parts into a time" in {
      optionalTimeFieldsMapping("bar").bind(Map("hour" -> "12", "minutes" -> "45", "period" -> "AM")) shouldBe Right(
        Some(LocalTime.parse("00:45"))
      )
      optionalTimeFieldsMapping("bar").bind(Map("hour" -> "12", "minutes" -> "00", "period" -> "PM")) shouldBe Right(
        Some(LocalTime.parse("12:00"))
      )
      optionalTimeFieldsMapping("bar").bind(Map("hour" -> "3", "minutes" -> "3", "period" -> "AM")) shouldBe Right(
        Some(LocalTime.parse("03:03"))
      )
      optionalTimeFieldsMapping("bar").bind(Map("hour" -> "3", "minutes" -> "3", "period" -> "PM")) shouldBe Right(
        Some(LocalTime.parse("15:03"))
      )
      optionalTimeFieldsMapping("bar").bind(Map()) shouldBe Right(None)
      optionalTimeFieldsMapping("bar").bind(Map("hour" -> "", "minutes" -> "", "period" -> "")) shouldBe Right(None)
      optionalTimeFieldsMapping("bar").bind(Map("hour" -> "", "minutes" -> "")) shouldBe Right(None)
      optionalTimeFieldsMapping("bar").bind(Map("hour" -> "12")) should haveOnlyErrors(
        "error.bar.required-minutes",
        "error.bar.required-period"
      )
      optionalTimeFieldsMapping("bar").bind(Map("hour" -> "00", "minutes" -> "60")) should haveOnlyErrors(
        "error.bar.invalid-hour-value",
        "error.bar.invalid-minutes-value",
        "error.bar.required-period"
      )
    }
  }

}
