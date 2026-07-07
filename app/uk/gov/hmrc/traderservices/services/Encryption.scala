/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.traderservices.services

import play.api.libs.json.{Json, Reads, Writes}
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainText}

import scala.util.Try

object Encryption {

  def encrypt[T: Writes](value: T, encrypter: Encrypter): Crypted =
    encrypter.encrypt(PlainText(Json.stringify(Json.toJson(value))))

  def decrypt[T](crypted: Crypted, crypto: Decrypter, keyProvider: KeyProvider)(implicit reads: Reads[T]): T =
    decryptGcm(crypted, crypto)
      .orElse(Try(LegacyEncryption.decryptLegacy(crypted.value, keyProvider)).toOption)
      .getOrElse(throw new SecurityException("Failed decrypting data"))

  private def decryptGcm[T](crypted: Crypted, decrypter: Decrypter)(implicit reads: Reads[T]): Option[T] =
    Try(decrypter.decrypt(crypted).value).toOption.map { decrypted =>
      Json.parse(decrypted).as[T]
    }

}
