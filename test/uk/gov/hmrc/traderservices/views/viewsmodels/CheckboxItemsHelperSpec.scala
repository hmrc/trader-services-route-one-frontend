/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.traderservices.views.viewsmodels

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{Messages, MessagesApi}
import play.api.test.Helpers
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.traderservices.models.EnumerationFormats
import uk.gov.hmrc.traderservices.views.CheckboxItemsHelper

class CheckboxItemsHelperSpec extends AnyFlatSpec with Matchers with CheckboxItemsHelper {
  implicit val messagesAPI: MessagesApi = Helpers.stubMessagesApi()
  implicit val messages: Messages = Helpers.stubMessages()

  object TestEnum extends Enumeration {
    type TestEnum = Value
    val testVal1, testVal2, testVal3 = Value
  }

  implicit val enumFormats: EnumerationFormats[TestEnum.Value] = new EnumerationFormats[TestEnum.Value] {
    override val values: Set[TestEnum.Value] = TestEnum.values
    override def keyOf(value: TestEnum.Value): Option[String] = Some(value.toString)
    override def valueOf(key: String): Option[TestEnum.Value] = TestEnum.values.find(_.toString == key)
  }

  "CheckboxItemsHelper" should "generate correct CheckboxItems" in {
    val form = Form(single("testField" -> text))
    val values = Seq(TestEnum.testVal1, TestEnum.testVal2, TestEnum.testVal3)

    val result = checkboxItems("testForm", "testField", values, form)

    result should have length 3

    result.map(_.value) should contain theSameElementsAs Seq("testVal1", "testVal2", "testVal3")
    result.map(_.content) should contain theSameElementsAs Seq(
      Text("form.testForm.testField.testVal1"),
      Text("form.testForm.testField.testVal2"),
      Text("form.testForm.testField.testVal3")
    )
    result.foreach(_.checked shouldBe false)
  }

  it should "mark items as checked when form contains the value" in {
    val form = Form(single("testField" -> text)).fill("testVal2")
    val values = Seq(TestEnum.testVal1, TestEnum.testVal2, TestEnum.testVal3)

    val result = checkboxItems("testForm", "testField", values, form)

    result should have length 3
    result.find(_.value == "testVal2").get.checked shouldBe true
    result.filterNot(_.value == "testVal2").foreach(_.checked shouldBe false)
  }
}
