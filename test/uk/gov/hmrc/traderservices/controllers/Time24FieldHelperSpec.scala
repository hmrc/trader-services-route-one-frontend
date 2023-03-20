/*
 * Copyright 2023 HM Revenue & Customs
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

///*
// * Copyright 2023 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.traderservices.controllers
//
//import java.time.LocalTime
//
//import uk.gov.hmrc.traderservices.controllers.Time24FieldHelper._
//import uk.gov.hmrc.traderservices.support.UnitSpec
//import java.time.LocalTime
//import uk.gov.hmrc.traderservices.support.FormMappingMatchers
//import play.api.data.validation.Valid
//import play.api.data.validation.ValidationError
//import play.api.data.validation.Invalid
//
//class Time24FieldHelperSpec extends UnitSpec with FormMappingMatchers {
//
//  "Time24FieldHelper" should {
//
//    "format time using iso standard" in {
//      isoTimeFormatter.format(LocalTime.parse("07:01")) shouldBe "07:01"
//      isoTimeFormatter.format(LocalTime.parse("12:00")) shouldBe "12:00"
//      isoTimeFormatter.format(LocalTime.parse("23:59")) shouldBe "23:59"
//      isoTimeFormatter.format(LocalTime.parse("12:00")) shouldBe "12:00"
//      isoTimeFormatter.format(LocalTime.parse("00:00")) shouldBe "00:00"
//    }
//
//    "normalize time fields" in {
//      normalizeTimeFields("1", "1") shouldBe (("01", "01"))
//      normalizeTimeFields("12", "59") shouldBe (("12", "59"))
//      normalizeTimeFields("a", "b") shouldBe (("0a", "0b"))
//      normalizeTimeFields("10", "1") shouldBe (("10", "01"))
//      normalizeTimeFields("1", "11") shouldBe (("01", "11"))
//    }
//
//    "validate hour of day" in {
//      isValidHour("00") shouldBe true
//      isValidHour("01") shouldBe true
//      isValidHour("12") shouldBe true
//      isValidHour("13") shouldBe true
//      isValidHour("23") shouldBe true
//      isValidHour("24") shouldBe false
//      isValidHour("25") shouldBe false
//    }
//
//    "validate minutes of hour" in {
//      isValidMinutes("00") shouldBe true
//      isValidMinutes("59") shouldBe true
//      isValidMinutes("60") shouldBe false
//      isValidMinutes("61") shouldBe false
//    }
//
//    "split the time into parts" in {
//      splitTime("12:00") shouldBe (("12", "00"))
//      splitTime("12:00") shouldBe (("12", "00"))
//      splitTime(":00") shouldBe (("", "00"))
//      splitTime(":1") shouldBe (("", "1"))
//      splitTime("1:") shouldBe (("1", ""))
//      splitTime("07:") shouldBe (("07", ""))
//      splitTime(":") shouldBe (("", ""))
//    }
//
//    "concatenate time parts" in {
//      concatTime(("12", "00")) shouldBe "12:00"
//      concatTime(("1", "0")) shouldBe "1:0"
//      concatTime(("", "")) shouldBe ":"
//    }
//
//    "validate time fields" in {
//      val validate = t => validTimeFields("foo", required = true)(t)
//      validate(("12", "00")) shouldBe Valid
//      validate(("12", "00")) shouldBe Valid
//      validate(("", "")) shouldBe Invalid(ValidationError(Seq("subfieldFocus=hour", "error.foo.all.required")))
//      validate(("12", "")) shouldBe Invalid(
//        Seq(ValidationError(Seq("subfieldFocus=minutes", "error.foo.minutes.required")))
//      )
//      validate(("", "59")) shouldBe Invalid(
//        Seq(ValidationError(Seq("subfieldFocus=hour", "error.foo.hour.required")))
//      )
//      validate(("", "59")) shouldBe Invalid(
//        Seq(ValidationError(Seq("subfieldFocus=hour", "error.foo.hour.required")))
//      )
//      validate(("24", "")) shouldBe Invalid(
//        Seq(
//          ValidationError(Seq("subfieldFocus=minutes", "error.foo.minutes.required"))
//        )
//      )
//      validate(("25", "60")) shouldBe Invalid(
//        Seq(
//          ValidationError(Seq("subfieldFocus=hour", "error.foo.hour.invalid-value"))
//        )
//      )
//      validate(("0a", "b0")) shouldBe Invalid(
//        Seq(
//          ValidationError(Seq("subfieldFocus=hour", "error.foo.hour.invalid-digits"))
//        )
//      )
//      validate(("12", "5a")) shouldBe Invalid(
//        Seq(
//          ValidationError(Seq("subfieldFocus=minutes", "error.foo.minutes.invalid-digits"))
//        )
//      )
//      validate(("12", "60")) shouldBe Invalid(
//        Seq(
//          ValidationError(Seq("subfieldFocus=minutes", "error.foo.minutes.invalid-value"))
//        )
//      )
//    }
//
//    "validate optional time fields" in {
//      val validate = t => validTimeFields("foo", required = false)(t)
//      validate(("12", "00")) shouldBe Valid
//      validate(("12", "00")) shouldBe Valid
//      validate(("", "")) shouldBe Valid
//      validate(("12", "")) shouldBe Invalid(
//        Seq(ValidationError(Seq("subfieldFocus=minutes", "error.foo.minutes.required")))
//      )
//      validate(("", "59")) shouldBe Invalid(
//        Seq(ValidationError(Seq("subfieldFocus=hour", "error.foo.hour.required")))
//      )
//      validate(("", "59")) shouldBe Invalid(
//        Seq(ValidationError(Seq("subfieldFocus=hour", "error.foo.hour.required")))
//      )
//      validate(("24", "")) shouldBe Invalid(
//        Seq(
//          ValidationError(Seq("subfieldFocus=minutes", "error.foo.minutes.required"))
//        )
//      )
//      validate(("25", "60")) shouldBe Invalid(
//        Seq(
//          ValidationError(Seq("subfieldFocus=hour", "error.foo.hour.invalid-value"))
//        )
//      )
//      validate(("0a", "b0")) shouldBe Invalid(
//        Seq(
//          ValidationError(Seq("subfieldFocus=hour", "error.foo.hour.invalid-digits"))
//        )
//      )
//    }
//
//    "validate and map time parts into a time" in {
//      timeFieldsMapping("bar").bind(Map("hour" -> "12", "minutes" -> "45")) shouldBe Right(
//        LocalTime.parse("12:45")
//      )
//      timeFieldsMapping("bar").bind(Map("hour" -> "012", "minutes" -> "0045")) shouldBe Right(
//        LocalTime.parse("12:45")
//      )
//      timeFieldsMapping("bar").bind(Map("hour" -> "12", "minutes" -> "00")) shouldBe Right(
//        LocalTime.parse("12:00")
//      )
//      timeFieldsMapping("bar").bind(Map("hour" -> "3", "minutes" -> "3")) shouldBe Right(
//        LocalTime.parse("03:03")
//      )
//      timeFieldsMapping("bar").bind(Map("hour" -> "0", "minutes" -> "3")) shouldBe Right(
//        LocalTime.parse("00:03")
//      )
//
//      timeFieldsMapping("bar").unbind(LocalTime.parse("12:45")) shouldBe Map("hour" -> "12", "minutes" -> "45")
//      timeFieldsMapping("bar").unbind(LocalTime.parse("03:03")) shouldBe Map("hour" -> "03", "minutes" -> "03")
//      timeFieldsMapping("bar").unbind(LocalTime.parse("13:53")) shouldBe Map("hour" -> "13", "minutes" -> "53")
//
//      timeFieldsMapping("bar").bind(Map("hour" -> "24", "minutes" -> "33")) should haveOnlyError(
//        "error.bar.hour.invalid-value"
//      )
//      timeFieldsMapping("bar").bind(Map("hour" -> "59", "minutes" -> "29")) should haveOnlyError(
//        "error.bar.hour.invalid-value"
//      )
//      timeFieldsMapping("bar").bind(Map()) should haveError("error.bar.all.required")
//      timeFieldsMapping("bar").bind(Map("hour" -> "", "minutes" -> "")) should haveError(
//        "error.bar.all.required"
//      )
//      timeFieldsMapping("bar").bind(Map("hour" -> "", "minutes" -> "")) should haveError(
//        "error.bar.all.required"
//      )
//      timeFieldsMapping("bar").bind(Map("hour" -> "12")) should haveOnlyError(
//        "error.bar.minutes.required"
//      )
//      timeFieldsMapping("bar").bind(Map("hour" -> "25", "minutes" -> "60")) should haveOnlyError(
//        "error.bar.hour.invalid-value"
//      )
//    }
//
//    "validate and optionally map time parts into a time" in {
//      optionalTimeFieldsMapping("bar").bind(Map("hour" -> "12", "minutes" -> "45")) shouldBe Right(
//        Some(LocalTime.parse("12:45"))
//      )
//      optionalTimeFieldsMapping("bar").bind(Map("hour" -> "0012", "minutes" -> "045")) shouldBe Right(
//        Some(LocalTime.parse("12:45"))
//      )
//      optionalTimeFieldsMapping("bar").bind(Map("hour" -> "12", "minutes" -> "00")) shouldBe Right(
//        Some(LocalTime.parse("12:00"))
//      )
//      optionalTimeFieldsMapping("bar").bind(Map("hour" -> "3", "minutes" -> "3")) shouldBe Right(
//        Some(LocalTime.parse("03:03"))
//      )
//      optionalTimeFieldsMapping("bar").bind(Map("hour" -> "15", "minutes" -> "3")) shouldBe Right(
//        Some(LocalTime.parse("15:03"))
//      )
//
//      optionalTimeFieldsMapping("bar").unbind(Some(LocalTime.parse("15:03"))) shouldBe Map(
//        "hour"    -> "15",
//        "minutes" -> "03"
//      )
//      optionalTimeFieldsMapping("bar").unbind(Some(LocalTime.parse("05:33"))) shouldBe Map(
//        "hour"    -> "05",
//        "minutes" -> "33"
//      )
//      optionalTimeFieldsMapping("bar").unbind(None) shouldBe Map("hour" -> "", "minutes" -> "")
//
//      optionalTimeFieldsMapping("bar").bind(
//        Map("hour" -> "25", "minutes" -> "33")
//      ) should haveOnlyError(
//        "error.bar.hour.invalid-value"
//      )
//      optionalTimeFieldsMapping("bar").bind(
//        Map("hour" -> "59", "minutes" -> "29")
//      ) should haveOnlyError(
//        "error.bar.hour.invalid-value"
//      )
//      optionalTimeFieldsMapping("bar").bind(
//        Map("hour" -> "24", "minutes" -> "29")
//      ) should haveOnlyError(
//        "error.bar.hour.invalid-value"
//      )
//      optionalTimeFieldsMapping("bar").bind(Map()) shouldBe Right(None)
//      optionalTimeFieldsMapping("bar").bind(Map("hour" -> "", "minutes" -> "")) shouldBe Right(None)
//      optionalTimeFieldsMapping("bar").bind(Map("hour" -> "", "minutes" -> "")) shouldBe Right(None)
//      optionalTimeFieldsMapping("bar").bind(Map("hour" -> "12")) should haveOnlyError(
//        "error.bar.minutes.required"
//      )
//      optionalTimeFieldsMapping("bar").bind(Map("hour" -> "25", "minutes" -> "60")) should haveOnlyError(
//        "error.bar.hour.invalid-value"
//      )
//    }
//  }
//
//}
