/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.traderservices.models

import uk.gov.hmrc.traderservices.support.UnitSpec
import scala.util.Random
import play.api.libs.json.Json

class TimestampSpec extends UnitSpec {

  "Timestamp" should {
    "serialize to and from json" in {
      Json.stringify(Json.toJson(Timestamp(7))) shouldBe "7"
      Json.parse("7").as[Timestamp].value shouldBe 7

      for (i <- Stream.continually(Random.nextLong).take(1000))
        Json.parse(Json.stringify(Json.toJson(Timestamp(i)))).as[Timestamp] shouldBe Timestamp(i)
    }

    "compare always to itself" in {
      for (i <- Stream.continually(Random.nextLong).take(1000))
        Timestamp(i) shouldBe Timestamp(i)
    }

    "compare always to AnyTimestamp" in {
      for (i <- Stream.continually(Random.nextLong).take(1000)) {
        Timestamp(i) shouldBe Timestamp.Any
        Timestamp.Any shouldBe Timestamp(i)
      }
    }

    "do not compare to next Timestamp" in {
      for (i <- Stream.continually(Random.nextLong).take(1000)) {
        Timestamp(i) should not be (Timestamp(i + 1))
        Timestamp(i) should not be (Timestamp(i - 1))
      }
    }

    "do not compare to other entities" in {
      for (i <- Stream.continually(Random.nextLong).take(1000)) {
        Timestamp(i) should not be s"$i"
        Timestamp(i) should not be (i.toInt)
      }
    }

    "check isAfter" in {
      Timestamp(1).isAfter(Timestamp(2), 1) shouldBe false
      Timestamp(2).isAfter(Timestamp(1), 1) shouldBe false
      Timestamp(3).isAfter(Timestamp(1), 1) shouldBe true
      Timestamp(0).isAfter(Timestamp.Any, 1) shouldBe true
      Timestamp.Any.isAfter(Timestamp(0), 1) shouldBe true
      Timestamp.Any.isAfter(Timestamp.Any, 1) shouldBe true
    }

    "print as human readable time" in {
      Timestamp(0).toString shouldBe "00:00:00"
      Timestamp(1).toString shouldBe "00:00:00.001"
    }

    "have stable and unique hash code" in {
      for (i <- Stream.continually(Random.nextLong).take(1000)) {
        Timestamp(i).hashCode shouldBe i.toInt
        Timestamp(i).hashCode should not be (Timestamp(i + 1).hashCode)
        Timestamp(i).hashCode should not be (Timestamp(i - 1).hashCode)
      }
    }
  }
}
