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

package uk.gov.hmrc.traderservices.support

import play.api.data.Form
import play.api.data.FormError
import org.scalatest.Matchers
import org.scalatest.OptionValues

trait FormValidator extends FormMatchers {
  self: Matchers with OptionValues =>

  def validate[A](form: Form[A], formInput: Map[String, String], formOutput: A): Unit = {
    form.bind(formInput).value shouldBe Some(formOutput)
    form.fill(formOutput).data shouldBe formInput
  }

  def validate[A](form: Form[A], fieldName: String, formInput: Map[String, String], errors: Seq[String]): Unit = {
    form.bind(formInput).value shouldBe None
    form.bind(formInput).errors should haveOnlyErrors(errors.map(e => FormError(fieldName, e)): _*)
  }
}
