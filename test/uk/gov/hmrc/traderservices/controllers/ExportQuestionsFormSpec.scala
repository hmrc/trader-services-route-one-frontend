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
import uk.gov.hmrc.traderservices.models.ExportFreightType

class ExportQuestionsFormSpec extends UnitSpec with FormMatchers {

  "Export questions forms" should {

    val form = TraderServicesFrontendController.ExportRequestTypeForm

    "bind some requestType and return ExportRequestType and fill it back" in {
      val formInput = Map("requestType" -> "New")
      val formOutput = ExportRequestType.New
      form.bind(formInput).value shouldBe Some(formOutput)
      form.fill(formOutput).data shouldBe formInput
    }

  }
}
