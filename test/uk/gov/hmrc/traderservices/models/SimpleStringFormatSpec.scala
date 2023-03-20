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
//class SimpleStringFormatsSpec extends AnyWordSpec with Matchers {
//
//  case class A(i: Int)
//  val format = SimpleStringFormat[A](s => A(s.drop(1).toInt), a => s"A${a.i}")
//
//  "SimpleStringFormats" should {
//    "serialize an entity as string" in {
//      format.writes(A(2)) shouldBe JsString("A2")
//      format.writes(A(0)) shouldBe JsString("A0")
//      format.writes(A(101)) shouldBe JsString("A101")
//    }
//
//    "de-serialize a string as an entity" in {
//      format.reads(JsString("A2")) shouldBe JsSuccess(A(2))
//      format.reads(JsString("A0")) shouldBe JsSuccess(A(0))
//      format.reads(JsString("A101")) shouldBe JsSuccess(A(101))
//      format.reads(JsNull) shouldBe a[JsError]
//      format.reads(Json.obj()) shouldBe a[JsError]
//      format.reads(JsNumber(2)) shouldBe a[JsError]
//      format.reads(JsBoolean(true)) shouldBe a[JsError]
//    }
//
//  }
//}
