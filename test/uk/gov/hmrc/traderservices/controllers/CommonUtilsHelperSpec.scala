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

import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import uk.gov.hmrc.traderservices.support.UnitSpec
import uk.gov.hmrc.traderservices.views.CommonUtilsHelper.Improvements
class CommonUtilsHelperSpec extends UnitSpec {

  "CommonUtilsHelper" should {

    "format EPU numbers correctly using 3 digit formatter" in {
      1.format3d mustBe "001"
      100.format3d mustBe "100"
      11.format3d mustBe "011"
    }
  }
}
