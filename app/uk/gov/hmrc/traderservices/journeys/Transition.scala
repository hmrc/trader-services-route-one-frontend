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

package uk.gov.hmrc.traderservices.journeys

import scala.concurrent.{ExecutionContext, Future}

final class Transition[A] private (val apply: PartialFunction[A, Future[A]]) {

  /** Composes this transition with the fallback transition which gets applied where this transition is not defined for
    * the curent argument.
    */
  def orElse(fallback: Transition[A]): Transition[A] =
    Transition(apply.orElse(fallback.apply))

  /** Composes this transition with the next transition which gets applied to the result of this transition, if
    * successful.
    */
  def andThen(next: Transition[A])(implicit ec: ExecutionContext): Transition[A] =
    Transition(apply.andThen(_.flatMap(value => next.apply(value))))
}

object Transition {
  def apply[A](rules: PartialFunction[A, Future[A]]): Transition[A] =
    new Transition(rules)
}

case class TransitionNotAllowed[A](
  value: A,
  breadcrumbs: List[A],
  transition: Transition[A]
) extends Exception
