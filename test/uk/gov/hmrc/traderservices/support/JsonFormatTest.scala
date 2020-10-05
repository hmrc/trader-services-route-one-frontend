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

package uk.gov.hmrc.traderservices.support

import org.scalatest.Assertion
import org.scalatest.Matchers._
import play.api.libs.json.{Format, Json}
import org.scalatest.Informer

abstract class JsonFormatTest[A: Format](info: Informer) {

  case class TestEntity(entity: A)
  implicit val testFormat: Format[TestEntity] = Json.format[TestEntity]

  def validateJsonFormat(value: String, entity: A): Assertion = {
    info(nameOf(entity))
    val json = s"""{"entity":${if (value.startsWith("{")) value else s""""$value""""}}"""
    Json.parse(json).as[TestEntity].entity shouldBe entity
    Json.stringify(Json.toJson(TestEntity(entity))) shouldBe json
  }

  val localPackagePrefix = "class uk.gov.hmrc.traderservices."

  def nameOf(entity: A): String = {
    val s = entity.getClass.toString.replace("$", ".")
    val s1 = if (s.endsWith(".")) s.dropRight(1) else s
    if (s1.startsWith(localPackagePrefix)) s1.drop(localPackagePrefix.length)
    else s1

  }

}
