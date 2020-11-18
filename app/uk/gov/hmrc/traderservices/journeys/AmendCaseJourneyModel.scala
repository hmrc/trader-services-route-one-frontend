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
import uk.gov.hmrc.traderservices.connectors.UpscanInitiateRequest

object AmendCaseJourneyModel extends FileUploadJourneyModelMixin {

  sealed trait IsError

  override val root: State = State.EnterCaseReferenceNumber()

  sealed trait AmendCaseState extends State {
    def model: AmendCaseModel
  }

  // FileUploadJourneyModel customization

  override val maxFileUploadsNumber: Int = 10

  override def retreatFromFileUpload: String => Transition =
    Transitions.backFromFileUploadState

  /** Opaque data carried through the file upload process. */
  override type FileUploadHostData = AmendCaseModel

  /** All the possible states the journey can take. */
  object State {

    /** State intended to use only in the development of the model to fill loose ends. */
    case object WorkInProgressDeadEnd extends State

    /** Root state of the journey. */
    case class EnterCaseReferenceNumber(model: AmendCaseModel = AmendCaseModel()) extends AmendCaseState

    case class SelectTypeOfAmendment(model: AmendCaseModel) extends AmendCaseState

    case class EnterResponseText(model: AmendCaseModel) extends AmendCaseState

    case class AmendCaseConfirmation(model: AmendCaseModel) extends State
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
        case s: AmendCaseState if s.model.typeOfAmendment.isDefined =>
          goto(SelectTypeOfAmendment(s.model))
      }

    final def submitedTypeOfAmendment(
      upscanRequest: UpscanInitiateRequest
    )(
      upscanInitiate: UpscanInitiateApi
    )(user: String)(typeOfAmendment: TypeOfAmendment)(implicit ec: ExecutionContext) =
      Transition {
        case SelectTypeOfAmendment(model) =>
          val updatedModel = model.copy(typeOfAmendment = Some(typeOfAmendment))
          typeOfAmendment match {
            case TypeOfAmendment.WriteResponse =>
              goto(EnterResponseText(updatedModel))

            case TypeOfAmendment.WriteResponseAndUploadDocuments =>
              goto(EnterResponseText(updatedModel))

            case TypeOfAmendment.UploadDocuments =>
              gotoFileUploadOrUploaded(
                updatedModel.copy(responseText = None),
                upscanRequest,
                upscanInitiate,
                model.fileUploads,
                showUploadSummaryIfAny = true
              )
          }
      }

    final def backToEnterResponseText(user: String) =
      Transition {
        case s: AmendCaseState if s.model.responseText.isDefined =>
          goto(EnterResponseText(s.model))
      }

    final def submitedResponseText(
      upscanRequest: UpscanInitiateRequest
    )(
      upscanInitiate: UpscanInitiateApi
    )(user: String)(responseText: String)(implicit ec: ExecutionContext) =
      Transition {
        case EnterResponseText(model)
            if model.hasTypeOfAmendment(
              TypeOfAmendment.WriteResponse,
              TypeOfAmendment.WriteResponseAndUploadDocuments
            ) =>
          val updatedModel = model.copy(responseText = Some(responseText))
          if (model.typeOfAmendment.contains(TypeOfAmendment.WriteResponse))
            goto(AmendCaseConfirmation(updatedModel.copy(fileUploads = None)))
          else
            gotoFileUploadOrUploaded(
              updatedModel,
              upscanRequest,
              upscanInitiate,
              model.fileUploads,
              showUploadSummaryIfAny = true
            )
      }

    final def backFromFileUploadState(user: String) =
      Transition {
        case s: FileUploadState =>
          val model = s.hostData.copy(fileUploads = Some(s.fileUploads))
          s.hostData.typeOfAmendment.getOrElse(TypeOfAmendment.UploadDocuments) match {
            case TypeOfAmendment.WriteResponse | TypeOfAmendment.WriteResponseAndUploadDocuments =>
              goto(EnterResponseText(model))

            case TypeOfAmendment.UploadDocuments =>
              goto(SelectTypeOfAmendment(model))
          }
      }

    final def amendCase(user: String) =
      Transition {
        case s: FileUploadState =>
          val updatedModel = s.hostData.copy(fileUploads = Some(s.fileUploads))
          goto(AmendCaseConfirmation(updatedModel))

        case EnterResponseText(model) if model.typeOfAmendment.contains(TypeOfAmendment.WriteResponse) =>
          goto(AmendCaseConfirmation(model))
      }
  }
}
