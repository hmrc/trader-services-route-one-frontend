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

package uk.gov.hmrc.traderservices.controllers

import org.scalacheck.Gen
import org.scalacheck.Shrink.shrinkAny
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.data.validation.{Invalid, Valid}
import test.uk.gov.hmrc.traderservices.support.UnitSpec
import uk.gov.hmrc.traderservices.controllers.FormFieldMappings._
import uk.gov.hmrc.traderservices.generators.FormFieldGenerators
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.support.FormMappingMatchers

import java.time.{LocalDate, LocalTime}

class FormFieldMappingsSpec
    extends UnitSpec with FormMappingMatchers with ScalaCheckDrivenPropertyChecks with FormFieldGenerators {

  "FormFieldMappings" should {
    "validate EPU" in {

      forAll(Gen.choose(1, 669), Gen.choose(670, 999)) { (rawEPU, invalidEPU) =>
        val epu = f"$rawEPU%03d"
        epuMapping.bind(Map("" -> epu)) shouldBe Right(EPU(rawEPU))
        epuMapping.bind(Map("" -> epu.slice(1, 2))) should haveOnlyError[EPU]("error.epu.invalid-length")
        epuMapping.bind(Map("" -> invalidEPU.toString)) should haveOnlyError[EPU]("error.epu.invalid-number")
        epuMapping.bind(Map("" -> epu.replaceFirst("[0-9]", "A"))) should haveOnlyError[EPU](
          "error.epu.invalid-only-digits"
        )
      }

      epuMapping.bind(Map("" -> "")) should haveOnlyError[EPU]("error.epu.required")
    }

    "validate EntryNumber" in {

      forAll(entryNumberGen, Gen.alphaChar, invalidSpecialCharGen) { (number, char, specialChar) =>
        val numberWithAlphaPrefix = number.replaceFirst("\\d", char.toUpper.toString)

        entryNumberMapping.bind(Map("" -> number)) shouldBe Right(EntryNumber(number))
        entryNumberMapping.bind(Map("" -> numberWithAlphaPrefix)) shouldBe Right(EntryNumber(numberWithAlphaPrefix))

        entryNumberMapping.bind(Map("" -> number.slice(0, number.length - 1))) should haveOnlyError[EntryNumber](
          "error.entryNumber.invalid-length"
        )

        entryNumberMapping
          .bind(Map("" -> number.replace(number.charAt(4), specialChar))) should haveOnlyError[EntryNumber](
          "error.entryNumber.invalid-only-digits-and-letters"
        )

        entryNumberMapping.bind(Map("" -> number.reverse)) should haveOnlyError[EntryNumber](
          "error.entryNumber.invalid-ends-with-letter"
        )

        entryNumberMapping.bind(Map("" -> number.replace(number.charAt(4), char))) should haveOnlyError[EntryNumber](
          "error.entryNumber.invalid-letter-wrong-position"
        )
      }

      entryNumberMapping.bind(Map("" -> "")) should haveOnlyError[EntryNumber]("error.entryNumber.required")
    }

    "validate EntryDate" in {

      forAll(Gen.chooseNum(1, 10).map(LocalDate.now.minusDays(_)), stringGen) { (date, invalidValue) =>
        entryDateMapping.bind(
          Map("year" -> s"${date.getYear}", "month" -> s"${date.getMonthValue}", "day" -> s"${date.getDayOfMonth}")
        ) shouldBe Right(date)

        entryDateMapping.bind(
          Map("year" -> s"${date.getYear}", "month" -> s"${date.getMonthValue}", "day" -> "")
        ) should haveOnlyError[LocalDate](
          "error.entryDate.day.required"
        )

        entryDateMapping.bind(
          Map("year" -> s"${date.getYear}", "month" -> s"${date.getMonthValue}", "day" -> invalidValue)
        ) should haveOnlyError[LocalDate](
          "error.entryDate.day.invalid-value"
        )

        entryDateMapping.bind(
          Map("year" -> s"${date.getYear}", "month" -> "", "day" -> s"${date.getDayOfMonth}")
        ) should haveOnlyError[LocalDate](
          "error.entryDate.month.required"
        )

        entryDateMapping.bind(
          Map("year" -> s"${date.getYear}", "month" -> invalidValue, "day" -> s"${date.getDayOfMonth}")
        ) should haveOnlyError[LocalDate](
          "error.entryDate.month.invalid-value"
        )

        entryDateMapping.bind(
          Map("year" -> "", "month" -> s"${date.getMonthValue}", "day" -> s"${date.getDayOfMonth}")
        ) should haveOnlyError[LocalDate](
          "error.entryDate.year.required"
        )

        entryDateMapping.bind(
          Map("year" -> invalidValue, "month" -> s"${date.getMonthValue}", "day" -> s"${date.getDayOfMonth}")
        ) should haveOnlyError[LocalDate](
          "error.entryDate.year.invalid-value"
        )
      }

      entryDateMapping.bind(Map("year" -> "2050", "month" -> "01", "day" -> "01")) should haveOnlyError[LocalDate](
        "error.entryDate.all.invalid-value-future"
      )
      entryDateMapping.bind(Map("year" -> "1970", "month" -> "01", "day" -> "01")) should haveOnlyError[LocalDate](
        "error.entryDate.all.invalid-value-past"
      )
    }

    "validate export requestType" in {

      forAll(exportRequestTypeGen, stringGen) { (requestType, invalidInput) =>
        exportRequestTypeMapping.bind(Map("" -> requestType.toString)) shouldBe Right(requestType)

        exportRequestTypeMapping.bind(Map("" -> invalidInput)) should haveOnlyError[ExportRequestType](
          "error.exportRequestType.invalid-option"
        )
      }

      exportRequestTypeMapping.bind(Map()) should haveOnlyError[ExportRequestType](
        "error.exportRequestType.required"
      )
    }

    "validate import requestType" in {

      forAll(importRequestTypeGen, stringGen) { (requestType, invalidInput) =>
        importRequestTypeMapping.bind(Map("" -> requestType.toString)) shouldBe Right(requestType)

        importRequestTypeMapping.bind(Map("" -> invalidInput)) should haveOnlyError[ImportRequestType](
          "error.importRequestType.invalid-option"
        )
      }

      importRequestTypeMapping.bind(Map()) should haveOnlyError[ImportRequestType](
        "error.importRequestType.required"
      )
    }

    "validate export routeType" in {

      forAll(exportRouteTypeGen, stringGen) { (routeType, invalidInput) =>
        exportRouteTypeMapping.bind(Map("" -> routeType.toString)) shouldBe Right(routeType)

        exportRouteTypeMapping.bind(Map("" -> invalidInput)) should haveOnlyError[ExportRouteType](
          "error.exportRouteType.invalid-option"
        )
      }

      exportRouteTypeMapping.bind(Map()) should haveOnlyError[ExportRouteType](
        "error.exportRouteType.required"
      )
    }

    "validate import routeType" in {

      forAll(importRouteTypeGen, stringGen) { (routeType, invalidInput) =>
        importRouteTypeMapping.bind(Map("" -> routeType.toString)) shouldBe Right(routeType)

        importRouteTypeMapping.bind(Map("" -> invalidInput)) should haveOnlyError[ImportRouteType](
          "error.importRouteType.invalid-option"
        )
      }

      importRouteTypeMapping.bind(Map()) should haveOnlyError[ImportRouteType](
        "error.importRouteType.required"
      )
    }

    "validate import reason text" in {

      forAll(stringGen.suchThat(_.length <= 1024)) { input =>
        importReasonTextMapping.bind(Map("" -> input)) shouldBe Right(input)
      }

      importReasonTextMapping.bind(Map("" -> textGreaterThan(1024))) should haveOnlyError(
        "error.import.reason-text.invalid-length"
      )

      importReasonTextMapping.bind(Map("" -> "")) should haveOnlyError(
        "error.import.reason-text.required"
      )
    }

    "validate export reason text" in {

      forAll(stringGen.suchThat(_.length <= 1024)) { input =>
        exportReasonTextMapping.bind(Map("" -> input)) shouldBe Right(input)
      }

      exportReasonTextMapping.bind(Map("" -> textGreaterThan(1024))) should haveOnlyError(
        "error.export.reason-text.invalid-length"
      )
      exportReasonTextMapping.bind(Map("" -> "")) should haveOnlyError(
        "error.export.reason-text.required"
      )
    }

    "validate export hasPriorityGoods" in {

      forAll(yesNoGen) { input =>
        exportHasPriorityGoodsMapping.bind(Map("" -> input)) shouldBe Right(yesNoConversion(input))
      }

      exportHasPriorityGoodsMapping.bind(Map()) should haveOnlyError[Boolean]("error.exportHasPriorityGoods.required")
    }

    "validate import hasPriorityGoods" in {

      forAll(yesNoGen) { input =>
        importHasPriorityGoodsMapping.bind(Map("" -> input)) shouldBe Right(yesNoConversion(input))
      }

      importHasPriorityGoodsMapping.bind(Map()) should haveOnlyError[Boolean]("error.importHasPriorityGoods.required")
    }

    "validate export priorityGoods" in {

      forAll(exportPriorityGoodsGen, stringGen) { (priorityGood, invalidInput) =>
        exportPriorityGoodsMapping.bind(Map("" -> priorityGood.toString)) shouldBe Right(priorityGood)

        exportPriorityGoodsMapping.bind(Map("" -> invalidInput)) should haveOnlyError[ExportPriorityGoods](
          "error.exportPriorityGoods.invalid-option"
        )
      }

      exportPriorityGoodsMapping.bind(Map()) should haveOnlyError[ExportPriorityGoods](
        "error.exportPriorityGoods.required"
      )
    }

    "validate import priorityGoods" in {

      forAll(importPriorityGoodsGen, stringGen) { (priorityGood, invalidInput) =>
        importPriorityGoodsMapping.bind(Map("" -> priorityGood.toString)) shouldBe Right(priorityGood)

        importPriorityGoodsMapping.bind(Map("" -> invalidInput)) should haveOnlyError[ImportPriorityGoods](
          "error.importPriorityGoods.invalid-option"
        )
      }

      importPriorityGoodsMapping.bind(Map()) should haveOnlyError[ImportPriorityGoods](
        "error.importPriorityGoods.required"
      )
    }

    "validate import hasALVS" in {

      forAll(yesNoGen) { input =>
        importHasALVSMapping.bind(Map("" -> input)) shouldBe Right(yesNoConversion(input))
      }

      importHasALVSMapping.bind(Map()) should haveOnlyError[Boolean]("error.importHasALVS.required")
    }

    "validate export freightType" in {

      forAll(exportFreightTypeGen, stringGen) { (freightType, invalidInput) =>
        exportFreightTypeMapping.bind(Map("" -> freightType.toString)) shouldBe Right(freightType)
        exportFreightTypeMapping.bind(Map("" -> invalidInput)) should haveOnlyError[ExportFreightType](
          "error.exportFreightType.invalid-option"
        )
      }

      exportFreightTypeMapping.bind(Map()) should haveOnlyError[ExportFreightType]("error.exportFreightType.required")
    }

    "validate import freightType" in {

      forAll(importFreightTypeGen, stringGen) { (freightType, invalidInput) =>
        importFreightTypeMapping.bind(Map("" -> freightType.toString)) shouldBe Right(freightType)
        importFreightTypeMapping.bind(Map("" -> invalidInput)) should haveOnlyError[ImportFreightType](
          "error.importFreightType.invalid-option"
        )
      }

      importFreightTypeMapping.bind(Map()) should haveOnlyError[ImportFreightType]("error.importFreightType.required")
    }

    "validate mandatory vesselName" in {

      forAll(
        stringGen,
        allowedSpecialCharGen,
        invalidSpecialCharGen
      ) { (input, allowedSpecialChar, invalidSpecialChar) =>
        val inputWithSpecialChar = input + allowedSpecialChar

        mandatoryVesselNameMapping.bind(Map("" -> input)) shouldBe Right(Some(input.trim))

        mandatoryVesselNameMapping.bind(Map("" -> inputWithSpecialChar)) shouldBe Right(
          Some(inputWithSpecialChar.trim)
        )
        mandatoryVesselNameMapping.bind(Map("" -> (input + invalidSpecialChar))) should haveOnlyError[Option[String]](
          "error.vesselName.invalid-characters"
        )
      }

      mandatoryVesselNameMapping.bind(Map("" -> textGreaterThan(128))) should haveOnlyError[Option[String]](
        "error.vesselName.invalid-length"
      )

      mandatoryVesselNameMapping.bind(Map("" -> "")) should haveOnlyError[Option[String]](
        "error.vesselName.required"
      )
    }

    "validate optional vesselName" in {

      forAll(
        stringGen,
        allowedSpecialCharGen,
        invalidSpecialCharGen
      ) { (input, allowedSpecialChar, invalidSpecialChar) =>
        val inputWithSpecialChar = input + allowedSpecialChar

        optionalVesselNameMapping.bind(Map("" -> input)) shouldBe Right(Some(input.trim))

        optionalVesselNameMapping.bind(Map("" -> inputWithSpecialChar)) shouldBe Right(
          Some(inputWithSpecialChar.trim)
        )
        optionalVesselNameMapping.bind(Map("" -> (input + invalidSpecialChar))) should haveOnlyError[Option[String]](
          "error.vesselName.invalid-characters"
        )
      }

      optionalVesselNameMapping.bind(Map("" -> textGreaterThan(128))) should haveOnlyError[Option[String]](
        "error.vesselName.invalid-length"
      )

      optionalVesselNameMapping.bind(Map("" -> "")) shouldBe Right(None)
    }

    "validate mandatory dateOfArrival" in {

      forAll(pastDateGen) { date =>
        mandatoryDateOfArrivalMapping.bind(
          Map(
            "year"  -> date.getYear.toString,
            "month" -> date.getMonthValue.toString,
            "day"   -> date.getDayOfMonth.toString
          )
        ) shouldBe Right(Some(date))

        mandatoryDateOfArrivalMapping
          .bind(
            Map("year" -> "", "month" -> date.getMonth.toString, "day" -> date.getDayOfMonth.toString)
          ) should haveOnlyError(
          "error.dateOfArrival.year.required"
        )
        mandatoryDateOfArrivalMapping
          .bind(Map("year" -> "", "month" -> "", "day" -> date.getDayOfMonth.toString)) should haveOnlyError(
          "error.dateOfArrival.month.required"
        )
        mandatoryDateOfArrivalMapping
          .bind(Map("year" -> date.getYear.toString, "month" -> "", "day" -> "")) should haveOnlyError(
          "error.dateOfArrival.day.required"
        )
      }

      mandatoryDateOfArrivalMapping.bind(Map()) should haveOnlyError[Option[LocalDate]](
        "error.dateOfArrival.all.required"
      )
      mandatoryDateOfArrivalMapping
        .bind(Map("year" -> "", "month" -> "", "day" -> "")) should haveOnlyError[Option[LocalDate]](
        "error.dateOfArrival.all.required"
      )
    }

    "validate optional dateOfArrival" in {

      forAll(pastDateGen) { date =>
        optionalDateOfArrivalMapping.bind(
          Map(
            "year"  -> date.getYear.toString,
            "month" -> date.getMonthValue.toString,
            "day"   -> date.getDayOfMonth.toString
          )
        ) shouldBe Right(Some(date))

        optionalDateOfArrivalMapping
          .bind(
            Map("year" -> "", "month" -> date.getMonth.toString, "day" -> date.getDayOfMonth.toString)
          ) should haveOnlyError(
          "error.dateOfArrival.year.required"
        )
        optionalDateOfArrivalMapping
          .bind(Map("year" -> "", "month" -> "", "day" -> date.getDayOfMonth.toString)) should haveOnlyError(
          "error.dateOfArrival.month.required"
        )
        optionalDateOfArrivalMapping
          .bind(Map("year" -> date.getYear.toString, "month" -> "", "day" -> "")) should haveOnlyError(
          "error.dateOfArrival.day.required"
        )
      }

      optionalDateOfArrivalMapping.bind(Map()) shouldBe Right(None)
      optionalDateOfArrivalMapping
        .bind(Map("year" -> "", "month" -> "", "day" -> "")) shouldBe Right(None)
    }

    "validate mandatory timeOfArrival" in {

      forAll(hourGen, minutesGen) { (hours, minutes) =>
        val formattedHours = f"$hours%02d"
        val formattedMinutes = f"$minutes%02d"
        mandatoryTimeOfArrivalMapping.bind(Map("hour" -> formattedHours, "minutes" -> formattedMinutes)) shouldBe Right(
          Some(LocalTime.parse(s"$formattedHours:$formattedMinutes"))
        )

        mandatoryTimeOfArrivalMapping.bind(Map("hour" -> formattedHours, "minutes" -> "")) should haveOnlyError(
          "error.timeOfArrival.minutes.required"
        )
        mandatoryTimeOfArrivalMapping.bind(Map("hour" -> "", "minutes" -> formattedMinutes)) should haveOnlyError(
          "error.timeOfArrival.hour.required"
        )
      }

      mandatoryTimeOfArrivalMapping.bind(Map("hour" -> "", "minutes" -> "")) should haveOnlyError(
        "error.timeOfArrival.all.required"
      )
      mandatoryTimeOfArrivalMapping.bind(Map()) should haveOnlyError(
        "error.timeOfArrival.all.required"
      )
    }

    "validate optional timeOfArrival" in {

      forAll(hourGen, minutesGen) { (hours, minutes) =>
        val formattedHours = f"$hours%02d"
        val formattedMinutes = f"$minutes%02d"
        optionalTimeOfArrivalMapping.bind(Map("hour" -> formattedHours, "minutes" -> formattedMinutes)) shouldBe Right(
          Some(LocalTime.parse(s"$formattedHours:$formattedMinutes"))
        )

        optionalTimeOfArrivalMapping.bind(Map("hour" -> formattedHours, "minutes" -> "")) should haveOnlyError(
          "error.timeOfArrival.minutes.required"
        )
        optionalTimeOfArrivalMapping.bind(Map("hour" -> "", "minutes" -> formattedMinutes)) should haveOnlyError(
          "error.timeOfArrival.hour.required"
        )
      }

      optionalTimeOfArrivalMapping.bind(Map("hour" -> "", "minutes" -> " ")) shouldBe Right(None)
      optionalTimeOfArrivalMapping.bind(Map()) shouldBe Right(None)
    }

    "validate mandatory dateOfDeparture" in {

      forAll(pastDateGen) { date =>
        mandatoryDateOfDepartureMapping.bind(
          Map(
            "year"  -> date.getYear.toString,
            "month" -> date.getMonthValue.toString,
            "day"   -> date.getDayOfMonth.toString
          )
        ) shouldBe Right(Some(date))

        mandatoryDateOfDepartureMapping
          .bind(
            Map("year" -> "", "month" -> date.getMonth.toString, "day" -> date.getDayOfMonth.toString)
          ) should haveOnlyError(
          "error.dateOfDeparture.year.required"
        )
        mandatoryDateOfDepartureMapping
          .bind(Map("year" -> "", "month" -> "", "day" -> date.getDayOfMonth.toString)) should haveOnlyError(
          "error.dateOfDeparture.month.required"
        )
        mandatoryDateOfDepartureMapping
          .bind(Map("year" -> date.getYear.toString, "month" -> "", "day" -> "")) should haveOnlyError(
          "error.dateOfDeparture.day.required"
        )
      }

      mandatoryDateOfDepartureMapping.bind(Map()) should haveOnlyError[Option[LocalDate]](
        "error.dateOfDeparture.all.required"
      )
      mandatoryDateOfDepartureMapping
        .bind(Map("year" -> "", "month" -> "", "day" -> "")) should haveOnlyError[Option[LocalDate]](
        "error.dateOfDeparture.all.required"
      )
    }

    "validate optional dateOfDeparture" in {

      forAll(pastDateGen) { date =>
        optionalDateOfDepartureMapping.bind(
          Map(
            "year"  -> date.getYear.toString,
            "month" -> date.getMonthValue.toString,
            "day"   -> date.getDayOfMonth.toString
          )
        ) shouldBe Right(Some(date))

        optionalDateOfDepartureMapping
          .bind(
            Map("year" -> "", "month" -> date.getMonth.toString, "day" -> date.getDayOfMonth.toString)
          ) should haveOnlyError(
          "error.dateOfDeparture.year.required"
        )
        optionalDateOfDepartureMapping
          .bind(Map("year" -> "", "month" -> "", "day" -> date.getDayOfMonth.toString)) should haveOnlyError(
          "error.dateOfDeparture.month.required"
        )
        optionalDateOfDepartureMapping
          .bind(Map("year" -> date.getYear.toString, "month" -> "", "day" -> "")) should haveOnlyError(
          "error.dateOfDeparture.day.required"
        )
      }

      optionalDateOfDepartureMapping.bind(Map()) shouldBe Right(None)
      optionalDateOfDepartureMapping
        .bind(Map("year" -> "", "month" -> "", "day" -> "")) shouldBe Right(None)
      optionalDateOfDepartureMapping.bind(Map("year" -> "", "day" -> "")) shouldBe Right(None)
    }

    "validate mandatory timeOfDeparture" in {

      forAll(hourGen, minutesGen) { (hours, minutes) =>
        val formattedHours = f"$hours%02d"
        val formattedMinutes = f"$minutes%02d"
        mandatoryTimeOfDepartureMapping.bind(
          Map("hour" -> formattedHours, "minutes" -> formattedMinutes)
        ) shouldBe Right(
          Some(LocalTime.parse(s"$formattedHours:$formattedMinutes"))
        )

        mandatoryTimeOfDepartureMapping.bind(Map("hour" -> formattedHours, "minutes" -> "")) should haveOnlyError(
          "error.timeOfDeparture.minutes.required"
        )
        mandatoryTimeOfDepartureMapping.bind(Map("hour" -> "", "minutes" -> formattedMinutes)) should haveOnlyError(
          "error.timeOfDeparture.hour.required"
        )
      }

      mandatoryTimeOfDepartureMapping.bind(Map("hour" -> "", "minutes" -> "")) should haveOnlyError(
        "error.timeOfDeparture.all.required"
      )
      mandatoryTimeOfDepartureMapping.bind(Map()) should haveOnlyError(
        "error.timeOfDeparture.all.required"
      )
    }

    "validate optional timeOfDeparture" in {

      forAll(hourGen, minutesGen) { (hours, minutes) =>
        val formattedHours = f"$hours%02d"
        val formattedMinutes = f"$minutes%02d"
        optionalTimeOfDepartureMapping.bind(
          Map("hour" -> formattedHours, "minutes" -> formattedMinutes)
        ) shouldBe Right(
          Some(LocalTime.parse(s"$formattedHours:$formattedMinutes"))
        )

        optionalTimeOfDepartureMapping.bind(Map("hour" -> formattedHours, "minutes" -> "")) should haveOnlyError(
          "error.timeOfDeparture.minutes.required"
        )
        optionalTimeOfDepartureMapping.bind(Map("hour" -> "", "minutes" -> formattedMinutes)) should haveOnlyError(
          "error.timeOfDeparture.hour.required"
        )
      }

      optionalTimeOfDepartureMapping.bind(Map("hour" -> "", "minutes" -> "")) shouldBe Right(None)
      optionalTimeOfDepartureMapping.bind(Map()) shouldBe Right(None)
    }

    "validate import contactNameMapping" in {

      forAll(stringGen) { validInput =>
        whenever(validInput.length > 1) {
          importContactNameMapping.bind(Map("" -> validInput)) shouldBe Right(Some(validInput))
        }
      }

      importContactNameMapping.bind(Map("" -> textGreaterThan(128))) should haveOnlyError(
        "error.contactName.invalid-length-long"
      )
      importContactNameMapping.bind(Map("" -> "a")) should haveOnlyError("error.contactName.invalid-length-short")
      importContactNameMapping.bind(Map("" -> "")) shouldBe Right(None)
    }

    "validate export contactNameMapping" in {

      forAll(stringGen.suchThat(_.nonEmpty)) { validInput =>
        whenever(validInput.length > 1) {
          exportContactNameMapping.bind(Map("" -> validInput)) shouldBe Right(Some(validInput))
        }
      }

      exportContactNameMapping.bind(Map("" -> textGreaterThan(128))) should haveOnlyError(
        "error.contactName.invalid-length-long"
      )
      exportContactNameMapping.bind(Map("" -> "a")) should haveOnlyError("error.contactName.invalid-length-short")

      exportContactNameMapping.bind(Map("" -> "")) shouldBe Right(None)
    }

    "validate import contactEmailMapping" in {

      forAll(stringGen) { invalidEmail =>
        importContactEmailMapping.bind(Map("" -> invalidEmail)) should haveOnlyError(
          "error.contactEmail"
        )
      }

      importContactEmailMapping.bind(Map("" -> "test@example.com")) shouldBe Right("test@example.com")

      importContactEmailMapping.bind(Map("" -> "")) should haveOnlyError(
        "error.contactEmail.required"
      )
    }

    "validate export contactEmailMapping" in {

      forAll(stringGen) { invalidEmail =>
        exportContactEmailMapping.bind(Map("" -> invalidEmail)) should haveOnlyError(
          "error.contactEmail"
        )
      }

      exportContactEmailMapping.bind(Map("" -> "test@example.com")) shouldBe Right("test@example.com")

      exportContactEmailMapping.bind(Map("" -> "")) should haveOnlyError(
        "error.contactEmail.required"
      )
    }

    "validate import contactNumberMapping" in {

      forAll(invalidPhoneNumberGen) { invalidNumber =>
        importContactNumberMapping.bind(Map("" -> textGreaterThan(10))) should haveOnlyError(
          "error.contactNumber"
        )
        importContactNumberMapping.bind(Map("" -> invalidNumber)) should haveOnlyError(
          "error.contactNumber"
        )
      }

      importContactNumberMapping.bind(Map("" -> "07894561232")) shouldBe Right(Some("07894561232"))
      importContactNumberMapping.bind(Map("" -> "+44113 2432111")) shouldBe Right(Some("01132432111"))
      importContactNumberMapping.bind(Map("" -> "4411-3243-2111")) shouldBe Right(Some("01132432111"))
      importContactNumberMapping.bind(Map("" -> "(0044)1132432111")) shouldBe Right(Some("01132432111"))

      importContactNumberMapping.bind(Map("" -> "")) shouldBe Right(None)
    }

    "validate export contactNumberMapping" in {

      forAll(invalidPhoneNumberGen) { invalidNumber =>
        exportContactNumberMapping.bind(Map("" -> textGreaterThan(10))) should haveOnlyError(
          "error.contactNumber"
        )
        exportContactNumberMapping.bind(Map("" -> invalidNumber)) should haveOnlyError(
          "error.contactNumber"
        )
      }

      exportContactNumberMapping.bind(Map("" -> "07894561232")) shouldBe Right(Some("07894561232"))
      exportContactNumberMapping.bind(Map("" -> "+44113 2432111")) shouldBe Right(Some("01132432111"))
      exportContactNumberMapping.bind(Map("" -> "4411-3243-2111")) shouldBe Right(Some("01132432111"))
      exportContactNumberMapping.bind(Map("" -> "(0044)1132432111")) shouldBe Right(Some("01132432111"))

      exportContactNumberMapping.bind(Map("" -> "")) shouldBe Right(None)
    }

    "validate case reference number mapping" in {

      forAll(Gen.alphaNumStr.suchThat(str => str.nonEmpty && str.length != 22)) { invalidRef =>
        caseReferenceNumberMapping.bind(Map("" -> invalidRef)) should haveOnlyError(
          "error.caseReferenceNumber.invalid-value"
        )
      }

      caseReferenceNumberMapping.bind(Map("" -> "AA0000000000000000000Z")) shouldBe Right("AA0000000000000000000Z")
      caseReferenceNumberMapping.bind(Map("" -> "")) should haveOnlyError("error.caseReferenceNumber.required")
    }

    "validate response text mapping" in {

      forAll(stringGen) { input =>
        responseTextMapping.bind(Map("" -> input)) shouldBe Right(input)
      }

      responseTextMapping.bind(Map("" -> textGreaterThan(1000))) should haveOnlyError(
        "error.responseText.invalid-length"
      )
      responseTextMapping.bind(Map("" -> "")) should haveOnlyError("error.responseText.required")
    }

    "validate all constraints" in {

      forAll(stringGen) { input =>
        FormFieldMappings.all(FormFieldMappings.nonEmpty("foo")).apply(input) shouldBe Valid
      }

      FormFieldMappings.all(FormFieldMappings.nonEmpty("foo")).apply("") shouldBe a[Invalid]
      FormFieldMappings
        .all(FormFieldMappings.nonEmpty("foo1"), FormFieldMappings.nonEmpty("foo2"))
        .apply("") shouldBe a[Invalid]
    }
  }

}
