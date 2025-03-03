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

package uk.gov.hmrc.traderservices.controllers

import uk.gov.hmrc.traderservices.models.{ExportContactInfo, ExportFreightType, ExportPriorityGoods, ExportRequestType, ExportRouteType}
import uk.gov.hmrc.traderservices.support.{FormValidator, UnitSpec}

class ExportQuestionsFormSpec extends UnitSpec with FormValidator {

  "Export questions forms" should {

    "bind some requestType and return ExportRequestType and fill it back" in {
      val form = CreateCaseJourneyController.ExportRequestTypeForm
      validate(form, Map("requestType" -> "New"), ExportRequestType.New)
      validate(form, Map("requestType" -> "Cancellation"), ExportRequestType.Cancellation)
      validate(form, Map("requestType" -> "C1601"), ExportRequestType.C1601)
      validate(form, Map("requestType" -> "C1602"), ExportRequestType.C1602)
      validate(form, Map("requestType" -> "C1603"), ExportRequestType.C1603)
      validate(form, Map("requestType" -> "WithdrawalOrReturn"), ExportRequestType.WithdrawalOrReturn)
      validate(form, "requestType", Map(), Seq("error.exportRequestType.required"))
      validate(form, "requestType", Map("requestType" -> "Foo"), Seq("error.exportRequestType.invalid-option"))
    }

    "bind some routeType and return ExportRouteType and fill it back" in {
      val form = CreateCaseJourneyController.ExportRouteTypeForm
      validate(form, Map("routeType" -> "Route1"), ExportRouteType.Route1)
      validate(form, Map("routeType" -> "Route1Cap"), ExportRouteType.Route1Cap)
      validate(form, Map("routeType" -> "Route2"), ExportRouteType.Route2)
      validate(form, Map("routeType" -> "Route3"), ExportRouteType.Route3)
      validate(form, Map("routeType" -> "Route6"), ExportRouteType.Route6)
      validate(form, Map("routeType" -> "Hold"), ExportRouteType.Hold)
      validate(form, "routeType", Map(), Seq("error.exportRouteType.required"))
      validate(form, "routeType", Map("routeType" -> "Foo"), Seq("error.exportRouteType.invalid-option"))
    }

    "bind yes/no and return Boolean and fill it back" in {
      val form = CreateCaseJourneyController.ExportHasPriorityGoodsForm
      validate(form, Map("hasPriorityGoods" -> "yes"), true)
      validate(form, Map("hasPriorityGoods" -> "no"), false)
      validate(form, "hasPriorityGoods", Map(), Seq("error.exportHasPriorityGoods.required"))
      validate(
        form,
        "hasPriorityGoods",
        Map("hasPriorityGoods" -> "Foo"),
        Seq("error.exportHasPriorityGoods.required")
      )
    }

    "bind some priorityGoods and return ExportPriorityGoods and fill it back" in {
      val form = CreateCaseJourneyController.ExportPriorityGoodsForm
      validate(form, Map("priorityGoods" -> "LiveAnimals"), ExportPriorityGoods.LiveAnimals)
      validate(form, Map("priorityGoods" -> "HumanRemains"), ExportPriorityGoods.HumanRemains)
      validate(form, Map("priorityGoods" -> "ExplosivesOrFireworks"), ExportPriorityGoods.ExplosivesOrFireworks)
      validate(form, "priorityGoods", Map(), Seq("error.exportPriorityGoods.required"))
      validate(form, "priorityGoods", Map("priorityGoods" -> "Foo"), Seq("error.exportPriorityGoods.invalid-option"))
    }

    "bind some freightType and return ExportFreightType and fill it back" in {
      val form = CreateCaseJourneyController.ExportFreightTypeForm
      validate(form, Map("freightType" -> "Air"), ExportFreightType.Air)
      validate(form, Map("freightType" -> "Maritime"), ExportFreightType.Maritime)
      validate(form, Map("freightType" -> "RORO"), ExportFreightType.RORO)
      validate(form, "freightType", Map(), Seq("error.exportFreightType.required"))
      validate(form, "freightType", Map("freightType" -> "Foo"), Seq("error.exportFreightType.invalid-option"))
    }

    "bind some contactInfo and return ExportContactInfo and fill it back" in {
      val form = CreateCaseJourneyController.ExportContactForm
      validate(
        form,
        Map("contactEmail" -> "name@example.com"),
        ExportContactInfo(contactEmail = "name@example.com")
      )

      validate(
        form,
        Map("contactName" -> "Full Name", "contactEmail" -> "name@example.com", "contactNumber" -> "01234567891"),
        ExportContactInfo(
          contactName = Some("Full Name"),
          contactEmail = "name@example.com",
          contactNumber = Some("01234567891")
        )
      )
      validate(
        form,
        "contactNumber",
        Map("contactName" -> "Full Name", "contactEmail" -> "name@example.com", "contactNumber" -> " "),
        Seq("error.contactNumber")
      )
      validate(
        form,
        "contactNumber",
        Map("contactName" -> "Full Name", "contactEmail" -> "name@example.com", "contactNumber" -> "00000000000"),
        Seq("error.contactNumber")
      )
    }
  }
}
