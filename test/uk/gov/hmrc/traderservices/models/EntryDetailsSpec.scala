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

package uk.gov.hmrc.traderservices.models

import uk.gov.hmrc.traderservices.support.UnitSpec

import java.time.LocalDate

class EntryDetailsSpec extends UnitSpec with EntryDetailsTestData {

  "EntryDetails" when {
    "isExportDeclaration is called" should {
      "return true when entry number begins and ends with a letter" in {
        exportEntryDetails.isExportDeclaration shouldBe true
      }

      "return false when entry number starts with a digit and ends with a letter" in {
        importEntryDetails.isExportDeclaration shouldBe false
      }

      "return false when entry number starts and ends with a digit" in {
        invalidEntryDetails.isExportDeclaration shouldBe false
      }
    }

    "isImportDeclaration is called" should {
      "return false when entry number begins and ends with a letter" in {
        exportEntryDetails.isImportDeclaration shouldBe false
      }

      "return true when entry number start with a digit and ends with a letter" in {
        importEntryDetails.isImportDeclaration shouldBe true
      }

      "return false when entry number starts and ends with a digit" in {
        invalidEntryDetails.isImportDeclaration shouldBe false
      }
    }
  }
}

trait EntryDetailsTestData {

  val eoriNumber = "foo"
  val correlationId = "123"

  val exportEntryDetails = EntryDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-09-23"))
  val importEntryDetails = EntryDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-09-23"))
  val invalidEntryDetails = EntryDetails(EPU(123), EntryNumber("0000000"), LocalDate.parse("2020-09-23"))

}
