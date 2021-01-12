/*
 * Copyright 2021 HM Revenue & Customs
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

import org.scalatest.matchers.{MatchResult, Matcher}
import play.api.data.FormError

trait FormMatchers {

  def haveError(expectedError: FormError): Matcher[Seq[FormError]] =
    new Matcher[Seq[FormError]] {
      override def apply(errors: Seq[FormError]): MatchResult =
        if (errors.find(_.key == expectedError.key).exists(_.messages == expectedError.messages))
          MatchResult(true, "", s"")
        else
          MatchResult(
            false,
            s"An error $expectedError has been expected but got only ${errors.flatMap(_.messages).mkString(" and ")}",
            s""
          )
    }

  def haveOnlyErrors(expectedErrors: FormError*): Matcher[Seq[FormError]] =
    new Matcher[Seq[FormError]] {
      override def apply(errors: Seq[FormError]): MatchResult = {
        val found = errors.map(e => (e.key, e.messages)).toSet
        val expected = expectedErrors.map(e => (e.key, e.messages)).toSet
        val unexpected = found.diff(expected)
        val unfulfilled = expected.diff(found)
        if (found == expected)
          MatchResult(true, "", s"")
        else
          MatchResult(
            false,
            s"Only ${expectedErrors.size} error(s) has been expected but got ${errors.size} error(s).${if (unexpected.nonEmpty)
              s" Unexpected: ${unexpected.mkString(" and ")}."
            else ""}${if (unfulfilled.nonEmpty)
              s" Unfulfilled: ${unfulfilled.mkString(" and ")}."
            else ""}",
            s""
          )
      }
    }

  def haveOnlyError(expectedError: FormError): Matcher[Seq[FormError]] =
    new Matcher[Seq[FormError]] {
      override def apply(errors: Seq[FormError]): MatchResult =
        if (errors.size == 1 && errors.find(_.key == expectedError.key).exists(_.messages == expectedError.messages))
          MatchResult(true, "", s"")
        else
          MatchResult(
            false,
            s"An error(s) $expectedError has been expected but got ${errors.size} error(s): ${errors.flatMap(_.messages).mkString(" and ")}",
            s""
          )
    }

}
