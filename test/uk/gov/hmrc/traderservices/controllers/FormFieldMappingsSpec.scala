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

import play.api.data.validation.{Constraint, Invalid, Valid}
import uk.gov.hmrc.traderservices.controllers.FormFieldMappings.validName
import uk.gov.hmrc.play.test.UnitSpec

class FormFieldMappingsSpec extends UnitSpec {

  val validateName: Constraint[String] = validName(fieldName = "bar", minLenInc = 0)
  val invalid = Invalid("error.bar.invalid-format")

  "FormFieldMappings" should {
    "validate name" in {
      validName(fieldName = "foo", minLenInc = 2)("a") shouldBe Invalid("error.foo.invalid-format")
      validName(fieldName = "foo", minLenInc = 1)("a") shouldBe Valid
      validateName("1") shouldBe invalid
      validateName("1a") shouldBe invalid
      validateName("a1") shouldBe invalid
      validateName("a1") shouldBe invalid
      validateName("Artur") shouldBe Valid
      validateName("Art ur") shouldBe Valid
      validateName("Art-ur") shouldBe Valid
      validateName("Art'ur") shouldBe Valid
      validateName("Art'ur") shouldBe Valid
      validateName("Art2ur") shouldBe invalid
      validateName("Art_ur") shouldBe invalid
      validateName("$Artur") shouldBe invalid
      validateName("@Artur") shouldBe invalid
      validateName("Ar#tur") shouldBe invalid
      validateName("ĄĘÓŚŻĆŁąęółśćńżźāēīūčģķļņšž") shouldBe Valid
    }
  }

}
