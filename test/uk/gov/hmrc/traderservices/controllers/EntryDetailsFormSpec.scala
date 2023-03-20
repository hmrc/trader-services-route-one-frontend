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
//import java.time.LocalDate
//
//import play.api.data.FormError
//import uk.gov.hmrc.traderservices.support.UnitSpec
//import uk.gov.hmrc.traderservices.models.{EPU, EntryDetails, EntryNumber}
//import uk.gov.hmrc.traderservices.support.FormMatchers
//import java.time.temporal.ChronoField
//
//class EntryDetailsFormSpec extends UnitSpec with FormMatchers {
//
//  val date = LocalDate.now
//
//  val formOutput = EntryDetails(
//    epu = EPU(123),
//    entryNumber = EntryNumber("000000Z"),
//    entryDate = date
//  )
//
//  def formInputFor(date: LocalDate) =
//    Map(
//      "epu"             -> "123",
//      "entryNumber"     -> "000000Z",
//      "entryDate.year"  -> f"${date.get(ChronoField.YEAR)}",
//      "entryDate.month" -> f"${date.get(ChronoField.MONTH_OF_YEAR)}%02d",
//      "entryDate.day"   -> f"${date.get(ChronoField.DAY_OF_MONTH)}%02d"
//    )
//
//  val formInput = formInputFor(date)
//
//  "EntryDetailsForm" should {
//
//    val form = CreateCaseJourneyController.EntryDetailsForm
//
//    "bind some input fields and return EntryDetails and fill it back" in {
//      form.bind(formInput).value shouldBe Some(formOutput)
//      form.fill(formOutput).data shouldBe formInput
//    }
//
//    "report an error when EPU is missing" in {
//      val input = formInput.updated("epu", "")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(FormError("epu", "error.epu.required"))
//    }
//
//    "report an error when EPU is invalid" in {
//      val input = formInput.updated("epu", "ABCD")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("epu", "error.epu.invalid-only-digits")
//      )
//    }
//
//    "report an error when EntryNumber is missing" in {
//      val input = formInput.updated("entryNumber", "")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(FormError("entryNumber", "error.entryNumber.required"))
//    }
//
//    "report an error when EntryNumber is invalid" in {
//      val input = formInput.updated("entryNumber", "00000Z")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("entryNumber", "error.entryNumber.invalid-length")
//      )
//    }
//
//    "report an error when entryDate.year is missing" in {
//      val input = formInput.updated("entryDate.year", "")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("entryDate", Seq("subfieldFocus=year", "error.entryDate.year.required"))
//      )
//    }
//
//    "report an error when entryDate.year is invalid" in {
//      val input = formInput.updated("entryDate.year", "197B")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("entryDate", Seq("subfieldFocus=year", "error.entryDate.year.invalid-value"))
//      )
//    }
//
//    "report an error when entryDate.day is invalid - contains digit and letter" in {
//      val input = formInput.updated("entryDate.day", "0X")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("entryDate", Seq("subfieldFocus=day", "error.entryDate.day.invalid-value"))
//      )
//    }
//
//    "report an error when entryDate.day is invalid - contains value out-of-scope" in {
//      val input = formInput.updated("entryDate.day", "32")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("entryDate", Seq("subfieldFocus=day", "error.entryDate.day.invalid-value"))
//      )
//    }
//
//    "report an error when entryDate.month is invalid - contains digit and letter" in {
//      val input = formInput.updated("entryDate.month", "1X")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("entryDate", Seq("subfieldFocus=month", "error.entryDate.month.invalid-value"))
//      )
//    }
//
//    "report an error when entryDate.month is invalid - contains value out-of-scope" in {
//      val input = formInput.updated("entryDate.month", "13")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("entryDate", Seq("subfieldFocus=month", "error.entryDate.month.invalid-value"))
//      )
//    }
//
//    "disallow empty entryDate.day" in {
//      val input = formInput.updated("entryDate.day", "")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("entryDate", Seq("subfieldFocus=day", "error.entryDate.day.required"))
//      )
//    }
//
//    "report an error when empty entryDate.month " in {
//      val input = formInput.updated("entryDate.month", "")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("entryDate", Seq("subfieldFocus=month", "error.entryDate.month.required"))
//      )
//    }
//
//    "report an error when entryDate in the future" in {
//      val input = formInputFor(date.plusDays(1))
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("entryDate", Seq("subfieldFocus=day", "error.entryDate.all.invalid-value-future"))
//      )
//    }
//
//    "disallow empty entryDate.day and empty entryDate.month" in {
//      val input = formInput.updated("entryDate.day", "").updated("entryDate.month", "")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("entryDate", Seq("subfieldFocus=day", "error.entryDate.day.required"))
//      )
//    }
//  }
//}
