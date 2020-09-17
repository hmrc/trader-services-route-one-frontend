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

import play.api.data.validation.{Invalid, Valid, ValidationError}
import uk.gov.hmrc.traderservices.controllers.DateFieldHelper._
import uk.gov.hmrc.play.test.UnitSpec

class DateFieldHelperSpec extends UnitSpec {

  val `2020-04-21`: LocalDate = LocalDate.parse("2020-04-21")

  "DateFieldHelper" should {
    "reject invalid date string when wildcards allowed" in {
      validateDate("", `2020-04-21`, true) shouldBe false
      validateDate("   ", `2020-04-21`, true) shouldBe false
      validateDate("   -  -  ", `2020-04-21`, true) shouldBe false
      validateDate("0000-00-00", `2020-04-21`, true) shouldBe false
      validateDate("0", `2020-04-21`, true) shouldBe false
      validateDate("01", `2020-04-21`, true) shouldBe false
      validateDate("01-", `2020-04-21`, true) shouldBe false
      validateDate("---", `2020-04-21`, true) shouldBe false
      validateDate("--", `2020-04-21`, true) shouldBe false
      validateDate("-", `2020-04-21`, true) shouldBe false
      validateDate("01-01-01", `2020-04-21`, true) shouldBe false
      validateDate("2001-12-32", `2020-04-21`, true) shouldBe false
      validateDate("2001--1-30", `2020-04-21`, true) shouldBe false
      validateDate("2001-13-31", `2020-04-21`, true) shouldBe false
      validateDate("2001-02-30", `2020-04-21`, true) shouldBe false
      validateDate("2019-02-29", `2020-04-21`, true) shouldBe false
      validateDate("201-01-01", `2020-04-21`, true) shouldBe false
      validateDate("1899-01-01", `2020-04-21`, true) shouldBe false
      validateDate("2222-0X-07", `2020-04-21`, true) shouldBe false
      validateDate("2222-X1-07", `2020-04-21`, true) shouldBe false
      validateDate("2222-XX-X2", `2020-04-21`, true) shouldBe false
      validateDate("2222-XX-1X", `2020-04-21`, true) shouldBe false
      validateDate("1900-12-2X", `2020-04-21`, true) shouldBe false
      validateDate("1900-12-X2", `2020-04-21`, true) shouldBe false
      validateDate("2222-XX-07", `2020-04-21`, true) shouldBe false
      validateDate("2000-XX-XX", LocalDate.parse("1999-08-01"), true) shouldBe false
      validateDate("1999-09-XX", LocalDate.parse("1999-08-01"), true) shouldBe false
      validateDate("1999-08-02", LocalDate.parse("1999-08-01"), true) shouldBe false
      validateDate("2000-07-31", LocalDate.parse("1999-08-01"), true) shouldBe false
      validateDate("1999-12-XX", LocalDate.parse("1999-08-01"), true) shouldBe false
      validateDate("2000-08-01", LocalDate.parse("1999-08-01"), true) shouldBe false
      validateDate("1999-07-16", LocalDate.parse("1999-07-15"), true) shouldBe false
      validateDate("2000-07-15", LocalDate.parse("1999-07-15"), true) shouldBe false
    }

    "reject invalid date string when wildcards disallowed" in {
      validateDate("", `2020-04-21`, false) shouldBe false
      validateDate("   ", `2020-04-21`, false) shouldBe false
      validateDate("   -  -  ", `2020-04-21`, false) shouldBe false
      validateDate("0000-00-00", `2020-04-21`, false) shouldBe false
      validateDate("0", `2020-04-21`, false) shouldBe false
      validateDate("01", `2020-04-21`, false) shouldBe false
      validateDate("01-", `2020-04-21`, false) shouldBe false
      validateDate("---", `2020-04-21`, false) shouldBe false
      validateDate("--", `2020-04-21`, false) shouldBe false
      validateDate("-", `2020-04-21`, false) shouldBe false
      validateDate("01-01-01", `2020-04-21`, false) shouldBe false
      validateDate("2001-12-32", `2020-04-21`, false) shouldBe false
      validateDate("2001--1-30", `2020-04-21`, false) shouldBe false
      validateDate("2001-13-31", `2020-04-21`, false) shouldBe false
      validateDate("2001-02-30", `2020-04-21`, false) shouldBe false
      validateDate("2019-02-29", `2020-04-21`, false) shouldBe false
      validateDate("201-01-01", `2020-04-21`, false) shouldBe false
      validateDate("1899-01-01", `2020-04-21`, false) shouldBe false
      validateDate("2222-0X-07", `2020-04-21`, false) shouldBe false
      validateDate("2222-X1-07", `2020-04-21`, false) shouldBe false
      validateDate("2222-XX-X2", `2020-04-21`, false) shouldBe false
      validateDate("2222-XX-1X", `2020-04-21`, false) shouldBe false
      validateDate("1900-12-2X", `2020-04-21`, false) shouldBe false
      validateDate("1900-12-X2", `2020-04-21`, false) shouldBe false
      validateDate("2222-XX-07", `2020-04-21`, false) shouldBe false
      validateDate("2000-XX-XX", LocalDate.parse("1999-08-01"), false) shouldBe false
      validateDate("1999-09-XX", LocalDate.parse("1999-08-01"), false) shouldBe false
      validateDate("1999-08-02", LocalDate.parse("1999-08-01"), false) shouldBe false
      validateDate("2000-07-31", LocalDate.parse("1999-08-01"), false) shouldBe false
      validateDate("1999-12-XX", LocalDate.parse("1999-08-01"), false) shouldBe false
      validateDate("2000-08-01", LocalDate.parse("1999-08-01"), false) shouldBe false
      validateDate("1999-07-16", LocalDate.parse("1999-07-15"), false) shouldBe false
      validateDate("2000-07-15", LocalDate.parse("1999-07-15"), false) shouldBe false
    }

    "accept valid date string when wildcards allowed" in {
      validateDate("1970-01-01", `2020-04-21`, true) shouldBe true
      validateDate("2001-12-31", `2020-04-21`, true) shouldBe true
      validateDate("2999-06-07", `2020-04-21`, true) shouldBe false
      validateDate("2222-XX-XX", `2020-04-21`, true) shouldBe false
      validateDate("2222-XX-XX", LocalDate.parse("2222-01-01"), true) shouldBe true
      validateDate("1900-12-XX", `2020-04-21`, true) shouldBe true
      validateDate("2000-02-29", `2020-04-21`, true) shouldBe true
      validateDate("2020-02-29", `2020-04-21`, true) shouldBe true
      validateDate("2016-02-29", `2020-04-21`, true) shouldBe true
      validateDate("2012-02-29", `2020-04-21`, true) shouldBe true
      validateDate("2008-02-29", `2020-04-21`, true) shouldBe true
      validateDate("2004-02-29", `2020-04-21`, true) shouldBe true
      validateDate("2000-02-29", `2020-04-21`, true) shouldBe true
      validateDate("1999-XX-XX", LocalDate.parse("1999-08-01"), true) shouldBe true
      validateDate("1999-08-XX", LocalDate.parse("1999-08-01"), true) shouldBe true
      validateDate("1999-08-01", LocalDate.parse("1999-08-01"), true) shouldBe true
      validateDate("1999-07-31", LocalDate.parse("1999-08-01"), true) shouldBe true
      validateDate("1998-12-XX", LocalDate.parse("1999-08-01"), true) shouldBe true
      validateDate("1998-08-02", LocalDate.parse("1999-08-01"), true) shouldBe true
      validateDate("1999-07-15", LocalDate.parse("1999-07-15"), true) shouldBe true
      validateDate("1999-07-14", LocalDate.parse("1999-07-15"), true) shouldBe true
    }

    "accept valid date string when wildcards disallowed" in {
      validateDate("1970-01-01", `2020-04-21`, false) shouldBe true
      validateDate("2001-12-31", `2020-04-21`, false) shouldBe true
      validateDate("2999-06-07", `2020-04-21`, false) shouldBe false
      validateDate("2222-XX-XX", `2020-04-21`, false) shouldBe false
      validateDate("2222-XX-XX", LocalDate.parse("2222-01-01"), false) shouldBe false
      validateDate("1900-12-XX", `2020-04-21`, false) shouldBe false
      validateDate("2000-02-29", `2020-04-21`, false) shouldBe true
      validateDate("2020-02-29", `2020-04-21`, false) shouldBe true
      validateDate("2016-02-29", `2020-04-21`, false) shouldBe true
      validateDate("2012-02-29", `2020-04-21`, false) shouldBe true
      validateDate("2008-02-29", `2020-04-21`, false) shouldBe true
      validateDate("2004-02-29", `2020-04-21`, false) shouldBe true
      validateDate("2000-02-29", `2020-04-21`, false) shouldBe true
      validateDate("1999-XX-XX", LocalDate.parse("1999-08-01"), false) shouldBe false
      validateDate("1999-08-XX", LocalDate.parse("1999-08-01"), false) shouldBe false
      validateDate("1999-08-01", LocalDate.parse("1999-08-01"), false) shouldBe true
      validateDate("1999-07-31", LocalDate.parse("1999-08-01"), false) shouldBe true
      validateDate("1998-12-XX", LocalDate.parse("1999-08-01"), false) shouldBe false
      validateDate("1998-08-02", LocalDate.parse("1999-08-01"), false) shouldBe true
      validateDate("1999-07-15", LocalDate.parse("1999-07-15"), false) shouldBe true
      validateDate("1999-07-14", LocalDate.parse("1999-07-15"), false) shouldBe true
    }

    "format date from fields" in {
      formatDateFromFields("", "", "") shouldBe ""
      formatDateFromFields("2019", "", "") shouldBe "2019-XX-XX"
      formatDateFromFields("96", "", "") shouldBe "1996-XX-XX"
      formatDateFromFields("2019", "05", "") shouldBe "2019-05-XX"
      formatDateFromFields("2019", "", "17") shouldBe "2019-XX-17"
      formatDateFromFields("2019", "7", "5") shouldBe "2019-07-05"
      formatDateFromFields("2019", "7", "") shouldBe "2019-07-XX"
      formatDateFromFields("2019", "", "1") shouldBe "2019-XX-01"
      formatDateFromFields("", "11", "30") shouldBe "-11-30"
    }
    "parse date into fields" in {
      parseDateIntoFields("2019-01-17") shouldBe Some(("2019", "01", "17"))
      parseDateIntoFields("2019") shouldBe Some(("2019", "", ""))
      parseDateIntoFields("2019-01") shouldBe Some(("2019", "01", ""))
      parseDateIntoFields("2019-01-XX") shouldBe Some(("2019", "01", ""))
      parseDateIntoFields("2019-XX-XX") shouldBe Some(("2019", "", ""))
      parseDateIntoFields("2019-XX-31") shouldBe Some(("2019", "", "31"))
      parseDateIntoFields("foo") shouldBe Some(("foo", "", ""))
      parseDateIntoFields("2019-foo-bar") shouldBe Some(("2019", "foo", "bar"))
      parseDateIntoFields("") shouldBe Some(("", "", ""))
    }
    "distinguish between missing and invalid date format" in {
      validDobDateFormat("") shouldBe Invalid(ValidationError("error.dateOfBirth.required"))
      validDobDateFormat("-11-30") shouldBe Invalid(ValidationError("error.dateOfBirth.invalid-format"))
      validDobDateFormat("-11-") shouldBe Invalid(ValidationError("error.dateOfBirth.invalid-format"))
      validDobDateFormat("--20") shouldBe Invalid(ValidationError("error.dateOfBirth.invalid-format"))
      validDobDateFormat("1972-XX-11") shouldBe Invalid(ValidationError("error.dateOfBirth.invalid-format"))
      validDobDateFormat("1972-11-XX") shouldBe Invalid(ValidationError("error.dateOfBirth.invalid-format"))
      validDobDateFormat("1972-XX-XX") shouldBe Invalid(ValidationError("error.dateOfBirth.invalid-format"))
    }
  }

}
