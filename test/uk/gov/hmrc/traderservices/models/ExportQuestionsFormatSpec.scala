///*
// * Copyright 2023 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.traderservices.models
//
//import uk.gov.hmrc.traderservices.support.UnitSpec
//import uk.gov.hmrc.traderservices.support.JsonFormatTest
//
//class ExportQuestionsFormatSpec extends UnitSpec {
//
//  "ExportQuestionsFormats" should {
//
//    "serialize and deserialize ExportRequestType" in new JsonFormatTest[ExportRequestType](info) {
//
//      ExportRequestType.values.size shouldBe 6
//
//      validateJsonFormat("New", ExportRequestType.New)
//      validateJsonFormat("Cancellation", ExportRequestType.Cancellation)
//      validateJsonFormat("C1601", ExportRequestType.C1601)
//      validateJsonFormat("C1602", ExportRequestType.C1602)
//      validateJsonFormat("C1603", ExportRequestType.C1603)
//      validateJsonFormat("WithdrawalOrReturn", ExportRequestType.WithdrawalOrReturn)
//    }
//
//    "serialize and deserialize ExportRouteType" in new JsonFormatTest[ExportRouteType](info) {
//
//      ExportRouteType.values.size shouldBe 6
//
//      validateJsonFormat("Route1", ExportRouteType.Route1)
//      validateJsonFormat("Route1Cap", ExportRouteType.Route1Cap)
//      validateJsonFormat("Route2", ExportRouteType.Route2)
//      validateJsonFormat("Route3", ExportRouteType.Route3)
//      validateJsonFormat("Route6", ExportRouteType.Route6)
//      validateJsonFormat("Hold", ExportRouteType.Hold)
//    }
//
//    "serialize and deserialize ExportPriorityGoods" in new JsonFormatTest[ExportPriorityGoods](info) {
//
//      ExportPriorityGoods.values.size shouldBe 3
//
//      validateJsonFormat("LiveAnimals", ExportPriorityGoods.LiveAnimals)
//      validateJsonFormat("HumanRemains", ExportPriorityGoods.HumanRemains)
//      validateJsonFormat("ExplosivesOrFireworks", ExportPriorityGoods.ExplosivesOrFireworks)
//    }
//
//    "serialize and deserialize ExportFreightType" in new JsonFormatTest[ExportFreightType](info) {
//
//      ExportFreightType.values.size shouldBe 3
//
//      validateJsonFormat("Air", ExportFreightType.Air)
//      validateJsonFormat("Maritime", ExportFreightType.Maritime)
//      validateJsonFormat("RORO", ExportFreightType.RORO)
//    }
//
//  }
//}
