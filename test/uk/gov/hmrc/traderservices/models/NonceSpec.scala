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

class NonceSpec extends UnitSpec {

  "Nonce" should {
    "serialize to and from base64 string" in {
      Nonce(0).toString shouldBe "AAAAAA=="
      Nonce("AAAAAA==").value shouldBe 0

      for (i <- Stream.continually(Random.nextInt).take(1000))
        Nonce(Nonce(i).toString) shouldBe Nonce(i)
    }

    "serialize to and from json" in {
      Json.stringify(Json.toJson(Nonce(7))) shouldBe "7"
      Json.parse("7").as[Nonce].value shouldBe 7

      for (i <- Stream.continually(Random.nextInt).take(1000))
        Json.parse(Json.stringify(Json.toJson(Nonce(i)))).as[Nonce] shouldBe Nonce(i)
    }

    "compare always to itself" in {
      for (i <- Stream.continually(Random.nextInt).take(1000))
        Nonce(i) shouldBe Nonce(i)
    }

    "compare always to AnyNonce" in {
      for (i <- Stream.continually(Random.nextInt).take(1000)) {
        Nonce(i) shouldBe Nonce.Any
        Nonce.Any shouldBe Nonce(i)
      }
    }

    "do not compare to next Nonce" in {
      for (i <- Stream.continually(Random.nextInt).take(1000)) {
        Nonce(i) should not be (Nonce(i + 1))
        Nonce(i) should not be (Nonce(i - 1))
      }
    }

    "do not compare to other entities" in {
      for (i <- Stream.continually(Random.nextInt).take(1000)) {
        Nonce(i) should not be s"$i"
        Nonce(i) should not be (i.toInt)
      }
    }

    "create random if invalid string" in {
      Nonce("")
      Nonce("...")
    }

    "have stable and unique hash code" in {
      for (i <- Stream.continually(Random.nextInt).take(1000)) {
        Nonce(i).hashCode shouldBe i.toInt
        Nonce(i).hashCode should not be (Nonce(i + 1).hashCode)
        Nonce(i).hashCode should not be (Nonce(i - 1).hashCode)
      }
    }
  }
}
