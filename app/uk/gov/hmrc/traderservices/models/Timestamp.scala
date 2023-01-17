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

import play.api.libs.json.Format
import java.time.format.DateTimeFormatter
import java.time._

/** Testing and serialization friendly timestamp wrapper. */
sealed trait Timestamp {

  val value: Long

  def isAfter(other: Timestamp, minGapMillis: Long): Boolean

  final override def hashCode(): Int =
    value.toInt

  final override def toString: String =
    DateTimeFormatter.ISO_LOCAL_TIME
      .format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneId.of("UTC")))
}

object Timestamp {

  final def now: Timestamp =
    new Strict(System.currentTimeMillis())

  final def apply(value: Long): Timestamp =
    new Strict(value)

  object Any extends Timestamp {
    final val value: Long = 0

    final override def equals(obj: scala.Any): Boolean =
      obj.isInstanceOf[Timestamp]

    final def isAfter(other: Timestamp, minGapMillis: Long): Boolean =
      true
  }

  final class Strict(val value: Long) extends Timestamp {
    final override def equals(obj: scala.Any): Boolean =
      if (obj.isInstanceOf[Any.type]) true
      else if (obj.isInstanceOf[Timestamp])
        obj.asInstanceOf[Timestamp].value == value
      else false

    final def isAfter(other: Timestamp, minGapMillis: Long): Boolean =
      if (other.isInstanceOf[Timestamp.Any.type])
        true
      else
        (other.value + minGapMillis) < value

  }

  implicit final val formats: Format[Timestamp] =
    SimpleDecimalFormat[Timestamp](s => Timestamp(s.toLongExact), n => BigDecimal(n.value))
}
