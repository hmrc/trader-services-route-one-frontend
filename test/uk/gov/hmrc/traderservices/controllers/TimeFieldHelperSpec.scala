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

import play.api.data.validation.{Invalid, Valid, ValidationError}
import uk.gov.hmrc.traderservices.controllers.TimeFieldHelper._
import uk.gov.hmrc.play.test.UnitSpec
import java.time.LocalTime

class TimeFieldHelperSpec extends UnitSpec {

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
  }

}
