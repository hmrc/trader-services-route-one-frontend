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
//package uk.gov.hmrc.traderservices.generators
//
//import org.scalacheck.Arbitrary.arbitrary
//import org.scalacheck.Gen.alphaChar
//import org.scalacheck.{Arbitrary, Gen}
//import uk.gov.hmrc.traderservices.controllers.FormFieldMappings.allowedSpecialNameCharacterSet
//import uk.gov.hmrc.traderservices.models._
//
//import java.time.LocalDate
//import scala.util.Random
//
//trait FormFieldGenerators {
//
//  implicit lazy val arbitraryExportRequestType: Arbitrary[ExportRequestType] =
//    Arbitrary {
//      Gen.oneOf(ExportRequestType.values)
//    }
//
//  implicit lazy val arbitraryImportRequestType: Arbitrary[ImportRequestType] =
//    Arbitrary {
//      Gen.oneOf(ImportRequestType.values)
//    }
//
//  implicit lazy val arbitraryImportRouteType: Arbitrary[ImportRouteType] =
//    Arbitrary {
//      Gen.oneOf(ImportRouteType.values)
//    }
//
//  implicit lazy val arbitraryExportRouteType: Arbitrary[ExportRouteType] =
//    Arbitrary {
//      Gen.oneOf(ExportRouteType.values)
//    }
//
//  implicit lazy val arbitraryExportPriorityGoods: Arbitrary[ExportPriorityGoods] =
//    Arbitrary {
//      Gen.oneOf(ExportPriorityGoods.values)
//    }
//
//  implicit lazy val arbitraryImportPriorityGoods: Arbitrary[ImportPriorityGoods] =
//    Arbitrary {
//      Gen.oneOf(ImportPriorityGoods.values)
//    }
//
//  implicit lazy val arbitraryExportFreightType: Arbitrary[ExportFreightType] =
//    Arbitrary {
//      Gen.oneOf(ExportFreightType.values)
//    }
//
//  implicit lazy val arbitraryImportFreightType: Arbitrary[ImportFreightType] =
//    Arbitrary {
//      Gen.oneOf(ImportFreightType.values)
//    }
//
//  def importRequestTypeGen: Gen[ImportRequestType] =
//    for {
//      requestType <- arbitrary[ImportRequestType]
//    } yield requestType
//
//  def exportRequestTypeGen: Gen[ExportRequestType] =
//    for {
//      requestType <- arbitrary[ExportRequestType]
//    } yield requestType
//
//  def importRouteTypeGen: Gen[ImportRouteType] =
//    for {
//      routeType <- arbitrary[ImportRouteType]
//    } yield routeType
//
//  def exportRouteTypeGen: Gen[ExportRouteType] =
//    for {
//      routeType <- arbitrary[ExportRouteType]
//    } yield routeType
//
//  def exportPriorityGoodsGen: Gen[ExportPriorityGoods] =
//    for {
//      priorityGoods <- arbitrary[ExportPriorityGoods]
//    } yield priorityGoods
//
//  def importPriorityGoodsGen: Gen[ImportPriorityGoods] =
//    for {
//      priorityGoods <- arbitrary[ImportPriorityGoods]
//    } yield priorityGoods
//
//  def exportFreightTypeGen: Gen[ExportFreightType] =
//    for {
//      priorityGoods <- arbitrary[ExportFreightType]
//    } yield priorityGoods
//
//  def importFreightTypeGen: Gen[ImportFreightType] =
//    for {
//      priorityGoods <- arbitrary[ImportFreightType]
//    } yield priorityGoods
//
//  def stringGen: Gen[String] = Gen.nonEmptyListOf[Char](alphaChar).map(_.mkString).suchThat(_ != "\"\"")
//
//  def allowedSpecialCharGen: Gen[Char] = Gen.oneOf(allowedSpecialNameCharacterSet).suchThat(_.toString.nonEmpty)
//
//  def invalidSpecialCharGen: Gen[Char] =
//    Gen
//      .oneOf(
//        Set('!', '#', '$', '%', '(', ')', '*', ',', ':', ';', '<', '>', '=', '?', '@', '[', ']', '^', '|', '~', '{',
//          '}')
//      )
//      .suchThat(_.toString.nonEmpty)
//
//  def yesNoGen: Gen[String] = Gen.oneOf(Seq("yes", "no"))
//
//  def pastDateGen: Gen[LocalDate] = Gen.chooseNum(1, 364).map(LocalDate.now.minusDays(_))
//
//  def hourGen: Gen[Int] = Gen.choose(0, 23)
//  def minutesGen: Gen[Int] = Gen.choose(0, 59)
//
//  def entryNumberGen: Gen[String] =
//    for {
//      number <- Gen.choose(100000, 999999).map(_.toString)
//      suffix <- Gen.alphaChar.map(_.toUpper)
//    } yield number + suffix
//
//  def invalidPhoneNumberGen: Gen[String] = Gen.numStr.suchThat(num => num.nonEmpty && num.length != 11)
//
//  def textGreaterThan(length: Int): String = Random.alphanumeric.take(length + 1).mkString
//
//  def yesNoConversion(str: String): Boolean =
//    str.toLowerCase match {
//      case "yes" => true
//      case "no"  => false
//    }
//}
