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

package uk.gov.hmrc.traderservices.utils

import java.security.MessageDigest

object SHA256 {

  final def compute(value: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(value.toCharArray().map(_.toByte))
    convertBytesToHex(digest.digest())
  }

  private def convertBytesToHex(bytes: Array[Byte]): String = {
    val sb = new StringBuilder
    for (b <- bytes)
      sb.append(String.format("%02x", Byte.box(b)))
    sb.toString
  }

}
