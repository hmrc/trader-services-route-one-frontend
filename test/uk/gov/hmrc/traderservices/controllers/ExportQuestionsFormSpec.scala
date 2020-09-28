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
import uk.gov.hmrc.traderservices.models.{ExportGoodsPriority, ExportQuestions, ExportRequestType, ExportRouteType}
import uk.gov.hmrc.traderservices.support.FormMatchers
import uk.gov.hmrc.traderservices.models.ExportRequestType
import uk.gov.hmrc.traderservices.models.ExportRouteType
import uk.gov.hmrc.traderservices.models.ExportGoodsPriority

class ExportQuestionsFormSpec extends UnitSpec with FormMatchers {

  val formOutput = ExportQuestions(
    requestType = ExportRequestType.New,
    routeType = ExportRouteType.Hold,
    goodsPriority = ExportGoodsPriority.None
  )

  val formInput = Map(
    "requestType"   -> "New",
    "routeType"     -> "Hold",
    "goodsPriority" -> "None"
  )

  "ExportQuestionsForm" should {

    val form = TraderServicesFrontendController.ExportQuestionsForm

    "bind some input fields and return ExportQuestionsForm and fill it back" in {
      form.bind(formInput).value shouldBe Some(formOutput)
      form.fill(formOutput).data shouldBe formInput
    }

    "report an error when requestType is missing" in {
      val input = formInput.-("requestType")
      form.bind(input).value shouldBe None
      form.bind(input).errors should haveOnlyError(FormError("requestType", "error.requestType.required"))
    }

    "report an error when requestType is invalid" in {
      val input = formInput.updated("requestType", "FooBar")
      form.bind(input).value shouldBe None
      form.bind(input).errors should haveOnlyError(FormError("requestType", "error.requestType.invalid-option"))
    }

    "report an error when routeType is missing" in {
      val input = formInput.-("routeType")
      form.bind(input).value shouldBe None
      form.bind(input).errors should haveOnlyError(FormError("routeType", "error.routeType.required"))
    }

    "report an error when routeType is invalid" in {
      val input = formInput.updated("routeType", "FooBar")
      form.bind(input).value shouldBe None
      form.bind(input).errors should haveOnlyError(FormError("routeType", "error.routeType.invalid-option"))
    }

    "report an error when goodsPriority is missing" in {
      val input = formInput.-("goodsPriority")
      form.bind(input).value shouldBe None
      form.bind(input).errors should haveOnlyError(FormError("goodsPriority", "error.goodsPriority.required"))
    }

    "report an error when goodsPriority is invalid" in {
      val input = formInput.updated("goodsPriority", "FooBar")
      form.bind(input).value shouldBe None
      form.bind(input).errors should haveOnlyError(FormError("goodsPriority", "error.goodsPriority.invalid-option"))
    }

  }
}
