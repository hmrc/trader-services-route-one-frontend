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
//import play.api.data.FormError
//import uk.gov.hmrc.traderservices.support.UnitSpec
//import uk.gov.hmrc.traderservices.models.VesselDetails
//import uk.gov.hmrc.traderservices.support.FormMatchers
//import java.time.LocalDateTime
//import java.time.temporal.ChronoField
//import java.time.temporal.ChronoUnit
//import uk.gov.hmrc.traderservices.models.ExportRequestType._
//class VesselDetailsFormSpec extends UnitSpec with FormMatchers {
//
//  val dateTime = LocalDateTime.now().plusHours(1)
//
//  val formOutput = VesselDetails(
//    vesselName = Some("Foo Bar"),
//    dateOfArrival = Some(dateTime.toLocalDate()),
//    timeOfArrival = Some(dateTime.toLocalTime().truncatedTo(ChronoUnit.MINUTES))
//  )
//
//  def importFormInputFor(dateTime: LocalDateTime) =
//    Map(
//      "vesselName"            -> "Foo Bar",
//      "dateOfArrival.year"    -> f"${dateTime.get(ChronoField.YEAR)}",
//      "dateOfArrival.month"   -> f"${dateTime.get(ChronoField.MONTH_OF_YEAR)}%02d",
//      "dateOfArrival.day"     -> f"${dateTime.get(ChronoField.DAY_OF_MONTH)}%02d",
//      "timeOfArrival.hour"    -> f"${dateTime.get(ChronoField.HOUR_OF_DAY)}%02d",
//      "timeOfArrival.minutes" -> f"${dateTime.get(ChronoField.MINUTE_OF_HOUR)}%02d"
//    )
//
//  def exportFormInputFor(dateTime: LocalDateTime) =
//    Map(
//      "vesselName"              -> "Foo Bar",
//      "dateOfDeparture.year"    -> f"${dateTime.get(ChronoField.YEAR)}",
//      "dateOfDeparture.month"   -> f"${dateTime.get(ChronoField.MONTH_OF_YEAR)}%02d",
//      "dateOfDeparture.day"     -> f"${dateTime.get(ChronoField.DAY_OF_MONTH)}%02d",
//      "timeOfDeparture.hour"    -> f"${dateTime.get(ChronoField.HOUR_OF_DAY)}%02d",
//      "timeOfDeparture.minutes" -> f"${dateTime.get(ChronoField.MINUTE_OF_HOUR)}%02d"
//    )
//
//  def exportFormInputForArrival(dateTime: LocalDateTime) =
//    Map(
//      "vesselName"            -> "Foo Bar",
//      "dateOfArrival.year"    -> f"${dateTime.get(ChronoField.YEAR)}",
//      "dateOfArrival.month"   -> f"${dateTime.get(ChronoField.MONTH_OF_YEAR)}%02d",
//      "dateOfArrival.day"     -> f"${dateTime.get(ChronoField.DAY_OF_MONTH)}%02d",
//      "timeOfArrival.hour"    -> f"${dateTime.get(ChronoField.HOUR_OF_DAY)}%02d",
//      "timeOfArrival.minutes" -> f"${dateTime.get(ChronoField.MINUTE_OF_HOUR)}%02d"
//    )
//
//  def formOutputFor(dateTime: LocalDateTime) =
//    VesselDetails(
//      vesselName = Some("Foo Bar"),
//      dateOfArrival = Some(dateTime.toLocalDate()),
//      timeOfArrival = Some(dateTime.toLocalTime().truncatedTo(ChronoUnit.MINUTES))
//    )
//
//  val importFormInput = importFormInputFor(dateTime)
//  val exportFormInput = exportFormInputFor(dateTime)
//  val exportArrivalFormInput = exportFormInputForArrival(dateTime)
//
//  "MandatoryImportVesselDetailsForm" should {
//
//    val form = CreateCaseJourneyController.MandatoryImportVesselDetailsForm
//
//    "bind some input fields and return VesselDetails and fill it back" in {
//      form.bind(importFormInput).value shouldBe Some(formOutput)
//      form.fill(formOutput).data shouldBe importFormInput
//    }
//
//    "report an error when vesselName is missing" in {
//      val input = importFormInput.updated("vesselName", "")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(FormError("vesselName", "error.vesselName.required"))
//    }
//
//    "report an error when vesselName is invalid" in {
//      val input = importFormInput.updated("vesselName", "$$$")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyErrors(
//        FormError("vesselName", "error.vesselName.invalid-characters")
//      )
//    }
//
//    "report an error when dateOfArrival is missing" in {
//      val input =
//        importFormInput
//          .updated("dateOfArrival.year", "")
//          .updated("dateOfArrival.month", "")
//          .updated("dateOfArrival.day", "")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("dateOfArrival", Seq("subfieldFocus=day", "error.dateOfArrival.all.required"))
//      )
//    }
//
//    "report an error when dateOfArrival is partially missing" in {
//      val input = importFormInput
//        .updated("dateOfArrival.year", "")
//        .updated("dateOfArrival.month", "")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("dateOfArrival", Seq("subfieldFocus=month", "error.dateOfArrival.month.required"))
//      )
//    }
//
//    "report an error when dateOfArrival is invalid" in {
//      val input = importFormInput
//        .updated("dateOfArrival.year", "202a")
//        .updated("dateOfArrival.month", "13")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("dateOfArrival", Seq("subfieldFocus=month", "error.dateOfArrival.all.invalid-value"))
//      )
//    }
//
//    "report an error when timeOfArrival is missing" in {
//      val input = importFormInput
//        .updated("timeOfArrival.hour", "")
//        .updated("timeOfArrival.minutes", "")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("timeOfArrival", Seq("subfieldFocus=hour", "error.timeOfArrival.all.required"))
//      )
//    }
//
//    "report an error when timeOfArrival is invalid" in {
//      val input = importFormInput
//        .updated("timeOfArrival.hour", "25")
//        .updated("timeOfArrival.minutes", "60")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("timeOfArrival", Seq("subfieldFocus=hour", "error.timeOfArrival.hour.invalid-value"))
//      )
//    }
//
//    "report an error when dateOfArrival is more than 6 months in the past" in {
//      val input = importFormInputFor(dateTime.minusMonths(6).minusDays(2))
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("dateOfArrival", Seq("subfieldFocus=day", "error.dateOfArrival.all.invalid-value-range"))
//      )
//    }
//
//    "report an error when dateOfArrival is more than 6 months in the future" in {
//      val input = importFormInputFor(dateTime.plusMonths(6).plusDays(1))
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyErrors(
//        FormError("dateOfArrival", Seq("subfieldFocus=day", "error.dateOfArrival.all.invalid-value-range"))
//      )
//    }
//
//    "bind input when dateOfArrival is after provided entry date" in {
//      val form = CreateCaseJourneyController.mandatoryImportVesselDetailsForm(Some(dateTime.minusDays(1).toLocalDate))
//      val input = importFormInputFor(dateTime)
//      val output = formOutputFor(dateTime)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe importFormInput
//    }
//
//    "bind input when dateOfArrival is equal to provided entry date" in {
//      val form = CreateCaseJourneyController.mandatoryImportVesselDetailsForm(Some(dateTime.toLocalDate))
//      val input = importFormInputFor(dateTime)
//      val output = formOutputFor(dateTime)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe importFormInput
//    }
//
//    "report an error when dateOfArrival is before provided entry date" in {
//      val form = CreateCaseJourneyController.mandatoryImportVesselDetailsForm(Some(dateTime.plusDays(1).toLocalDate))
//      val input = importFormInputFor(dateTime)
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyErrors(
//        FormError("dateOfArrival", Seq("subfieldFocus=day", "error.dateOfArrival.all.invalid-value-before-entry-date"))
//      )
//    }
//  }
//
//  "OptionalImportVesselDetailsForm" should {
//
//    val form = CreateCaseJourneyController.OptionalImportVesselDetailsForm
//
//    "bind some input fields and return VesselDetails and fill it back" in {
//      form.bind(importFormInput).value shouldBe Some(formOutput)
//      form.fill(formOutput).data shouldBe importFormInput
//    }
//
//    "return VesselDetails despite missing vesselName" in {
//      val input = importFormInput.-("vesselName")
//      val output = formOutput.copy(vesselName = None)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe input
//    }
//
//    "report an error when vesselName is invalid" in {
//      val input = importFormInput.updated("vesselName", "$$$")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyErrors(
//        FormError("vesselName", "error.vesselName.invalid-characters")
//      )
//    }
//
//    "return VesselDetails despite missing dateOfArrival" in {
//      val input =
//        importFormInput
//          .updated("dateOfArrival.year", "")
//          .updated("dateOfArrival.month", "")
//          .updated("dateOfArrival.day", "")
//      val output = formOutput.copy(dateOfArrival = None)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe input
//    }
//
//    "report an error when dateOfArrival is partially missing" in {
//      val input = importFormInput
//        .updated("dateOfArrival.year", "")
//        .updated("dateOfArrival.month", "")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("dateOfArrival", Seq("subfieldFocus=month", "error.dateOfArrival.month.required"))
//      )
//    }
//
//    "report an error when dateOfArrival is invalid" in {
//      val input = importFormInput
//        .updated("dateOfArrival.year", "202a")
//        .updated("dateOfArrival.month", "13")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("dateOfArrival", Seq("subfieldFocus=month", "error.dateOfArrival.all.invalid-value"))
//      )
//    }
//
//    "return VesselDetails despite missing timeOfArrival" in {
//      val input = importFormInput
//        .updated("timeOfArrival.hour", "")
//        .updated("timeOfArrival.minutes", "")
//      val output = formOutput.copy(timeOfArrival = None)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe input
//    }
//
//    "report an error when timeOfArrival is partially missing" in {
//      val input = importFormInput
//        .updated("timeOfArrival.hour", "")
//        .updated("timeOfArrival.minutes", "")
//      val output = formOutput.copy(timeOfArrival = None)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe input
//    }
//
//    "report an error when timeOfArrival is invalid" in {
//      val input = importFormInput
//        .updated("timeOfArrival.hour", "25")
//        .updated("timeOfArrival.minutes", "60")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyErrors(
//        FormError("timeOfArrival", Seq("subfieldFocus=hour", "error.timeOfArrival.hour.invalid-value"))
//      )
//    }
//
//    "report an error when dateOfArrival is more than 6 months in the past" in {
//      val input = importFormInputFor(dateTime.minusMonths(6).minusDays(2))
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyErrors(
//        FormError("dateOfArrival", Seq("subfieldFocus=day", "error.dateOfArrival.all.invalid-value-range"))
//      )
//    }
//
//    "report an error when dateOfArrival is more than 6 months in the future" in {
//      val input = importFormInputFor(dateTime.plusMonths(6).plusDays(1))
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyErrors(
//        FormError("dateOfArrival", Seq("subfieldFocus=day", "error.dateOfArrival.all.invalid-value-range"))
//      )
//    }
//
//    "bind input when dateOfArrival is after provided entry date" in {
//      val form = CreateCaseJourneyController.optionalImportVesselDetailsForm(Some(dateTime.minusDays(1).toLocalDate))
//      val input = importFormInputFor(dateTime)
//      val output = formOutputFor(dateTime)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe importFormInput
//    }
//
//    "bind input when dateOfArrival is equal to provided entry date" in {
//      val form = CreateCaseJourneyController.optionalImportVesselDetailsForm(Some(dateTime.toLocalDate))
//      val input = importFormInputFor(dateTime)
//      val output = formOutputFor(dateTime)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe importFormInput
//    }
//
//    "report an error when dateOfArrival is before provided entry date" in {
//      val form = CreateCaseJourneyController.optionalImportVesselDetailsForm(Some(dateTime.plusDays(1).toLocalDate))
//      val input = importFormInputFor(dateTime)
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyErrors(
//        FormError("dateOfArrival", Seq("subfieldFocus=day", "error.dateOfArrival.all.invalid-value-before-entry-date"))
//      )
//    }
//  }
//
//  "MandatoryExportVesselDetailsForm" should {
//
//    val form = CreateCaseJourneyController.MandatoryExportVesselDetailsForm
//
//    "bind some input fields and return VesselDetails and fill it back" in {
//      form.bind(exportFormInput).value shouldBe Some(formOutput)
//      form.fill(formOutput).data shouldBe exportFormInput
//    }
//
//    "report an error when vesselName is missing" in {
//      val input = exportFormInput.updated("vesselName", "")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(FormError("vesselName", "error.vesselName.required"))
//    }
//
//    "report an error when vesselName is invalid" in {
//      val input = exportFormInput.updated("vesselName", "$$$")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyErrors(
//        FormError("vesselName", "error.vesselName.invalid-characters")
//      )
//    }
//
//    "report an error when dateOfDeparture is missing" in {
//      val input =
//        exportFormInput
//          .updated("dateOfDeparture.year", "")
//          .updated("dateOfDeparture.month", "")
//          .updated("dateOfDeparture.day", "")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("dateOfDeparture", Seq("subfieldFocus=day", "error.dateOfDeparture.all.required"))
//      )
//    }
//
//    "report an error when dateOfDeparture is partially missing" in {
//      val input = exportFormInput
//        .updated("dateOfDeparture.year", "")
//        .updated("dateOfDeparture.month", "")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("dateOfDeparture", Seq("subfieldFocus=month", "error.dateOfDeparture.month.required"))
//      )
//    }
//
//    "report an error when dateOfDeparture is invalid" in {
//      val input = exportFormInput
//        .updated("dateOfDeparture.year", "202a")
//        .updated("dateOfDeparture.month", "13")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("dateOfDeparture", Seq("subfieldFocus=month", "error.dateOfDeparture.all.invalid-value"))
//      )
//    }
//
//    "report an error when timeOfDeparture is missing" in {
//      val input = exportFormInput
//        .updated("timeOfDeparture.hour", "")
//        .updated("timeOfDeparture.minutes", "")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("timeOfDeparture", Seq("subfieldFocus=hour", "error.timeOfDeparture.all.required"))
//      )
//    }
//
//    "report an error when timeOfDeparture is invalid" in {
//      val input = exportFormInput
//        .updated("timeOfDeparture.hour", "25")
//        .updated("timeOfDeparture.minutes", "60")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("timeOfDeparture", Seq("subfieldFocus=hour", "error.timeOfDeparture.hour.invalid-value"))
//      )
//    }
//
//    "report an error when dateOfDeparture is more than 6 months in the past" in {
//      val input = exportFormInputFor(dateTime.minusMonths(6).minusDays(2))
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("dateOfDeparture", Seq("subfieldFocus=day", "error.dateOfDeparture.all.invalid-value-range"))
//      )
//    }
//
//    "report an error when dateOfDeparture is more than 6 months in the future" in {
//      val input = exportFormInputFor(dateTime.plusMonths(6).plusDays(1))
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyErrors(
//        FormError("dateOfDeparture", Seq("subfieldFocus=day", "error.dateOfDeparture.all.invalid-value-range"))
//      )
//    }
//
//    "bind input when dateOfDeparture is after provided entry date" in {
//      val form = CreateCaseJourneyController.mandatoryExportVesselDetailsForm(
//        Some(dateTime.minusDays(1).toLocalDate),
//        Some(C1602)
//      )
//      val input = exportFormInputFor(dateTime)
//      val output = formOutputFor(dateTime)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe exportFormInput
//    }
//
//    "bind input when dateOfDeparture is equal to provided entry date" in {
//      val form = CreateCaseJourneyController.mandatoryExportVesselDetailsForm(Some(dateTime.toLocalDate), Some(C1602))
//      val input = exportFormInputFor(dateTime)
//      val output = formOutputFor(dateTime)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe exportFormInput
//    }
//
//    "report an error when dateOfDeparture is before provided entry date" in {
//      val form = CreateCaseJourneyController.mandatoryExportVesselDetailsForm(
//        Some(dateTime.plusDays(1).toLocalDate),
//        Some(C1602)
//      )
//      val input = exportFormInputFor(dateTime)
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyErrors(
//        FormError(
//          "dateOfDeparture",
//          Seq("subfieldFocus=day", "error.dateOfDeparture.all.invalid-value-before-entry-date")
//        )
//      )
//    }
//  }
//  "MandatoryExportVesselDetailsForm for C1601 (Arrival)" should {
//
//    val form = CreateCaseJourneyController.mandatoryExportVesselDetailsForm(None, Some(C1601))
//
//    "report an error when dateOfArrival is missing" in {
//      val input =
//        exportArrivalFormInput
//          .updated("dateOfArrival.year", "")
//          .updated("dateOfArrival.month", "")
//          .updated("dateOfArrival.day", "")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("dateOfArrival", Seq("subfieldFocus=day", "error.dateOfArrival.all.required"))
//      )
//    }
//
//    "report an error when dateOfArrival is partially missing" in {
//      val input = exportArrivalFormInput
//        .updated("dateOfArrival.year", "")
//        .updated("dateOfArrival.month", "")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("dateOfArrival", Seq("subfieldFocus=month", "error.dateOfArrival.month.required"))
//      )
//    }
//
//    "report an error when dateOfArrival is invalid" in {
//      val input = exportArrivalFormInput
//        .updated("dateOfArrival.year", "202a")
//        .updated("dateOfArrival.month", "13")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("dateOfArrival", Seq("subfieldFocus=month", "error.dateOfArrival.all.invalid-value"))
//      )
//    }
//
//    "report an error when timeOfArrival is missing" in {
//      val input = exportArrivalFormInput
//        .updated("timeOfArrival.hour", "")
//        .updated("timeOfArrival.minutes", "")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("timeOfArrival", Seq("subfieldFocus=hour", "error.timeOfArrival.all.required"))
//      )
//    }
//
//    "report an error when timeOfArrival is invalid" in {
//      val input = exportArrivalFormInput
//        .updated("timeOfArrival.hour", "25")
//        .updated("timeOfArrival.minutes", "60")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("timeOfArrival", Seq("subfieldFocus=hour", "error.timeOfArrival.hour.invalid-value"))
//      )
//    }
//
//    "report an error when dateOfArrival is more than 6 months in the past" in {
//      val input = exportFormInputForArrival(dateTime.minusMonths(6).minusDays(2))
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("dateOfArrival", Seq("subfieldFocus=day", "error.dateOfArrival.all.invalid-value-range"))
//      )
//    }
//
//    "report an error when dateOfArrival is more than 6 months in the future" in {
//      val input = exportFormInputForArrival(dateTime.plusMonths(6).plusDays(1))
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyErrors(
//        FormError("dateOfArrival", Seq("subfieldFocus=day", "error.dateOfArrival.all.invalid-value-range"))
//      )
//    }
//
//    "bind input when dateOfArrival is after provided entry date" in {
//      val form = CreateCaseJourneyController.mandatoryExportVesselDetailsForm(
//        Some(dateTime.minusDays(1).toLocalDate),
//        Some(C1601)
//      )
//      val input = exportFormInputForArrival(dateTime)
//      val output = formOutputFor(dateTime)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe exportArrivalFormInput
//    }
//
//    "bind input when dateOfArrival is equal to provided entry date" in {
//      val form = CreateCaseJourneyController.mandatoryExportVesselDetailsForm(Some(dateTime.toLocalDate), Some(C1601))
//      val input = exportFormInputForArrival(dateTime)
//      val output = formOutputFor(dateTime)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe exportArrivalFormInput
//    }
//
//    "report an error when dateOfArrival is before provided entry date" in {
//      val form = CreateCaseJourneyController.mandatoryExportVesselDetailsForm(
//        Some(dateTime.plusDays(1).toLocalDate),
//        Some(C1601)
//      )
//      val input = exportFormInputForArrival(dateTime)
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyErrors(
//        FormError(
//          "dateOfArrival",
//          Seq("subfieldFocus=day", "error.dateOfArrival.all.invalid-value-before-entry-date")
//        )
//      )
//    }
//  }
//
//  "OptionalExportVesselDetailsForm" should {
//
//    val form = CreateCaseJourneyController.OptionalExportVesselDetailsForm
//
//    "bind some input fields and return VesselDetails and fill it back" in {
//      form.bind(exportFormInput).value shouldBe Some(formOutput)
//      form.fill(formOutput).data shouldBe exportFormInput
//    }
//
//    "return VesselDetails despite missing vesselName" in {
//      val input = exportFormInput.-("vesselName")
//      val output = formOutput.copy(vesselName = None)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe input
//    }
//
//    "report an error when vesselName is invalid" in {
//      val input = exportFormInput.updated("vesselName", "$$$")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyErrors(
//        FormError("vesselName", "error.vesselName.invalid-characters")
//      )
//    }
//
//    "return VesselDetails despite missing dateOfDeparture" in {
//      val input =
//        exportFormInput
//          .updated("dateOfDeparture.year", "")
//          .updated("dateOfDeparture.month", "")
//          .updated("dateOfDeparture.day", "")
//      val output = formOutput.copy(dateOfArrival = None)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe input
//    }
//
//    "report an error when dateOfDeparture is partially missing" in {
//      val input = exportFormInput
//        .updated("dateOfDeparture.year", "")
//        .updated("dateOfDeparture.month", "")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("dateOfDeparture", Seq("subfieldFocus=month", "error.dateOfDeparture.month.required"))
//      )
//    }
//
//    "report an error when dateOfDeparture is invalid" in {
//      val input = exportFormInput
//        .updated("dateOfDeparture.year", "202a")
//        .updated("dateOfDeparture.month", "13")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("dateOfDeparture", Seq("subfieldFocus=month", "error.dateOfDeparture.all.invalid-value"))
//      )
//    }
//
//    "return VesselDetails despite missing timeOfDeparture" in {
//      val input = exportFormInput
//        .updated("timeOfDeparture.hour", "")
//        .updated("timeOfDeparture.minutes", "")
//      val output = formOutput.copy(timeOfArrival = None)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe input
//    }
//
//    "report an error when timeOfDeparture is partially missing" in {
//      val input = exportFormInput
//        .updated("timeOfDeparture.hour", "")
//        .updated("timeOfDeparture.minutes", "")
//      val output = formOutput.copy(timeOfArrival = None)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe input
//    }
//
//    "report an error when timeOfDeparture is invalid" in {
//      val input = exportFormInput
//        .updated("timeOfDeparture.hour", "25")
//        .updated("timeOfDeparture.minutes", "60")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyErrors(
//        FormError("timeOfDeparture", Seq("subfieldFocus=hour", "error.timeOfDeparture.hour.invalid-value"))
//      )
//    }
//
//    "report an error when dateOfDeparture is more than 6 months in the past" in {
//      val input = exportFormInputFor(dateTime.minusMonths(6).minusDays(2))
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyErrors(
//        FormError("dateOfDeparture", Seq("subfieldFocus=day", "error.dateOfDeparture.all.invalid-value-range"))
//      )
//    }
//
//    "report an error when dateOfDeparture is more than 6 months in the future" in {
//      val input = exportFormInputFor(dateTime.plusMonths(6).plusDays(1))
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyErrors(
//        FormError("dateOfDeparture", Seq("subfieldFocus=day", "error.dateOfDeparture.all.invalid-value-range"))
//      )
//    }
//
//    "bind input when dateOfDeparture is after provided entry date" in {
//      val form = CreateCaseJourneyController.optionalExportVesselDetailsForm(
//        Some(dateTime.minusDays(1).toLocalDate),
//        Some(C1602)
//      )
//      val input = exportFormInputFor(dateTime)
//      val output = formOutputFor(dateTime)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe exportFormInput
//    }
//
//    "bind input when dateOfDeparture is equal to provided entry date" in {
//      val form = CreateCaseJourneyController.optionalExportVesselDetailsForm(Some(dateTime.toLocalDate), Some(C1602))
//      val input = exportFormInputFor(dateTime)
//      val output = formOutputFor(dateTime)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe exportFormInput
//    }
//
//    "report an error when dateOfDeparture is before provided entry date" in {
//      val form =
//        CreateCaseJourneyController.optionalExportVesselDetailsForm(Some(dateTime.plusDays(1).toLocalDate), Some(C1602))
//      val input = exportFormInputFor(dateTime)
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyErrors(
//        FormError(
//          "dateOfDeparture",
//          Seq("subfieldFocus=day", "error.dateOfDeparture.all.invalid-value-before-entry-date")
//        )
//      )
//    }
//  }
//
//  "OptionalExportVesselDetailsForm for C1603 (arrival optional)" should {
//
//    val form = CreateCaseJourneyController.optionalExportVesselDetailsForm(None, Some(C1603))
//
//    "return VesselDetails despite missing dateOfArrival" in {
//      val input =
//        exportArrivalFormInput
//          .updated("dateOfArrival.year", "")
//          .updated("dateOfArrival.month", "")
//          .updated("dateOfArrival.day", "")
//      val output = formOutput.copy(dateOfArrival = None)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe input
//    }
//
//    "report an error when dateOfArrival is partially missing" in {
//      val input = exportArrivalFormInput
//        .updated("dateOfArrival.year", "")
//        .updated("dateOfArrival.month", "")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("dateOfArrival", Seq("subfieldFocus=month", "error.dateOfArrival.month.required"))
//      )
//    }
//
//    "report an error when dateOfArrival is invalid" in {
//      val input = exportArrivalFormInput
//        .updated("dateOfArrival.year", "202a")
//        .updated("dateOfArrival.month", "13")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyError(
//        FormError("dateOfArrival", Seq("subfieldFocus=month", "error.dateOfArrival.all.invalid-value"))
//      )
//    }
//
//    "return VesselDetails despite missing timeOfArrival" in {
//      val input = exportArrivalFormInput
//        .updated("timeOfArrival.hour", "")
//        .updated("timeOfArrival.minutes", "")
//      val output = formOutput.copy(timeOfArrival = None)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe input
//    }
//
//    "report an error when timeOfArrival is partially missing" in {
//      val input = exportArrivalFormInput
//        .updated("timeOfArrival.hour", "")
//        .updated("timeOfArrival.minutes", "")
//      val output = formOutput.copy(timeOfArrival = None)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe input
//    }
//
//    "report an error when timeOfArrival is invalid" in {
//      val input = exportArrivalFormInput
//        .updated("timeOfArrival.hour", "25")
//        .updated("timeOfArrival.minutes", "60")
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyErrors(
//        FormError("timeOfArrival", Seq("subfieldFocus=hour", "error.timeOfArrival.hour.invalid-value"))
//      )
//    }
//
//    "report an error when dateOfArrival is more than 6 months in the past" in {
//      val input = exportFormInputForArrival(dateTime.minusMonths(6).minusDays(2))
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyErrors(
//        FormError("dateOfArrival", Seq("subfieldFocus=day", "error.dateOfArrival.all.invalid-value-range"))
//      )
//    }
//
//    "report an error when dateOfArrival is more than 6 months in the future" in {
//      val input = exportFormInputForArrival(dateTime.plusMonths(6).plusDays(1))
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyErrors(
//        FormError("dateOfArrival", Seq("subfieldFocus=day", "error.dateOfArrival.all.invalid-value-range"))
//      )
//    }
//
//    "bind input when dateOfArrival is after provided entry date" in {
//      val form = CreateCaseJourneyController.optionalExportVesselDetailsForm(
//        Some(dateTime.minusDays(1).toLocalDate),
//        Some(C1603)
//      )
//      val input = exportFormInputForArrival(dateTime)
//      val output = formOutputFor(dateTime)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe exportArrivalFormInput
//    }
//
//    "bind input when dateOfArrival is equal to provided entry date" in {
//      val form = CreateCaseJourneyController.optionalExportVesselDetailsForm(Some(dateTime.toLocalDate), Some(C1603))
//      val input = exportFormInputForArrival(dateTime)
//      val output = formOutputFor(dateTime)
//      form.bind(input).value shouldBe Some(output)
//      form.fill(output).data shouldBe exportArrivalFormInput
//    }
//
//    "report an error when dateOfArrival is before provided entry date" in {
//      val form =
//        CreateCaseJourneyController.optionalExportVesselDetailsForm(Some(dateTime.plusDays(1).toLocalDate), Some(C1603))
//      val input = exportFormInputForArrival(dateTime)
//      form.bind(input).value shouldBe None
//      form.bind(input).errors should haveOnlyErrors(
//        FormError(
//          "dateOfArrival",
//          Seq("subfieldFocus=day", "error.dateOfArrival.all.invalid-value-before-entry-date")
//        )
//      )
//    }
//  }
//}
