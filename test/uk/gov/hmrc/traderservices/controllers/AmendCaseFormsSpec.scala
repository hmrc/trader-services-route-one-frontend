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
//package uk.gov.hmrc.traderservices.controllers
//
//import uk.gov.hmrc.traderservices.models.TypeOfAmendment
//import uk.gov.hmrc.traderservices.support.FormValidator
//import scala.util.Random
//import uk.gov.hmrc.traderservices.support.UnitSpec
//
//class AmendCaseFormsSpec extends UnitSpec with FormValidator {
//
//  "Amend case forms" should {
//    "bind some typeOfAmendment and return TypeOfAmendment, and fill it back" in {
//      val form = AmendCaseJourneyController.TypeOfAmendmentForm
//      validate(form, Map("typeOfAmendment" -> "WriteResponse"), TypeOfAmendment.WriteResponse)
//      validate(form, Map("typeOfAmendment" -> "UploadDocuments"), TypeOfAmendment.UploadDocuments)
//      validate(
//        form,
//        Map("typeOfAmendment" -> "WriteResponseAndUploadDocuments"),
//        TypeOfAmendment.WriteResponseAndUploadDocuments
//      )
//      validate(form, "typeOfAmendment", Map(), Seq("error.typeOfAmendment.required"))
//      validate(form, "typeOfAmendment", Map("typeOfAmendment" -> "Foo"), Seq("error.typeOfAmendment.invalid-option"))
//    }
//
//    "bind and validate response text" in {
//      val form = AmendCaseJourneyController.ResponseTextForm
//      val validTextSample = Random.alphanumeric.take(1000).mkString
//      validate(form, Map("responseText" -> validTextSample), validTextSample)
//      validate(
//        form,
//        "responseText",
//        Map("responseText" -> Random.alphanumeric.take(1001).mkString),
//        Seq("error.responseText.invalid-length")
//      )
//      validate(
//        form,
//        "responseText",
//        Map("responseText" -> ""),
//        Seq("error.responseText.required")
//      )
//      validateAsymmetric(
//        form,
//        Map("responseText" -> "abc 123 \u2061\u2062\u2063 xyz"),
//        "abc 123  xyz",
//        Map("responseText" -> "abc 123  xyz")
//      )
//    }
//
//  }
//}
