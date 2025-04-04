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

import scala.concurrent.Future

/** Journey Model is a base trait of a Finite State Machine, consisting of states and transitions modelling the logic of
  * the business process flow.
  */
trait JourneyModel {

  val root: State

  /** Replace the current state with the new one. */
  final def goto(state: State): Future[State] =
    Future.successful(state)

  /** Fail the transition */
  final def fail(exception: Exception): Future[State] =
    Future.failed(exception)

  /** Stay in the current state */
  final def stay: Future[State] =
    Future.failed(StayInCurrentState)

  final case object StayInCurrentState extends Exception

  /** Merger is a partial function of type `(S <: State, State) => S`, used to reconcile current and previous states
    * when rolling back the journey.
    */
  final class Merger[S <: State] private (val apply: PartialFunction[(S, State), S]) {

    /** Converts merger into modification by partially applying donor state parameter.
      */
    def withState(state: State): S => S = { s: S =>
      if (apply.isDefinedAt((s, state))) apply((s, state))
      else s
    }
  }

  /** Merger builder helper */
  protected final object Merger {
    def apply[S <: State](merge: PartialFunction[(S, State), S]): Merger[S] =
      new Merger(merge)
  }
}
