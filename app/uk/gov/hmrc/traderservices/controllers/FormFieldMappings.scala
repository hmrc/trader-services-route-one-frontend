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

package uk.gov.hmrc.traderservices.controllers

import java.time.{LocalDate, LocalTime}

import play.api.data.Forms.{of, optional, text}
import play.api.data.Mapping
import play.api.data.format.Formats._
import play.api.data.validation._
import uk.gov.hmrc.traderservices.models._

import scala.util.Try

object FormFieldMappings {

  val normalizedText: Mapping[String] = of[String].transform(_.replaceAll("\\s", ""), identity)
  val uppercaseNormalizedText: Mapping[String] = normalizedText.transform(_.toUpperCase, identity)

  def nonEmpty(fieldName: String): Constraint[String] =
    Constraint[String]("constraint.required") { s =>
      Option(s)
        .filter(!_.trim.isEmpty)
        .fold[ValidationResult](Invalid(ValidationError(s"error.$fieldName.required")))(_ => Valid)
    }

  def haveLength(fieldName: String, expectedLength: Int): Constraint[String] =
    Constraint[String]("constraint.length") { s =>
      Option(s)
        .filter(_.length == expectedLength)
        .fold[ValidationResult](Invalid(ValidationError(s"error.$fieldName.invalid-length")))(_ => Valid)
    }

  def constraint[A](fieldName: String, errorType: String, predicate: A => Boolean): Constraint[A] =
    Constraint[A](s"constraint.$fieldName.$errorType") { s =>
      Option(s)
        .filter(predicate)
        .fold[ValidationResult](Invalid(ValidationError(s"error.$fieldName.$errorType")))(_ => Valid)
    }

  def first[A](cs: Constraint[A]*): Constraint[A] =
    Constraint[A](s"constraints.sequence.${cs.map(_.name).mkString(".")}") { s =>
      cs.foldLeft[ValidationResult](Valid) { (r, c) =>
        r match {
          case Valid => c.apply(s)
          case r     => r
        }
      }
    }

  def all[A](cs: Constraint[A]*): Constraint[A] =
    Constraint[A](s"constraints.sequence.${cs.map(_.name).mkString(".")}") { s =>
      cs.foldLeft[ValidationResult](Valid) { (r, c) =>
        r match {
          case Valid => c.apply(s)
          case r @ Invalid(e1) =>
            c.apply(s) match {
              case Valid       => r
              case Invalid(e2) => Invalid(e1 ++ e2)
            }
        }
      }
    }

  def some[A](c: Constraint[A]): Constraint[Option[A]] =
    Constraint[Option[A]](s"${c.name}.optional") {
      case Some(s) => c.apply(s)
      case None    => Valid
    }

  val epuMapping: Mapping[EPU] = uppercaseNormalizedText
    .verifying(
      first(
        nonEmpty("epu"),
        all(haveLength("epu", 3), constraint[String]("epu", "invalid-only-digits", _.forall(_.isDigit))),
        constraint[String]("epu", "invalid-number", s => Try(s.toInt).fold(_ => true, _ <= 700))
      )
    )
    .transform(s => EPU(s.toInt), _.value.toString)

  val entryNumberMapping: Mapping[EntryNumber] = uppercaseNormalizedText
    .verifying(
      first(
        nonEmpty("entryNumber"),
        all(
          haveLength("entryNumber", 7),
          constraint[String]("entryNumber", "invalid-only-digits-and-letters", _.forall(_.isLetterOrDigit)),
          constraint[String](
            "entryNumber",
            "invalid-ends-with-letter",
            s => s.lastOption.forall(_.isLetter) || s.drop(6).headOption.forall(_.isLetter)
          ),
          constraint[String]("entryNumber", "invalid-letter-wrong-position", _.slice(1, 6).forall(_.isDigit))
        )
      )
    )
    .transform(EntryNumber.apply, _.value)

  val entryDateMapping: Mapping[LocalDate] = DateFieldHelper
    .dateFieldsMapping("entryDate")
    .verifying(DateFieldHelper.dateIsBefore("entryDate.all", "invalid-value-future", _.plusDays(1)))
    .verifying(DateFieldHelper.dateIsAfter("entryDate.all", "invalid-value-past", _.minusMonths(6)))

  def enumMapping[A: EnumerationFormats](fieldName: String): Mapping[A] =
    optional(text)
      .verifying(constraint[Option[String]](fieldName, "required", _.isDefined))
      .transform[String](_.get, Option.apply)
      .verifying(constraint(fieldName, "invalid-option", implicitly[EnumerationFormats[A]].isValidKey))
      .transform(implicitly[EnumerationFormats[A]].valueOf(_).get, implicitly[EnumerationFormats[A]].keyOf(_).get)

