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

  sealed trait AmendCaseState extends State {
    def model: AmendCaseStateModel
  }

  /** All the possible states the journey can take. */
  object State {

    /** State intended to use only in the development of the model to fill loose ends. */
    case object WorkInProgressDeadEnd extends State

    /** Root state of the journey. */
    case class EnterCaseReferenceNumber(model: AmendCaseStateModel = AmendCaseStateModel()) extends AmendCaseState

    case class SelectTypeOfAmendment(model: AmendCaseStateModel) extends AmendCaseState

    case class EnterResponseText(model: AmendCaseStateModel) extends AmendCaseState

    case class AmendCaseConfirmation(model: AmendCaseStateModel) extends AmendCaseState
  }

  /** This is where things happen a.k.a bussiness logic of the service. */
  object Transitions {
    import State._

    final def enterCaseReferenceNumber(user: String) =
      Transition {
        case s: AmendCaseState =>
          goto(EnterCaseReferenceNumber(s.model))

        case _ =>
          goto(EnterCaseReferenceNumber())
      }

    final def submitedCaseReferenceNumber(user: String)(caseReferenceNumber: String) =
      Transition {
        case EnterCaseReferenceNumber(model) =>
          goto(SelectTypeOfAmendment(model.copy(caseReferenceNumber = Some(caseReferenceNumber))))
      }

    final def backToSelectTypeOfAmendment(user: String) =
      Transition {
        case s: AmendCaseState =>
          goto(SelectTypeOfAmendment(s.model))
      }

    final def submitedTypeOfAmendment(user: String)(typeOfAmendment: TypeOfAmendment) =
      Transition {
        case SelectTypeOfAmendment(model) =>
          typeOfAmendment match {
            case TypeOfAmendment.WriteResponse =>
              goto(EnterResponseText(model.copy(typeOfAmendment = Some(typeOfAmendment))))

            case TypeOfAmendment.WriteResponseAndUploadDocuments =>
              goto(EnterResponseText(model.copy(typeOfAmendment = Some(typeOfAmendment))))

            case TypeOfAmendment.UploadDocuments =>
              val updatedModel = model.copy(typeOfAmendment = Some(typeOfAmendment), responseText = None)
              goto(WorkInProgressDeadEnd)
          }
      }

    final def backToEnterResponseText(user: String) =
      Transition {
        case s: AmendCaseState =>
          goto(EnterResponseText(s.model))
      }

    final def submitedResponseText(user: String)(responseText: String) =
      Transition {
        case EnterResponseText(model)
            if model.hasTypeOfAmendment(
              TypeOfAmendment.WriteResponse,
              TypeOfAmendment.WriteResponseAndUploadDocuments
            ) =>
          if (model.typeOfAmendment.contains(TypeOfAmendment.WriteResponseAndUploadDocuments))
            goto(WorkInProgressDeadEnd)
          else
            goto(AmendCaseConfirmation(model.copy(responseText = Some(responseText))))
      }
  }
}
