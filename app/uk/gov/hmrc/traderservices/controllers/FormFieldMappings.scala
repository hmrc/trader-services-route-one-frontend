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

import play.api.data.Forms.of
import play.api.data.Mapping
import play.api.data.format.Formats._
import play.api.data.validation._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.traderservices.controllers.DateFieldHelper._

object FormFieldMappings {

  def dateOfBirthMapping: Mapping[String] = dateFieldsMapping(validDobDateFormat)

  def validNino(
    nonEmptyFailure: String = "error.nino.required",
    invalidFailure: String = "error.nino.invalid-format"
  ): Constraint[String] =
    ValidateHelper.validateField(nonEmptyFailure, invalidFailure)(nino => Nino.isValid(nino))

  val maxNameLen = 64

  val normalizedText: Mapping[String] = of[String].transform(_.replaceAll("\\s", ""), identity)
  val uppercaseNormalizedText: Mapping[String] = normalizedText.transform(_.toUpperCase, identity)
  val trimmedName: Mapping[String] = of[String].transform[String](_.trim.take(maxNameLen), identity)

  val allowedNameCharacters: Set[Char] = Set('-', '\'', ' ')

  def validName(fieldName: String, minLenInc: Int): Constraint[String] =
    Constraint[String] { fieldValue: String =>
      nonEmpty(fieldName)(fieldValue) match {
        case i @ Invalid(_) => i
        case Valid =>
          if (
            fieldValue.length >= minLenInc && fieldValue
              .forall(ch => Character.isLetter(ch) || allowedNameCharacters.contains(ch))
          )
            Valid
          else
            Invalid(ValidationError(s"error.$fieldName.invalid-format"))
      }
    }

  def nonEmpty(fieldName: String): Constraint[String] =
    Constraint[String]("constraint.required") { s =>
      Option(s)
        .filter(!_.trim.isEmpty)
        .fold[ValidationResult](Invalid(ValidationError(s"error.$fieldName.required")))(_ => Valid)
    }
}
