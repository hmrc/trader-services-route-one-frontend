///*
// * Copyright 2023 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.traderservices.models
//
//import org.scalatest.wordspec.AnyWordSpec
//import org.scalatest.matchers.should.Matchers
//import play.api.libs.json._
//
//class SealedTraitFormatsSpec extends AnyWordSpec with Matchers {
//
//  sealed trait A
//  case class A1(i: Int) extends A
//  case class A2(j: String) extends A
//  case class A3(i: Int, j: String) extends A
//  case class A4(i: Int) extends A
//  val format = new SealedTraitFormats[A] {
//    val formats = Set(
//      Case[A1](Json.format[A1]),
//      Case[A2](Json.format[A2]),
//      Case[A3](Json.format[A3])
//    )
//  }
//
//  "SimpleStringFormats" should {
//    "serialize an entity as a number" in {
//      format.format.writes(A1(2)) shouldBe Json.obj("A1" -> Json.obj("i" -> JsNumber(2)))
//      format.format.writes(A1(0)) shouldBe Json.obj("A1" -> Json.obj("i" -> JsNumber(0)))
//      format.format.writes(A1(101)) shouldBe Json.obj("A1" -> Json.obj("i" -> JsNumber(101)))
//      format.format.writes(A2("2")) shouldBe Json.obj("A2" -> Json.obj("j" -> JsString("2")))
//      format.format.writes(A2("0")) shouldBe Json.obj("A2" -> Json.obj("j" -> JsString("0")))
//      format.format.writes(A2("101")) shouldBe Json.obj("A2" -> Json.obj("j" -> JsString("101")))
//      format.format.writes(A3(7, "2")) shouldBe Json.obj("A3" -> Json.obj("i" -> JsNumber(7), "j" -> JsString("2")))
//      format.format.writes(A3(77, "0")) shouldBe Json.obj(
//        "A3" -> Json.obj("i" -> JsNumber(77), "j" -> JsString("0"))
//      )
//      format.format.writes(A3(777, "101")) shouldBe Json.obj(
//        "A3" -> Json.obj("i" -> JsNumber(777), "j" -> JsString("101"))
//      )
//      an[Exception] shouldBe thrownBy(format.format.writes(A4(2)))
//    }
//
//    "de-serialize a number as an entity" in {
//      format.format.reads(Json.obj("A1" -> Json.obj("i" -> JsNumber(2)))) shouldBe JsSuccess(A1(2))
//      format.format.reads(Json.obj("A1" -> Json.obj("i" -> JsNumber(0)))) shouldBe JsSuccess(A1(0))
//      format.format.reads(Json.obj("A1" -> Json.obj("i" -> JsNumber(101)))) shouldBe JsSuccess(A1(101))
//      format.format.reads(Json.obj("A2" -> Json.obj("j" -> JsString("2")))) shouldBe JsSuccess(A2("2"))
//      format.format.reads(Json.obj("A2" -> Json.obj("j" -> JsString("0")))) shouldBe JsSuccess(A2("0"))
//      format.format.reads(Json.obj("A2" -> Json.obj("j" -> JsString("101")))) shouldBe JsSuccess(A2("101"))
//      format.format.reads(Json.obj("A3" -> Json.obj("i" -> JsNumber(7), "j" -> JsString("2")))) shouldBe JsSuccess(
//        A3(7, "2")
//      )
//      format.format.reads(
//        Json.obj(
//          "A3" -> Json.obj("i" -> JsNumber(77), "j" -> JsString("0"))
//        )
//      ) shouldBe JsSuccess(A3(77, "0"))
//      format.format.reads(
//        Json.obj(
//          "A3" -> Json.obj("i" -> JsNumber(777), "j" -> JsString("101"))
//        )
//      ) shouldBe JsSuccess(A3(777, "101"))
//
//      format.format.reads(Json.obj("A4" -> Json.obj("i" -> JsNumber(2)))) shouldBe a[JsError]
//      format.format.reads(Json.obj("A2" -> Json.obj("i" -> JsString("101")))) shouldBe a[JsError]
//      format.format.reads(Json.obj("A1" -> Json.obj("j" -> JsNumber(2)))) shouldBe a[JsError]
//      format.format.reads(Json.obj("A2" -> Json.obj())) shouldBe a[JsError]
//
//      format.format.reads(Json.obj()) shouldBe a[JsError]
//      format.format.reads(Json.obj("i" -> JsNumber(77), "j" -> JsString("0"))) shouldBe a[JsError]
//      format.format.reads(Json.obj("j" -> JsString("0"))) shouldBe a[JsError]
//      format.format.reads(Json.obj("i" -> JsNumber(77))) shouldBe a[JsError]
//      format.format.reads(JsNumber(0)) shouldBe a[JsError]
//      format.format.reads(JsNumber(2)) shouldBe a[JsError]
//      format.format.reads(JsNumber(101)) shouldBe a[JsError]
//      format.format.reads(JsNull) shouldBe a[JsError]
//      format.format.reads(Json.obj()) shouldBe a[JsError]
//      format.format.reads(JsString("2")) shouldBe a[JsError]
//      format.format.reads(JsString("0")) shouldBe a[JsError]
//      format.format.reads(JsString("101")) shouldBe a[JsError]
//      format.format.reads(JsBoolean(true)) shouldBe a[JsError]
//    }
//
//  }
//}
