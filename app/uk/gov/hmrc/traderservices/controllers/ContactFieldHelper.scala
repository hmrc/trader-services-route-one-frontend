/*
 * Copyright 2022 HM Revenue & Customs
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

import com.google.i18n.phonenumbers.{NumberParseException, PhoneNumberUtil}
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}

object ContactFieldHelper {

  val allowedSpecialNumberCharacterSet = Set(' ', '-', '(', ')')

  def contactNumber(errorMessage: String = "error.contactNumber"): Constraint[String] =
    Constraint[String]("constraint.contactNumber") { phoneNum =>
      if (phoneNum == null) Invalid(ValidationError(errorMessage))
      else if (phoneNum.trim.isEmpty) Invalid(ValidationError(errorMessage))
      else
        try {
          val phoneNumberUtil = PhoneNumberUtil.getInstance()
          val sequenceToNumber = phoneNumberUtil.parse(phoneNum, "GB")
          if (!phoneNum.forall(_.isDigit)) Invalid(ValidationError(errorMessage))
          else if (phoneNum.length != 11) Invalid(ValidationError(errorMessage))
          else if (phoneNumberUtil.isValidNumber(sequenceToNumber)) Valid
          else Invalid(ValidationError(errorMessage))
        } catch {
          case _: NumberParseException => Invalid(ValidationError(errorMessage))
        }
    }

  def normaliseNumber(phoneNum: String): String = {
    val updatedNum = removeNonAllowedCharacters(phoneNum)
    val prefixes = List("+44", "44", "0044")
    prefixes.find(updatedNum.startsWith(_)) match {
      case Some(n) => updatedNum.replace(n, "0")
      case None    => updatedNum
    }
  }

  def removeNonAllowedCharacters(phoneNum: String): String =
    phoneNum.map(c => if (allowedSpecialNumberCharacterSet.contains(c)) "" else c).mkString("")

  def replayNumber(phoneNum: String): String =
    phoneNum.patch(5, " ", 0).patch(9, " ", 0)

}
