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
import uk.gov.hmrc.traderservices.models.{EPU, EntryNumber, ExportPriorityGoods, ExportRequestType, ExportRouteType}
import uk.gov.hmrc.traderservices.support.FormMappingMatchers
import uk.gov.hmrc.traderservices.models.ImportPriorityGoods
import uk.gov.hmrc.traderservices.models.ExportFreightType
import uk.gov.hmrc.traderservices.models.ImportFreightType

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
      exportRequestTypeMapping.bind(Map("" -> "New")) shouldBe Right(ExportRequestType.New)
      exportRequestTypeMapping.bind(Map("" -> "Cancellation")) shouldBe Right(ExportRequestType.Cancellation)
      exportRequestTypeMapping.bind(Map("" -> "Hold")) shouldBe Right(ExportRequestType.Hold)
      exportRequestTypeMapping.bind(Map("" -> "C1601")) shouldBe Right(ExportRequestType.C1601)
      exportRequestTypeMapping.bind(Map("" -> "C1602")) shouldBe Right(ExportRequestType.C1602)
      exportRequestTypeMapping.bind(Map("" -> "C1603")) shouldBe Right(ExportRequestType.C1603)
      exportRequestTypeMapping.bind(Map("" -> "WithdrawalOrReturn")) shouldBe Right(
        ExportRequestType.WithdrawalOrReturn
      )
      exportRequestTypeMapping.bind(Map("" -> "Foo")) should haveOnlyError[ExportRequestType](
        "error.exportRequestType.invalid-option"
      )
      exportRequestTypeMapping.bind(Map()) should haveOnlyError[ExportRequestType](
        "error.exportRequestType.required"
      )
    }

    "validate export routeType" in {
      exportRouteTypeMapping.bind(Map("" -> "Route1")) shouldBe Right(ExportRouteType.Route1)
      exportRouteTypeMapping.bind(Map("" -> "Route1Cap")) shouldBe Right(ExportRouteType.Route1Cap)
      exportRouteTypeMapping.bind(Map("" -> "Route2")) shouldBe Right(ExportRouteType.Route2)
      exportRouteTypeMapping.bind(Map("" -> "Route3")) shouldBe Right(ExportRouteType.Route3)
      exportRouteTypeMapping.bind(Map("" -> "Route6")) shouldBe Right(ExportRouteType.Route6)
      exportRouteTypeMapping.bind(Map("" -> "Hold")) shouldBe Right(ExportRouteType.Hold)
      exportRouteTypeMapping.bind(Map("" -> "Foo")) should haveOnlyError[ExportRouteType](
        "error.exportRouteType.invalid-option"
      )
      exportRouteTypeMapping.bind(Map()) should haveOnlyError[ExportRouteType](
        "error.exportRouteType.required"
      )
    }

    "validate export hasPriorityGoods" in {
      exportHasPriorityGoodsMapping.bind(Map("" -> "yes")) shouldBe Right(true)
      exportHasPriorityGoodsMapping.bind(Map("" -> "no")) shouldBe Right(false)
      exportHasPriorityGoodsMapping.bind(Map()) should haveOnlyError[Boolean]("error.exportHasPriorityGoods.required")
    }

    "validate import hasPriorityGoods" in {
      importHasPriorityGoodsMapping.bind(Map("" -> "yes")) shouldBe Right(true)
      importHasPriorityGoodsMapping.bind(Map("" -> "no")) shouldBe Right(false)
      importHasPriorityGoodsMapping.bind(Map()) should haveOnlyError[Boolean]("error.importHasPriorityGoods.required")
    }

    "validate export priorityGoods" in {
      exportPriorityGoodsMapping.bind(Map("" -> "LiveAnimals")) shouldBe Right(ExportPriorityGoods.LiveAnimals)
      exportPriorityGoodsMapping.bind(Map("" -> "HumanRemains")) shouldBe Right(ExportPriorityGoods.HumanRemains)
      exportPriorityGoodsMapping.bind(Map("" -> "HighValueArt")) shouldBe Right(ExportPriorityGoods.HighValueArt)
      exportPriorityGoodsMapping.bind(Map("" -> "ClassADrugs")) shouldBe Right(ExportPriorityGoods.ClassADrugs)
      exportPriorityGoodsMapping.bind(Map("" -> "ExplosivesOrFireworks")) shouldBe Right(
        ExportPriorityGoods.ExplosivesOrFireworks
      )
      exportPriorityGoodsMapping.bind(Map("" -> "Foo")) should haveOnlyError[ExportPriorityGoods](
        "error.exportPriorityGoods.invalid-option"
      )
      exportPriorityGoodsMapping.bind(Map()) should haveOnlyError[ExportPriorityGoods](
        "error.exportPriorityGoods.required"
      )
    }

    "validate import priorityGoods" in {
      importPriorityGoodsMapping.bind(Map("" -> "LiveAnimals")) shouldBe Right(ImportPriorityGoods.LiveAnimals)
      importPriorityGoodsMapping.bind(Map("" -> "HumanRemains")) shouldBe Right(ImportPriorityGoods.HumanRemains)
      importPriorityGoodsMapping.bind(Map("" -> "HighValueArt")) shouldBe Right(ImportPriorityGoods.HighValueArt)
      importPriorityGoodsMapping.bind(Map("" -> "ClassADrugs")) shouldBe Right(ImportPriorityGoods.ClassADrugs)
      importPriorityGoodsMapping.bind(Map("" -> "ExplosivesOrFireworks")) shouldBe Right(
        ImportPriorityGoods.ExplosivesOrFireworks
      )
      importPriorityGoodsMapping.bind(Map("" -> "Foo")) should haveOnlyError[ImportPriorityGoods](
        "error.importPriorityGoods.invalid-option"
      )
      importPriorityGoodsMapping.bind(Map()) should haveOnlyError[ImportPriorityGoods](
        "error.importPriorityGoods.required"
      )
    }

    "validate import hasALVS" in {
      importHasALVSMapping.bind(Map("" -> "yes")) shouldBe Right(true)
      importHasALVSMapping.bind(Map("" -> "no")) shouldBe Right(false)
      importHasALVSMapping.bind(Map()) should haveOnlyError[Boolean]("error.importHasALVS.required")
    }

    "validate export freightType" in {
      exportFreightTypeMapping.bind(Map("" -> "Air")) shouldBe Right(ExportFreightType.Air)
      exportFreightTypeMapping.bind(Map("" -> "Maritime")) shouldBe Right(ExportFreightType.Maritime)
      exportFreightTypeMapping.bind(Map("" -> "RORO")) shouldBe Right(ExportFreightType.RORO)
      exportFreightTypeMapping.bind(Map()) should haveOnlyError[ExportFreightType]("error.exportFreightType.required")
    }

    "validate import freightType" in {
      importFreightTypeMapping.bind(Map("" -> "Air")) shouldBe Right(ImportFreightType.Air)
      importFreightTypeMapping.bind(Map("" -> "Maritime")) shouldBe Right(ImportFreightType.Maritime)
      importFreightTypeMapping.bind(Map("" -> "RORO")) shouldBe Right(ImportFreightType.RORO)
      importFreightTypeMapping.bind(Map()) should haveOnlyError[ImportFreightType]("error.importFreightType.required")
    }

    "validate mandatory vesselName" in {
      mandatoryVesselNameMapping.bind(Map("" -> "Titanic")) shouldBe Right(Some("Titanic"))
      mandatoryVesselNameMapping.bind(Map("" -> "Brian's boat ")) shouldBe Right(Some("Brian's boat"))
      mandatoryVesselNameMapping.bind(Map("" -> " Ship+ley / West-Yorkshire")) shouldBe Right(
        Some("Ship+ley / West-Yorkshire")
      )
      mandatoryVesselNameMapping.bind(Map("" -> "DINGY  123")) shouldBe Right(Some("DINGY 123"))
      mandatoryVesselNameMapping.bind(Map("" -> "Me & You")) shouldBe Right(Some("Me & You"))
      mandatoryVesselNameMapping.bind(Map("" -> "   Titanic  ")) shouldBe Right(Some("Titanic"))
      mandatoryVesselNameMapping.bind(Map("" -> "")) should haveOnlyError[Option[String]](
        "error.vesselName.required"
      )
      mandatoryVesselNameMapping.bind(Map("" -> " ")) should haveOnlyError[Option[String]](
        "error.vesselName.required"
      )
      mandatoryVesselNameMapping.bind(Map("" -> "  ")) should haveOnlyError[Option[String]](
        "error.vesselName.required"
      )
      mandatoryVesselNameMapping.bind(Map("" -> "X" * 129)) should haveOnlyError[Option[String]](
        "error.vesselName.invalid-length"
      )
      mandatoryVesselNameMapping.bind(Map("" -> "@X" * 65)) should haveOnlyErrors[Option[String]](
        "error.vesselName.invalid-length",
        "error.vesselName.invalid-characters"
      )
      mandatoryVesselNameMapping.bind(Map("" -> "-+-")) should haveOnlyError[Option[String]](
        "error.vesselName.invalid-characters"
      )
      mandatoryVesselNameMapping.bind(Map("" -> "/")) should haveOnlyError[Option[String]](
        "error.vesselName.invalid-characters"
      )
      mandatoryVesselNameMapping.bind(Map("" -> "a$$$$$$$")) should haveOnlyError[Option[String]](
        "error.vesselName.invalid-characters"
      )
      mandatoryVesselNameMapping.bind(Map("" -> "B@d name")) should haveOnlyError[Option[String]](
        "error.vesselName.invalid-characters"
      )
    }

    "validate optional vesselName" in {
      optionalVesselNameMapping.bind(Map("" -> "Titanic")) shouldBe Right(Some("Titanic"))
      optionalVesselNameMapping.bind(Map("" -> "Brian's boat ")) shouldBe Right(Some("Brian's boat"))
      optionalVesselNameMapping.bind(Map("" -> " Ship+ley / West-Yorkshire")) shouldBe Right(
        Some("Ship+ley / West-Yorkshire")
      )
      optionalVesselNameMapping.bind(Map("" -> "DINGY  123")) shouldBe Right(Some("DINGY 123"))
      optionalVesselNameMapping.bind(Map("" -> "Me & You")) shouldBe Right(Some("Me & You"))
      optionalVesselNameMapping.bind(Map("" -> "   Titanic  ")) shouldBe Right(Some("Titanic"))
      optionalVesselNameMapping.bind(Map("" -> "")) shouldBe Right(None)
      optionalVesselNameMapping.bind(Map("" -> " ")) shouldBe Right(None)
      optionalVesselNameMapping.bind(Map("" -> "  ")) shouldBe Right(None)
      optionalVesselNameMapping.bind(Map("" -> "                        ")) shouldBe Right(None)
      optionalVesselNameMapping.bind(Map("" -> "                        A")) shouldBe Right(Some("A"))
      optionalVesselNameMapping.bind(Map("" -> "X" * 129)) should haveOnlyError[Option[String]](
        "error.vesselName.invalid-length"
      )
      optionalVesselNameMapping.bind(Map("" -> "@X" * 65)) should haveOnlyErrors[Option[String]](
        "error.vesselName.invalid-length",
        "error.vesselName.invalid-characters"
      )
      optionalVesselNameMapping.bind(Map("" -> "-+-")) should haveOnlyError[Option[String]](
        "error.vesselName.invalid-characters"
      )
      optionalVesselNameMapping.bind(Map("" -> "/")) should haveOnlyError[Option[String]](
        "error.vesselName.invalid-characters"
      )
      optionalVesselNameMapping.bind(Map("" -> "a$$$$$$$")) should haveOnlyError[Option[String]](
        "error.vesselName.invalid-characters"
      )
      optionalVesselNameMapping.bind(Map("" -> "B@d name")) should haveOnlyError[Option[String]](
        "error.vesselName.invalid-characters"
      )
    }

  }

}
