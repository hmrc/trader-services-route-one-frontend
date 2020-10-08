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
import uk.gov.hmrc.traderservices.models.{ImportFreightType, ImportPriorityGoods, ImportRequestType, ImportRouteType}
import uk.gov.hmrc.traderservices.support.FormMatchers
import play.api.data.Form

class ImportQuestionsFormSpec extends UnitSpec with FormMatchers {

  def validate[A](form: Form[A], formInput: Map[String, String], formOutput: A): Unit = {
    form.bind(formInput).value shouldBe Some(formOutput)
    form.fill(formOutput).data shouldBe formInput
  }

  def validate[A](form: Form[A], fieldName: String, formInput: Map[String, String], errors: Seq[String]): Unit = {
    form.bind(formInput).value shouldBe None
    form.bind(formInput).errors should haveOnlyErrors(errors.map(e => FormError(fieldName, e)): _*)
  }

  "Import questions forms" should {

    "bind some requestType and return ImportRequestType and fill it back" in {
      val form = TraderServicesFrontendController.ImportRequestTypeForm
      validate(form, Map("requestType" -> "New"), ImportRequestType.New)
      validate(form, Map("requestType" -> "Hold"), ImportRequestType.Hold)
      validate(form, Map("requestType" -> "Cancellation"), ImportRequestType.Cancellation)
      validate(form, "requestType", Map(), Seq("error.importRequestType.required"))
      validate(form, "requestType", Map("requestType" -> "Foo"), Seq("error.importRequestType.invalid-option"))
    }

    "bind some routeType and return ImportRouteType and fill it back" in {
      val form = TraderServicesFrontendController.ImportRouteTypeForm
      validate(form, Map("routeType" -> "Route1"), ImportRouteType.Route1)
      validate(form, Map("routeType" -> "Route1Cap"), ImportRouteType.Route1Cap)
      validate(form, Map("routeType" -> "Route2"), ImportRouteType.Route2)
      validate(form, Map("routeType" -> "Route3"), ImportRouteType.Route3)
      validate(form, Map("routeType" -> "Route6"), ImportRouteType.Route6)
      validate(form, Map("routeType" -> "Hold"), ImportRouteType.Hold)
      validate(form, "routeType", Map(), Seq("error.importRouteType.required"))
      validate(form, "routeType", Map("routeType" -> "Foo"), Seq("error.importRouteType.invalid-option"))
    }

    "bind tes/no and return Boolean and fill it back" in {
      val form = TraderServicesFrontendController.ImportHasPriorityGoodsForm
      validate(form, Map("hasPriorityGoods" -> "yes"), true)
      validate(form, Map("hasPriorityGoods" -> "no"), false)
    }

    "bind some priorityGoods and return ImportPriorityGoods and fill it back" in {
      val form = TraderServicesFrontendController.ImportPriorityGoodsForm
      validate(form, Map("priorityGoods" -> "LiveAnimals"), ImportPriorityGoods.LiveAnimals)
      validate(form, Map("priorityGoods" -> "HumanRemains"), ImportPriorityGoods.HumanRemains)
      validate(form, Map("priorityGoods" -> "HighValueArt"), ImportPriorityGoods.HighValueArt)
      validate(form, Map("priorityGoods" -> "ExplosivesOrFireworks"), ImportPriorityGoods.ExplosivesOrFireworks)
      validate(form, Map("priorityGoods" -> "ClassADrugs"), ImportPriorityGoods.ClassADrugs)
      validate(form, "priorityGoods", Map(), Seq("error.importPriorityGoods.required"))
      validate(form, "priorityGoods", Map("priorityGoods" -> "Foo"), Seq("error.importPriorityGoods.invalid-option"))
    }

    "bind some freightType and return ImportFreightType and fill it back" in {
      val form = TraderServicesFrontendController.ImportFreightTypeForm
      validate(form, Map("freightType" -> "Air"), ImportFreightType.Air)
      validate(form, Map("freightType" -> "Maritime"), ImportFreightType.Maritime)
      validate(form, Map("freightType" -> "RORO"), ImportFreightType.RORO)
      validate(form, "freightType", Map(), Seq("error.importFreightType.required"))
      validate(form, "freightType", Map("freightType" -> "Foo"), Seq("error.importFreightType.invalid-option"))
    }

  }
}
