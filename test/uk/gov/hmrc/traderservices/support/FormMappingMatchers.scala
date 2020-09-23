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

import org.scalatest.matchers.{MatchResult, Matcher}
import play.api.data.FormError

trait FormMappingMatchers {

  def haveError[S](expectedError: String): Matcher[Either[Seq[FormError], S]] =
    new Matcher[Either[Seq[FormError], S]] {
      override def apply(result: Either[Seq[FormError], S]): MatchResult =
        result match {
          case Right(value) =>
            MatchResult(false, s"A failure $expectedError has been expected but is success $value", s"")
          case Left(errors) if errors.exists(_.messages.contains(expectedError)) =>
            MatchResult(true, "", s"")
          case Left(errors) =>
            MatchResult(
              false,
              s"An error $expectedError has been expected but got only ${errors.flatMap(_.messages).mkString(" and ")}",
              s""
            )
        }
    }

  def haveOnlyErrors[S](expectedErrors: String*): Matcher[Either[Seq[FormError], S]] =
    new Matcher[Either[Seq[FormError], S]] {
      override def apply(result: Either[Seq[FormError], S]): MatchResult =
        result match {
          case Right(value) =>
            MatchResult(
              false,
              s"${expectedErrors.size} error(s) ${expectedErrors.mkString(" and ")} has been expected but is success $value",
              s""
            )
          case Left(errors)
              if errors.map(_.message).toSet.diff(expectedErrors.toSet).isEmpty && expectedErrors.toSet
                .diff(errors.map(_.message).toSet)
                .isEmpty =>
            MatchResult(true, "", s"")
          case Left(errors) =>
            val l = errors.map(_.message).toSet.diff(expectedErrors.toSet)
            val r = expectedErrors.toSet.diff(errors.map(_.message).toSet)
            MatchResult(
              false,
              s"Only ${expectedErrors.size} error(s) has been expected but got ${errors.size} error(s).${if (l.nonEmpty)
                s" Unexpected: ${l.mkString(" and ")}."
              else ""}${if (r.nonEmpty)
                s" Unfulfilled: ${r.mkString(" and ")}."
              else ""}",
              s""
            )
        }
    }

  def haveOnlyError[S](expectedError: String): Matcher[Either[Seq[FormError], S]] =
    new Matcher[Either[Seq[FormError], S]] {
      override def apply(result: Either[Seq[FormError], S]): MatchResult =
        result match {
          case Right(value) =>
            MatchResult(false, s"An error $expectedError has been expected but is success $value", s"")
          case Left(errors) if errors.size == 1 && errors.map(_.message).contains(expectedError) =>
            MatchResult(true, "", s"")
          case Left(errors) =>
            MatchResult(
              false,
              s"An error(s) $expectedError has been expected but got ${errors.size} error(s): ${errors.flatMap(_.messages).mkString(" and ")}",
              s""
            )
        }
    }

}
