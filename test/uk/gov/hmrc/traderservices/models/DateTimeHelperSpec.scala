/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.traderservices.models

import uk.gov.hmrc.traderservices.support.UnitSpec
import java.time.ZonedDateTime

class DateTimeHelperSpec extends UnitSpec {

  import DateTimeHelper.isWorkingHours

  "DateTimeHelper" should {
    "test if datetime is inside service's working hours" in {
      isWorkingHours(ZonedDateTime.of(2021, 5, 19, 7, 59, 59, 999, DateTimeHelper.londonTimeZone), 8, 16) shouldBe false
      isWorkingHours(ZonedDateTime.of(2021, 5, 19, 8, 0, 0, 0, DateTimeHelper.londonTimeZone), 8, 16) shouldBe true
      isWorkingHours(ZonedDateTime.of(2021, 5, 19, 12, 55, 0, 0, DateTimeHelper.londonTimeZone), 8, 16) shouldBe true
      isWorkingHours(ZonedDateTime.of(2021, 5, 19, 15, 59, 59, 999, DateTimeHelper.londonTimeZone), 8, 16) shouldBe true
      isWorkingHours(ZonedDateTime.of(2021, 5, 19, 16, 0, 0, 0, DateTimeHelper.londonTimeZone), 8, 16) shouldBe false
      isWorkingHours(ZonedDateTime.of(2021, 5, 16, 12, 55, 0, 0, DateTimeHelper.londonTimeZone), 8, 16) shouldBe false
      isWorkingHours(ZonedDateTime.of(2021, 5, 15, 12, 55, 0, 0, DateTimeHelper.londonTimeZone), 8, 16) shouldBe false
      isWorkingHours(ZonedDateTime.of(2021, 5, 13, 12, 55, 0, 0, DateTimeHelper.londonTimeZone), 8, 16) shouldBe true
    }
  }
}
