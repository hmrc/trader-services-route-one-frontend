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

import play.api.data.FormError
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.traderservices.models.VesselDetails
import uk.gov.hmrc.traderservices.support.FormMatchers
import java.time.LocalDateTime
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit

class VesselDetailsFormSpec extends UnitSpec with FormMatchers {

  val dateTime = LocalDateTime.now().plusHours(1)

  val formOutput = VesselDetails(
    vesselName = Some("Foo Bar"),
    dateOfArrival = Some(dateTime.toLocalDate()),
    timeOfArrival = Some(dateTime.toLocalTime().truncatedTo(ChronoUnit.MINUTES))
  )

  def formInputFor(dateTime: LocalDateTime) =
    Map(
      "vesselName"            -> "Foo Bar",
      "dateOfArrival.year"    -> f"${dateTime.get(ChronoField.YEAR)}",
      "dateOfArrival.month"   -> f"${dateTime.get(ChronoField.MONTH_OF_YEAR)}%02d",
      "dateOfArrival.day"     -> f"${dateTime.get(ChronoField.DAY_OF_MONTH)}%02d",
      "timeOfArrival.hour"    -> f"${dateTime.get(ChronoField.CLOCK_HOUR_OF_AMPM)}%02d",
      "timeOfArrival.minutes" -> f"${dateTime.get(ChronoField.MINUTE_OF_HOUR)}%02d",
      "timeOfArrival.period"  -> { if (dateTime.get(ChronoField.AMPM_OF_DAY) == 0) "AM" else "PM" }
    )

  val formInput = formInputFor(dateTime)

  "MandatoryVesselDetailsForm" should {

    val form = TraderServicesFrontendController.MandatoryVesselDetailsForm

    "bind some input fields and return VesselDetails and fill it back" in {
      form.bind(formInput).value shouldBe Some(formOutput)
      form.fill(formOutput).data shouldBe formInput
    }

    "report an error when vesselName is missing" in {
      val input = formInput.updated("vesselName", "")
      form.bind(input).value shouldBe None
      form.bind(input).errors should haveOnlyError(FormError("vesselName", "error.vesselName.required"))
    }

    "report an error when vesselName is invalid" in {
      val input = formInput.updated("vesselName", "$$$")
      form.bind(input).value shouldBe None
      form.bind(input).errors should haveOnlyErrors(
        FormError("vesselName", "error.vesselName.invalid-characters")
      )
    }

    "report an error when dateOfArrival is missing" in {
      val input =
        formInput
          .updated("dateOfArrival.year", "")
          .updated("dateOfArrival.month", "")
          .updated("dateOfArrival.day", "")
      form.bind(input).value shouldBe None
      form.bind(input).errors should haveOnlyErrors(FormError("dateOfArrival", "error.dateOfArrival.all.required"))
    }

    "report an error when dateOfArrival is partially missing" in {
      val input = formInput
        .updated("dateOfArrival.year", "")
        .updated("dateOfArrival.month", "")
      form.bind(input).value shouldBe None
      form.bind(input).errors should haveOnlyErrors(
        FormError("dateOfArrival", "error.dateOfArrival.year.required"),
        FormError("dateOfArrival", "error.dateOfArrival.month.required")
      )
    }

    "report an error when dateOfArrival is invalid" in {
      val input = formInput
        .updated("dateOfArrival.year", "202a")
        .updated("dateOfArrival.month", "13")
      form.bind(input).value shouldBe None
      form.bind(input).errors should haveOnlyErrors(
        FormError("dateOfArrival", "error.dateOfArrival.year.invalid-digits"),
        FormError("dateOfArrival", "error.dateOfArrival.month.invalid-value")
      )
    }

    "report an error when timeOfArrival is missing" in {
      val input = formInput
        .updated("timeOfArrival.hour", "")
        .updated("timeOfArrival.minutes", "")
        .updated("timeOfArrival.period", "")
      form.bind(input).value shouldBe None
      form.bind(input).errors should haveOnlyErrors(
        FormError("timeOfArrival", "error.timeOfArrival.all.required")
      )
    }

    "report an error when timeOfArrival is partially missing" in {
      val input = formInput
        .updated("timeOfArrival.hour", "")
        .updated("timeOfArrival.minutes", "")
      form.bind(input).value shouldBe None
      form.bind(input).errors should haveOnlyErrors(
        FormError("timeOfArrival", "error.timeOfArrival.all.required")
      )
    }

    "report an error when timeOfArrival is invalid" in {
      val input = formInput
        .updated("timeOfArrival.hour", "25")
        .updated("timeOfArrival.minutes", "60")
        .updated("timeOfArrival.period", "ma")
      form.bind(input).value shouldBe None
      form.bind(input).errors should haveOnlyErrors(
        FormError("timeOfArrival", "error.timeOfArrival.hour.invalid-value"),
        FormError("timeOfArrival", "error.timeOfArrival.minutes.invalid-value"),
        FormError("timeOfArrival", "error.timeOfArrival.period.invalid-value")
      )
    }

    "report an error when dateOfArrival is more than 6 months in the past" in {
      val input = formInputFor(dateTime.minusMonths(6).minusDays(1))
      form.bind(input).value shouldBe None
      form.bind(input).errors should haveOnlyErrors(
        FormError("dateOfArrival", "error.dateOfArrival.all.invalid-value-past")
      )
    }

    "report an error when dateOfArrival is more than 6 months in the future" in {
      val input = formInputFor(dateTime.plusMonths(6).plusDays(1))
      form.bind(input).value shouldBe None
      form.bind(input).errors should haveOnlyErrors(
        FormError("dateOfArrival", "error.dateOfArrival.all.invalid-value-future")
      )
    }
  }

