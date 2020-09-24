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

import java.time.{LocalDate, ZoneId}

import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.play.fsm.JourneyModel

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object TraderServicesFrontendJourneyModel extends JourneyModel {

  sealed trait State
  sealed trait IsError

  override val root: State = State.Start

  object State {

    case object Start extends State

    case class EnterConsignmentDetails(consignmentDetailsOpt: Option[ConsignmentDetails]) extends State

    case object WorkInProgressDeadEnd extends State

  }

  object Transitions {
    import State._

    def start(user: String) =
      Transition {
        case _ =>
          goto(Start)
      }

    def enterConsignmentDetails(user: String) =
      Transition {
        case _ =>
          goto(EnterConsignmentDetails(None))
      }

    def submittedConsignmentDetails(user: String)(consignmentDetails: ConsignmentDetails) =
      Transition {
        case EnterConsignmentDetails(_) =>
          goto(WorkInProgressDeadEnd)
      }
  }

}
