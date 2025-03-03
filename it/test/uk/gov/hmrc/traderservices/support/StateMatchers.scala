/*
 * Copyright 2025 HM Revenue & Customs
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

import org.scalatest.matchers.Matcher
import org.scalatest.matchers.MatchResult
import uk.gov.hmrc.traderservices.utils.IdentityUtils.identityOf

import scala.io.AnsiColor

trait StateMatchers {

  final def beState(expected: AnyRef): Matcher[AnyRef] =
    new Matcher[AnyRef] {
      override def apply(obtained: AnyRef): MatchResult =
        if (obtained == expected)
          MatchResult(true, "", s"")
        else if (identityOf(obtained) != identityOf(expected))
          MatchResult(
            false,
            s"State ${AnsiColor.CYAN}${identityOf(
                expected
              )}${AnsiColor.RESET} has been expected but got state ${AnsiColor.CYAN}${identityOf(obtained)}${AnsiColor.RESET}",
            s""
          )
        else {
          val diff = Diff(obtained, expected)
          MatchResult(
            false,
            s"Obtained state ${AnsiColor.CYAN}${identityOf(obtained)}${AnsiColor.RESET} content differs from the expected:\n$diff}",
            s""
          )
        }

    }

}
