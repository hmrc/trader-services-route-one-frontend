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

package uk.gov.hmrc.traderservices.journeys

import uk.gov.hmrc.traderservices.models._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import uk.gov.hmrc.play.fsm.JourneyModel

object AmendCaseJourneyModel extends JourneyModel {

  sealed trait State
  sealed trait IsError
  sealed trait IsTransient

  override val root: State = State.EnterCaseReferenceNumber()

  /** Model parametrization and rules. */
  object Rules {}

  trait HasCaseReferenceNumber {
    def caseReferenceNumber: String
  }

  /** All the possible states the journey can take. */
  object State {

    /** State intended to use only in the development of the model to fill loose ends. */
    case object WorkInProgressDeadEnd extends State

    /** Root state of the journey. */
    case class EnterCaseReferenceNumber(caseReferenceNumberOpt: Option[String] = None) extends State

    case class SelectTypeOfAmendment(caseReferenceNumber: String) extends State with HasCaseReferenceNumber
  }

  /** This is where things happen a.k.a bussiness logic of the service. */
  object Transitions {
    import State._

    final def enterCaseReferenceNumber(user: String) =
      Transition {
        case s: HasCaseReferenceNumber =>
          goto(EnterCaseReferenceNumber(Some(s.caseReferenceNumber)))

        case _ =>
          goto(EnterCaseReferenceNumber(None))
      }

    final def submitedCaseReferenceNumber(user: String)(caseReferenceNumber: String) =
      Transition {
        case EnterCaseReferenceNumber(_) =>
          goto(SelectTypeOfAmendment(caseReferenceNumber))
      }

  }
}
