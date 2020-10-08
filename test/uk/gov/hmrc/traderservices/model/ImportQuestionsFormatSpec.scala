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

package uk.gov.hmrc.traderservices.model

import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.support.JsonFormatTest

class ImportQuestionsFormatSpec extends UnitSpec {

  "ImportQuestionsFormats" should {

    "serialize and deserialize ImportRequestType" in new JsonFormatTest[ImportRequestType](info) {

      ImportRequestType.values.size shouldBe 4

      validateJsonFormat("New", ImportRequestType.New)
      validateJsonFormat("Cancellation", ImportRequestType.Cancellation)
      validateJsonFormat("Hold", ImportRequestType.Hold)
    }

    "serialize and deserialize ImportRouteType" in new JsonFormatTest[ImportRouteType](info) {

      ImportRouteType.values.size shouldBe 6

      validateJsonFormat("Route1", ImportRouteType.Route1)
      validateJsonFormat("Route1Cap", ImportRouteType.Route1Cap)
      validateJsonFormat("Route2", ImportRouteType.Route2)
      validateJsonFormat("Route3", ImportRouteType.Route3)
      validateJsonFormat("Route6", ImportRouteType.Route6)
      validateJsonFormat("Hold", ImportRouteType.Hold)
    }

    "serialize and deserialize ImportGoodsPriority" in new JsonFormatTest[ImportPriorityGoods](info) {

      ImportPriorityGoods.values.size shouldBe 5

      validateJsonFormat("LiveAnimals", ImportPriorityGoods.LiveAnimals)
      validateJsonFormat("HumanRemains", ImportPriorityGoods.HumanRemains)
      validateJsonFormat("ExplosivesOrFireworks", ImportPriorityGoods.ExplosivesOrFireworks)
      validateJsonFormat("HighValueArt", ImportPriorityGoods.HighValueArt)
      validateJsonFormat("ClassADrugs", ImportPriorityGoods.ClassADrugs)
    }

    "serialize and deserialize ImportFreightType" in new JsonFormatTest[ImportFreightType](info) {

      ImportFreightType.values.size shouldBe 3

      validateJsonFormat("Air", ImportFreightType.Air)
      validateJsonFormat("Maritime", ImportFreightType.Maritime)
      validateJsonFormat("RORO", ImportFreightType.RORO)
    }
  }
}
