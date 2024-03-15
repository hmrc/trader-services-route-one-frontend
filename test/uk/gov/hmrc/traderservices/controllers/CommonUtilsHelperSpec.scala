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

import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import uk.gov.hmrc.traderservices.views.CommonUtilsHelper._

import java.time.LocalDateTime
import play.api.i18n._
import uk.gov.hmrc.traderservices.support.UnitSpec

class CommonUtilsHelperSpec extends UnitSpec {

  "CommonUtilsHelper" should {
    "format EPU numbers correctly using 3 digit formatter" in {
      1.format3d mustBe "001"
      100.format3d mustBe "100"
      11.format3d mustBe "011"
    }

    val messagesApi: MessagesApi =
      new DefaultMessagesApi(
        messages = Map(
          "en" -> Map("site.datetime.preposition" -> "at"),
          "cy" -> Map("site.datetime.preposition" -> "am")
        )
      )

    "format datetime using english localization" in {
      implicit val m: Messages = MessagesImpl(Lang("en"), messagesApi)
      LocalDateTime.parse("2021-03-25T16:01").ddMMYYYYAtTimeFormat shouldBe "25 March 2021 at 16:01"
    }

    "format datetime using welsh localization" in {
      implicit val m: Messages = MessagesImpl(Lang("cy"), messagesApi)
      LocalDateTime.parse("2021-03-25T16:01").ddMMYYYYAtTimeFormat shouldBe "25 Mawrth 2021 am 16:01"
    }
  }
}
