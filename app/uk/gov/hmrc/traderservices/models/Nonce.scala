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

import play.api.libs.json.Format
import java.util.Base64
import java.nio.charset.StandardCharsets
import scala.util.Random

/** Random integer value container. */
sealed trait Nonce {

  val value: Int

  final override def hashCode(): Int =
    value

  /** Encodes nonce as an url-safe base64 string */
  override def toString: String =
    new String(Base64.getUrlEncoder().encode(Nonce.intToByteArray(value)), StandardCharsets.UTF_8)
}

object Nonce {

  def apply(): Nonce =
    toNonce(Random.nextInt())

  def apply(value: Int): Nonce =
    toNonce(value)

  /** Decodes nonce from an url-safe base64 string */
  def apply(string: String): Nonce =
    Nonce.byteArrayToInt(Base64.getUrlDecoder.decode(string.getBytes(StandardCharsets.UTF_8)))

  object MatchAny extends Nonce {
    final val value: Int = 0
    final override def equals(obj: Any): Boolean =
      obj.isInstanceOf[Nonce]
  }

  final class StrictNonce(val value: Int) extends Nonce {
    override def equals(obj: Any): Boolean =
      if (obj.isInstanceOf[MatchAny.type]) true
      else if (obj.isInstanceOf[Nonce])
        obj.asInstanceOf[Nonce].value == value
      else false
  }

  implicit final val formats: Format[Nonce] =
    SimpleDecimalFormat[Nonce](s => Nonce(s.toInt), n => BigDecimal(n.value))

  implicit final def toNonce(value: Int): Nonce =
    new StrictNonce(value)

  final def intToByteArray(value: Int): Array[Byte] =
    Array[Byte](
      (value >>> 24).toByte,
      (value >>> 16).toByte,
      (value >>> 8).toByte,
      value.toByte
    )

  final def byteArrayToInt(b: Array[Byte]): Int = {
    var value: Int = 0
    value += (b(0) & 0x000000ff) << 24
    value += (b(1) & 0x000000ff) << 16
    value += (b(2) & 0x000000ff) << 8
    value += (b(3) & 0x000000ff) << 0
    value
  }

}
