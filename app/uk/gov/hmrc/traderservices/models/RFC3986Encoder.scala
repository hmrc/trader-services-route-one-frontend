/*
 * Copyright 2021 HM Revenue & Customs
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

import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

object RFC3986Encoder {

  private val hexDigits =
    Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

  def encode(string: String): String = {
    val bytes = StandardCharsets.UTF_8.encode(
      CharBuffer.wrap(
        string
          .replaceAllLiterally("%", "%25")
          .replaceAllLiterally("+", "%2B")
      )
    )
    val sb = new StringBuffer(bytes.limit())
    while (bytes.hasRemaining()) {
      val b = bytes.get() & 0xff;
      if (b >= 0x80) {
        sb.append('%')
        sb.append(hexDigits((b >> 4) & 0x0f))
        sb.append(hexDigits((b >> 0) & 0x0f))
      } else
        sb.append(b.toChar);
    }
    sb.toString()
  }
}
