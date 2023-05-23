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

package uk.gov.hmrc.traderservices.services

import com.typesafe.config.Config
import org.apache.commons.codec.binary.Base64
import play.api.Logger
import play.api.libs.json.{JsError, JsSuccess, Json, Reads, Writes}

import java.nio.charset.StandardCharsets
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import scala.collection.JavaConverters._
import scala.util.Try

object Encryption {

  final def encrypt[T](value: T, keyProvider: KeyProvider)(implicit wrts: Writes[T]): String = {
    val plainText = Json.stringify(wrts.writes(value))
    try {
      val key: Key = keyProvider.keys.headOption.getOrElse(throw new Exception("Missing excryption key"))
      val cipher: Cipher = Cipher.getInstance(key.getAlgorithm)
      cipher.init(Cipher.ENCRYPT_MODE, key, cipher.getParameters)
      new String(
        Base64.encodeBase64(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8))),
        StandardCharsets.UTF_8
      )
    } catch {
      case e: Exception => throw new SecurityException("Failed encrypting data", e)
    }
  }

  final def decrypt[T](encrypted: String, keyProvider: KeyProvider)(implicit rds: Reads[T]): T =
    keyProvider.keys
      .foldLeft[Either[Unit, T]](Left(())) {
        case (Left(()), key) =>
          Try {
            val cipher: Cipher = Cipher.getInstance(key.getAlgorithm)
            cipher.init(Cipher.DECRYPT_MODE, key, cipher.getParameters)
            val plainText = new String(
              cipher.doFinal(Base64.decodeBase64(encrypted.getBytes(StandardCharsets.UTF_8))),
              StandardCharsets.UTF_8
            )
            val json = Json.parse(plainText)
            rds.reads(json) match {
              case JsSuccess(value, path) => value
              case JsError(jsonErrors) =>
                val error =
                  s"Encountered an issue with de-serialising JSON state: ${jsonErrors
                      .map { case (p, s) =>
                        s"${if (p.toString().isEmpty()) "" else s"$p -> "}${s.map(_.message).mkString(", ")}"
                      }
                      .mkString(", ")}. \nCheck if all your states have relevant entries declared in the *JourneyStateFormats.serializeStateProperties and *JourneyStateFormats.deserializeState functions."
                Logger(getClass).error(error)
                throw new Exception(error)
            }
          }.toEither.left.map(_ => ())

        case (right, _) => right
      }
      .getOrElse(throw new SecurityException(s"Failed decrypting data"))

}

trait KeyProvider {
  def keys: Seq[Key]
}

object KeyProvider {

  def apply(base64Key: String): KeyProvider = KeyProvider(Seq(base64Key))

  def apply(base64Keys: Seq[String]): KeyProvider = {
    val secretKeys: Seq[Key] = base64Keys
      .map { encryptionKey =>
        new SecretKeySpec(Base64.decodeBase64(encryptionKey.getBytes(StandardCharsets.UTF_8)), "AES")
      }
    new KeyProvider {
      override val keys: Seq[Key] = secretKeys
    }
  }

  def apply(config: Config): KeyProvider = {
    val currentEncryptionKey: String = Try(config.getString("json.encryption.key"))
      .getOrElse(throw new SecurityException(s"Missing required configuration entry: json.encryption.key"))

    val previousEncryptionKeys: Seq[String] =
      Try(config.getStringList("json.encryption.previousKeys"))
        .map(_.asScala)
        .getOrElse(Seq.empty)

    KeyProvider(currentEncryptionKey +: previousEncryptionKeys)
  }

  val padding: Array[Byte] = Array.fill[Byte](32)(-1.toByte)

  def apply(keyProvider: KeyProvider, context: Option[String]): KeyProvider = new KeyProvider {
    val secretKeys = context
      .map { s =>
        val key: Array[Byte] = (s.getBytes(StandardCharsets.UTF_8) ++ padding).take(32)
        keyProvider.keys.map { k =>
          new SecretKeySpec(xor(k.getEncoded(), key), "AES")
        }
      }
      .getOrElse(keyProvider.keys)

    override val keys: Seq[Key] = secretKeys
  }

  private def xor(a: Array[Byte], b: Array[Byte]): Array[Byte] = {
    val c: Array[Byte] = Array.ofDim[Byte](32)
    for (i <- 0 until 32)
      c.update(i, a(i).^(b(i)).toByte)
    c
  }
}