  "OptionalVesselDetailsForm" should {

    val form = TraderServicesFrontendController.OptionalVesselDetailsForm

    "bind some input fields and return VesselDetails and fill it back" in {
      form.bind(formInput).value shouldBe Some(formOutput)
      form.fill(formOutput).data shouldBe formInput
    }

    "return VesselDetails despite missing vesselName" in {
      val input = formInput.-("vesselName")
      val output = formOutput.copy(vesselName = None)
      form.bind(input).value shouldBe Some(output)
      form.fill(output).data shouldBe input
    }

    "report an error when vesselName is invalid" in {
      val input = formInput.updated("vesselName", "$$$")
      form.bind(input).value shouldBe None
      form.bind(input).errors should haveOnlyErrors(
        FormError("vesselName", "error.vesselName.invalid-characters")
      )
    }

    "return VesselDetails despite missing dateOfArrival" in {
      val input =
        formInput
          .updated("dateOfArrival.year", "")
          .updated("dateOfArrival.month", "")
          .updated("dateOfArrival.day", "")
      val output = formOutput.copy(dateOfArrival = None)
      form.bind(input).value shouldBe Some(output)
      form.fill(output).data shouldBe input
    }

    "report an error when dateOfArrival is partially missing" in {
      val input = formInput
        .updated("dateOfArrival.year", "")
        .updated("dateOfArrival.month", "")
      form.bind(input).value shouldBe None
      form.bind(input).errors should haveOnlyErrors(
        FormError("dateOfArrival", "error.dateOfArrival.year.required"),
        FormError("dateOfArrival", "error.dateOfArrival.month.required")
      )
    }

    "report an error when dateOfArrival is invalid" in {
      val input = formInput
        .updated("dateOfArrival.year", "202a")
        .updated("dateOfArrival.month", "13")
      form.bind(input).value shouldBe None
      form.bind(input).errors should haveOnlyErrors(
        FormError("dateOfArrival", "error.dateOfArrival.year.invalid-digits"),
        FormError("dateOfArrival", "error.dateOfArrival.month.invalid-value")
      )
    }

    "return VesselDetails despite missing timeOfArrival" in {
      val input = formInput
        .updated("timeOfArrival.hour", "")
        .updated("timeOfArrival.minutes", "")
        .updated("timeOfArrival.period", "")
      val output = formOutput.copy(timeOfArrival = None)
      form.bind(input).value shouldBe Some(output)
      form.fill(output).data shouldBe input
    }

    "report an error when timeOfArrival is partially missing" in {
      val input = formInput
        .updated("timeOfArrival.hour", "")
        .updated("timeOfArrival.minutes", "")
      val output = formOutput.copy(timeOfArrival = None)
      form.bind(input).value shouldBe Some(output)
      form.fill(output).data shouldBe input.updated("timeOfArrival.period", "")
    }

    "report an error when timeOfArrival is invalid" in {
      val input = formInput
        .updated("timeOfArrival.hour", "25")
        .updated("timeOfArrival.minutes", "60")
        .updated("timeOfArrival.period", "ma")
      form.bind(input).value shouldBe None
      form.bind(input).errors should haveOnlyErrors(
        FormError("timeOfArrival", "error.timeOfArrival.hour.invalid-value"),
        FormError("timeOfArrival", "error.timeOfArrival.minutes.invalid-value"),
        FormError("timeOfArrival", "error.timeOfArrival.period.invalid-value")
      )
    }

    "report an error when dateOfArrival is more than 6 months in the past" in {
      val input = formInputFor(dateTime.minusMonths(6).minusDays(1))
      form.bind(input).value shouldBe None
      form.bind(input).errors should haveOnlyErrors(
        FormError("dateOfArrival", "error.dateOfArrival.all.invalid-value-past")
      )
    }

    "report an error when dateOfArrival is more than 6 months in the future" in {
      val input = formInputFor(dateTime.plusMonths(6).plusDays(1))
      form.bind(input).value shouldBe None
      form.bind(input).errors should haveOnlyErrors(
        FormError("dateOfArrival", "error.dateOfArrival.all.invalid-value-future")
      )
    }
  }
}