  def booleanMapping(fieldName: String, trueValue: String, falseValue: String): Mapping[Boolean] =
    optional(text)
      .verifying(constraint[Option[String]](fieldName, "required", _.exists(s => s == trueValue || s == falseValue)))
      .transform[Boolean](_.contains(trueValue), b => if (b) Some(trueValue) else Some(falseValue))

  val exportRequestTypeMapping: Mapping[ExportRequestType] = enumMapping[ExportRequestType]("exportRequestType")

  val importRequestTypeMapping: Mapping[ImportRequestType] = enumMapping[ImportRequestType]("importRequestType")

  val exportRouteTypeMapping: Mapping[ExportRouteType] = enumMapping[ExportRouteType]("exportRouteType")

  val importRouteTypeMapping: Mapping[ImportRouteType] = enumMapping[ImportRouteType]("importRouteType")

  val exportHasPriorityGoodsMapping: Mapping[Boolean] = booleanMapping("exportHasPriorityGoods", "yes", "no")

  val importHasPriorityGoodsMapping: Mapping[Boolean] = booleanMapping("importHasPriorityGoods", "yes", "no")

  val exportPriorityGoodsMapping: Mapping[ExportPriorityGoods] = enumMapping[ExportPriorityGoods]("exportPriorityGoods")

  val importPriorityGoodsMapping: Mapping[ImportPriorityGoods] = enumMapping[ImportPriorityGoods]("importPriorityGoods")

  val exportFreightTypeMapping: Mapping[ExportFreightType] = enumMapping[ExportFreightType]("exportFreightType")

  val importFreightTypeMapping: Mapping[ImportFreightType] = enumMapping[ImportFreightType]("importFreightType")

  val importHasALVSMapping: Mapping[Boolean] = booleanMapping("importHasALVS", "yes", "no")

  val allowedSpecialNameCharacterSet = Set(' ', '/', '\\', '_', '-', '&', '+', '\'', '.')

  val mandatoryVesselNameMapping: Mapping[Option[String]] = of[String]
    .transform(_.trim.replaceAll("\\s{2,128}", " "), identity[String])
    .verifying(
      first(
        constraint[String]("vesselName", "required", _.length > 0),
        all(
          constraint[String]("vesselName", "invalid-length", _.length <= 128),
          constraint[String](
            "vesselName",
            "invalid-characters",
            name =>
              name.exists(Character.isLetterOrDigit) &&
                name.forall(c => Character.isLetterOrDigit(c) || allowedSpecialNameCharacterSet.contains(c))
          )
        )
      )
    )
    .transform(Option.apply, _.get)

  val optionalVesselNameMapping: Mapping[Option[String]] = optional(
    of[String]
      .transform(_.trim.replaceAll("\\s{2,128}", " "), identity[String])
      .verifying(
        all(
          constraint[String]("vesselName", "invalid-length", name => name.isEmpty || name.length <= 128),
          constraint[String](
            "vesselName",
            "invalid-characters",
            name =>
              name.isEmpty || (name.exists(Character.isLetterOrDigit) &&
                name.forall(c => Character.isLetterOrDigit(c) || allowedSpecialNameCharacterSet.contains(c)))
          )
        )
      )
  ).transform({ case Some("") => None; case o => o }, identity[Option[String]])

  val dateOfArrivalRangeConstraint = some(
    first(
      DateFieldHelper.dateIsBefore("dateOfArrival.all", "invalid-value-future", _.plusMonths(6).plusDays(1)),
      DateFieldHelper.dateIsAfter("dateOfArrival.all", "invalid-value-past", _.minusMonths(6).minusDays(1))
    )
  )

  val mandatoryDateOfArrivalMapping: Mapping[Option[LocalDate]] =
    DateFieldHelper
      .dateFieldsMapping("dateOfArrival")
      .transform(Option.apply, _.get)

  val mandatoryTimeOfArrivalMapping: Mapping[Option[LocalTime]] =
    TimeFieldHelper.timeFieldsMapping("timeOfArrival").transform(Option.apply, _.get)

  val optionalDateOfArrivalMapping: Mapping[Option[LocalDate]] =
    DateFieldHelper.optionalDateFieldsMapping("dateOfArrival")

  val optionalTimeOfArrivalMapping: Mapping[Option[LocalTime]] =
    TimeFieldHelper.optionalTimeFieldsMapping("timeOfArrival")

  val importContactEmailMapping: Mapping[Option[String]] = optional(
    of[String].verifying(Constraints.emailAddress(errorMessage = "error.contactEmail"))
  )

  val importContactNumberMapping: Mapping[Option[String]] = optional(
    of[String].verifying(ContactFieldHelper.contactNumber())
  )
}
