/*
 * Copyright 2023 HM Revenue & Customs
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

import test.uk.gov.hmrc.traderservices.support.UnitSpec
import java.net.URLDecoder

class RFC3986EncoderSpec extends UnitSpec {

  "RFC3986Encoder" should {
    "encode US-ASCII string using percentage encoding defined in RFC 3986" in {
      val sentence: String = """abcdefghijklmnopqrstuwxyzABCDEFGHIJKLMNOPQRSTUWXYZ0123456789_-.;><()[]{}"'`~@#$^&*"""
      val e1 = RFC3986Encoder.encode(sentence)
      e1 shouldBe sentence
      URLDecoder.decode(e1, "utf8") shouldBe sentence
    }

    "encode non US-ASCII string using percentage encoding defined in RFC 3986" in {
      val sentence: String = (0 to 1024).map(_.toChar).foldLeft("")(_ + _)
      val e1 = RFC3986Encoder.encode(sentence)
      URLDecoder.decode(e1, "utf8") shouldBe sentence
    }
  }
}
