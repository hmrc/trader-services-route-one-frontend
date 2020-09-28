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

import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.traderservices.controllers.FormFieldMappings._
import uk.gov.hmrc.traderservices.models.{EPU, EntryNumber, ExportGoodsPriority, ExportRequestType, ExportRouteType}
import uk.gov.hmrc.traderservices.support.FormMappingMatchers

class FormFieldMappingsSpec extends UnitSpec with FormMappingMatchers {

  "FormFieldMappings" should {

    "validate EPU" in {
      epuMapping.bind(Map("" -> "123")) shouldBe Right(EPU(123))
      epuMapping.bind(Map("" -> "")) should haveOnlyError[EPU]("error.epu.required")
      epuMapping.bind(Map("" -> "1")) should haveOnlyError[EPU]("error.epu.invalid-length")
      epuMapping.bind(Map("" -> "12")) should haveOnlyError[EPU]("error.epu.invalid-length")
      epuMapping.bind(Map("" -> "1224")) should haveOnlyError[EPU]("error.epu.invalid-length")
      epuMapping.bind(Map("" -> "12A")) should haveOnlyError[EPU]("error.epu.invalid-only-digits")
      epuMapping.bind(Map("" -> "AAA")) should haveOnlyError[EPU]("error.epu.invalid-only-digits")
      epuMapping.bind(Map("" -> "A12")) should haveOnlyError[EPU]("error.epu.invalid-only-digits")
      epuMapping.bind(Map("" -> "1A2")) should haveOnlyError[EPU]("error.epu.invalid-only-digits")
      epuMapping.bind(Map("" -> "701")) should haveOnlyError[EPU]("error.epu.invalid-number")
      epuMapping.bind(Map("" -> "999")) should haveOnlyError[EPU]("error.epu.invalid-number")
    }

    "validate EntryNumber" in {
      entryNumberMapping.bind(Map("" -> "000000Z")) shouldBe Right(EntryNumber("000000Z"))
      entryNumberMapping.bind(Map("" -> "A00000Z")) shouldBe Right(EntryNumber("A00000Z"))
      entryNumberMapping.bind(Map("" -> "")) should haveOnlyError[EntryNumber]("error.entryNumber.required")
      entryNumberMapping.bind(Map("" -> "00000Z")) should haveOnlyErrors[EntryNumber](
        "error.entryNumber.invalid-length",
        "error.entryNumber.invalid-letter-wrong-position"
      )
      entryNumberMapping.bind(Map("" -> "0000Z")) should haveOnlyErrors[EntryNumber](
        "error.entryNumber.invalid-length",
        "error.entryNumber.invalid-letter-wrong-position"
      )
      entryNumberMapping.bind(Map("" -> "00000")) should haveOnlyErrors[EntryNumber](
        "error.entryNumber.invalid-length"
      )
      entryNumberMapping.bind(Map("" -> "0")) should haveOnlyErrors[EntryNumber](
        "error.entryNumber.invalid-length"
      )
      entryNumberMapping.bind(Map("" -> "Z")) should haveOnlyError[EntryNumber]("error.entryNumber.invalid-length")
      entryNumberMapping.bind(Map("" -> "+")) should haveOnlyErrors[EntryNumber](
        "error.entryNumber.invalid-length",
        "error.entryNumber.invalid-only-digits-and-letters"
      )
      entryNumberMapping.bind(Map("" -> "000000Z+")) should haveOnlyErrors[EntryNumber](
        "error.entryNumber.invalid-only-digits-and-letters",
        "error.entryNumber.invalid-length"
      )
      entryNumberMapping.bind(Map("" -> "000+000Z")) should haveOnlyErrors[EntryNumber](
        "error.entryNumber.invalid-only-digits-and-letters",
        "error.entryNumber.invalid-length",
        "error.entryNumber.invalid-letter-wrong-position"
      )
      entryNumberMapping.bind(Map("" -> "+++++++")) should haveOnlyErrors[EntryNumber](
        "error.entryNumber.invalid-only-digits-and-letters",
        "error.entryNumber.invalid-ends-with-letter",
        "error.entryNumber.invalid-letter-wrong-position"
      )
      entryNumberMapping.bind(Map("" -> "++++++Z")) should haveOnlyErrors[EntryNumber](
        "error.entryNumber.invalid-only-digits-and-letters",
        "error.entryNumber.invalid-letter-wrong-position"
      )
      entryNumberMapping.bind(Map("" -> "00000Z0")) should haveOnlyErrors[EntryNumber](
        "error.entryNumber.invalid-ends-with-letter",
        "error.entryNumber.invalid-letter-wrong-position"
      )
      entryNumberMapping.bind(Map("" -> "Z000000")) should haveOnlyError[EntryNumber](
        "error.entryNumber.invalid-ends-with-letter"
      )
      entryNumberMapping.bind(Map("" -> "0A0000Z")) should haveOnlyError[EntryNumber](
        "error.entryNumber.invalid-letter-wrong-position"
      )
      entryNumberMapping.bind(Map("" -> "0A000000Z")) should haveOnlyErrors[EntryNumber](
        "error.entryNumber.invalid-length",
        "error.entryNumber.invalid-letter-wrong-position"
      )
    }

    "validate EntryDate" in {
      entryDateMapping.bind(Map("year" -> "2020", "month" -> "09", "day" -> "21")) shouldBe Right(
        LocalDate.parse("2020-09-21")
      )
      entryDateMapping.bind(Map("year" -> "", "month" -> "", "day" -> "")) should haveOnlyError[LocalDate](
        "error.entryDate.required"
      )
      entryDateMapping.bind(Map("year" -> "2020", "month" -> "", "day" -> "")) should haveOnlyErrors[LocalDate](
        "error.entryDate.required-month",
        "error.entryDate.required-day"
      )
      entryDateMapping.bind(Map("year" -> "", "month" -> "11", "day" -> "")) should haveOnlyErrors[LocalDate](
        "error.entryDate.required-year",
        "error.entryDate.required-day"
      )
      entryDateMapping.bind(Map("year" -> "", "month" -> "", "day" -> "31")) should haveOnlyErrors[LocalDate](
        "error.entryDate.required-year",
        "error.entryDate.required-month"
      )
      entryDateMapping.bind(Map("year" -> "2020", "month" -> "12", "day" -> "")) should haveOnlyError[LocalDate](
        "error.entryDate.required-day"
      )
      entryDateMapping.bind(Map("year" -> "2020", "month" -> "13", "day" -> "")) should haveOnlyErrors[LocalDate](
        "error.entryDate.required-day",
        "error.entryDate.invalid-month-value"
      )
      entryDateMapping.bind(Map("year" -> "2020", "month" -> "13", "day" -> "32")) should haveOnlyErrors[LocalDate](
        "error.entryDate.invalid-day-value",
        "error.entryDate.invalid-month-value"
      )
      entryDateMapping.bind(Map("year" -> "20", "month" -> "13", "day" -> "32")) should haveOnlyErrors[LocalDate](
        "error.entryDate.invalid-day-value",
        "error.entryDate.invalid-month-value"
      )
      entryDateMapping.bind(Map("year" -> "2020", "month" -> "2", "day" -> "30")) should haveOnlyError[LocalDate](
        "error.entryDate.invalid-day-value"
      )
      entryDateMapping.bind(Map("year" -> "202", "month" -> "2", "day" -> "28")) should haveOnlyError[LocalDate](
        "error.entryDate.invalid-year-value"
      )
      entryDateMapping.bind(Map("year" -> "202A", "month" -> "1", "day" -> "1")) should haveOnlyError[LocalDate](
        "error.entryDate.invalid-year-digits"
      )
      entryDateMapping.bind(Map("year" -> "2020", "month" -> "06", "day" -> "31")) should haveOnlyError[LocalDate](
        "error.entryDate.invalid-day-value"
      )
      entryDateMapping.bind(Map("year" -> "", "month" -> "0A", "day" -> "21")) should haveError[LocalDate](
        "error.entryDate.required-year"
      ).and(haveError[LocalDate]("error.entryDate.invalid-month-digits"))
      entryDateMapping.bind(Map("year" -> "2020", "month" -> "0A", "day" -> "2AA")) should haveOnlyErrors[LocalDate](
        "error.entryDate.invalid-month-digits",
        "error.entryDate.invalid-day-digits"
      )
      entryDateMapping.bind(Map("year" -> "2021", "month" -> "0A", "day" -> "2AA")) should haveOnlyErrors[LocalDate](
        "error.entryDate.invalid-month-digits",
        "error.entryDate.invalid-day-digits"
      )
      entryDateMapping.bind(Map("year" -> "2050", "month" -> "01", "day" -> "01")) should haveOnlyError[LocalDate](
        "error.entryDate.invalid-value-future"
      )
      entryDateMapping.bind(Map("year" -> "1970", "month" -> "01", "day" -> "01")) should haveOnlyError[LocalDate](
        "error.entryDate.invalid-value-past"
      )
    }

    "validate export requestType" in {
      requestTypeMapping.bind(Map("" -> "New")) shouldBe Right(ExportRequestType.New)
      requestTypeMapping.bind(Map("" -> "Cancellation")) shouldBe Right(ExportRequestType.Cancellation)
      requestTypeMapping.bind(Map("" -> "Hold")) shouldBe Right(ExportRequestType.Hold)
      requestTypeMapping.bind(Map("" -> "C1601")) shouldBe Right(ExportRequestType.C1601)
      requestTypeMapping.bind(Map("" -> "C1602")) shouldBe Right(ExportRequestType.C1602)
      requestTypeMapping.bind(Map("" -> "C1603")) shouldBe Right(ExportRequestType.C1603)
      requestTypeMapping.bind(Map("" -> "WithdrawalOrReturn")) shouldBe Right(ExportRequestType.WithdrawalOrReturn)
      requestTypeMapping.bind(Map("" -> "Foo")) should haveOnlyError[ExportRequestType](
        "error.requestType.invalid-option"
      )
      requestTypeMapping.bind(Map()) should haveOnlyError[ExportRequestType](
        "error.requestType.required"
      )
    }

    "validate export routeType" in {
      routeTypeMapping.bind(Map("" -> "Route1")) shouldBe Right(ExportRouteType.Route1)
      routeTypeMapping.bind(Map("" -> "Route1Cap")) shouldBe Right(ExportRouteType.Route1Cap)
      routeTypeMapping.bind(Map("" -> "Route2")) shouldBe Right(ExportRouteType.Route2)
      routeTypeMapping.bind(Map("" -> "Route3")) shouldBe Right(ExportRouteType.Route3)
      routeTypeMapping.bind(Map("" -> "Route6")) shouldBe Right(ExportRouteType.Route6)
      routeTypeMapping.bind(Map("" -> "Hold")) shouldBe Right(ExportRouteType.Hold)
      routeTypeMapping.bind(Map("" -> "Foo")) should haveOnlyError[ExportRouteType](
        "error.routeType.invalid-option"
      )
      routeTypeMapping.bind(Map()) should haveOnlyError[ExportRouteType](
        "error.routeType.required"
      )
    }

    "validate export goodsPriority" in {
      goodPriorityMapping.bind(Map("" -> "None")) shouldBe Right(ExportGoodsPriority.None)
      goodPriorityMapping.bind(Map("" -> "LiveAnimals")) shouldBe Right(ExportGoodsPriority.LiveAnimals)
      goodPriorityMapping.bind(Map("" -> "HumanRemains")) shouldBe Right(ExportGoodsPriority.HumanRemains)
      goodPriorityMapping.bind(Map("" -> "HighValueArt")) shouldBe Right(ExportGoodsPriority.HighValueArt)
      goodPriorityMapping.bind(Map("" -> "ClassADrugs")) shouldBe Right(ExportGoodsPriority.ClassADrugs)
      goodPriorityMapping.bind(Map("" -> "ExplosivesOrFireworks")) shouldBe Right(
        ExportGoodsPriority.ExplosivesOrFireworks
      )
      goodPriorityMapping.bind(Map("" -> "Foo")) should haveOnlyError[ExportGoodsPriority](
        "error.goodsPriority.invalid-option"
      )
      goodPriorityMapping.bind(Map()) should haveOnlyError[ExportGoodsPriority](
        "error.goodsPriority.required"
      )
    }
  }

}
