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
import uk.gov.hmrc.traderservices.connectors.TraderServicesUpdateCaseRequest
import uk.gov.hmrc.traderservices.connectors.TraderServicesCaseResponse

object AmendCaseJourneyModel extends FileUploadJourneyModelMixin {

  sealed trait IsError

  override val root: State = State.Start

  sealed trait AmendCaseState extends State {
    def model: AmendCaseModel
  }

  // FileUploadJourneyModel customization

  override val maxFileUploadsNumber: Int = 10

  override def retreatFromFileUpload: String => Transition =
    Transitions.backFromFileUploadState

  /** Opaque data carried through the file upload process. */
  override type FileUploadHostData = AmendCaseModel

  type UpdateCaseApi = TraderServicesUpdateCaseRequest => Future[TraderServicesCaseResponse]

  /** All the possible states the journey can take. */
  object State {

    /** Root state of the journey. */
    final case object Start extends State

    /** State intended to use only in the development of the model to fill loose ends. */
    final case object WorkInProgressDeadEnd extends State

    final case class EnterCaseReferenceNumber(model: AmendCaseModel = AmendCaseModel()) extends AmendCaseState

    final case class SelectTypeOfAmendment(model: AmendCaseModel) extends AmendCaseState

    final case class EnterResponseText(model: AmendCaseModel) extends AmendCaseState

    final case class AmendCaseConfirmation(caseReferenceNumber: String) extends State
  }

  /** This is where things happen a.k.a bussiness logic of the service. */
  object Transitions {
    import State._

    final def start(user: String) =
      Transition {
        case _ => goto(Start)
      }

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

        case s: FileUploadState if s.hostData.typeOfAmendment.isDefined =>
          goto(SelectTypeOfAmendment(s.hostData.copy(fileUploads = Some(s.fileUploads))))
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

        case s: FileUploadState if s.hostData.responseText.isDefined =>
          goto(EnterResponseText(s.hostData.copy(fileUploads = Some(s.fileUploads))))
      }

    final def submitedResponseText(
      upscanRequest: UpscanInitiateRequest,
      upscanInitiate: UpscanInitiateApi,
      updateCaseApi: UpdateCaseApi
    )(user: String)(responseText: String)(implicit ec: ExecutionContext) =
      Transition {
        case EnterResponseText(model)
            if model.hasTypeOfAmendment(
              TypeOfAmendment.WriteResponse,
              TypeOfAmendment.WriteResponseAndUploadDocuments
            ) =>
          val updatedModel = model.copy(responseText = Some(responseText))
          if (model.typeOfAmendment.contains(TypeOfAmendment.WriteResponse))
            amendCase(updateCaseApi)(user).apply(EnterResponseText(updatedModel))
          else
            gotoFileUploadOrUploaded(
              updatedModel,
              upscanRequest,
              upscanInitiate,
              model.fileUploads,
              showUploadSummaryIfAny = true
            )

        case EnterResponseText(model) if model.typeOfAmendment.isEmpty =>
          goto(SelectTypeOfAmendment(model.copy(responseText = Some(responseText))))
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

    final def amendCase(updateCaseApi: UpdateCaseApi)(user: String)(implicit ec: ExecutionContext) = {

      def validateModelAndCallUpdateCase(model: AmendCaseModel) =
        model.caseReferenceNumber match {
          case Some(caseReferenceNumber) =>
            model.typeOfAmendment match {
              case Some(typeOfAmendment) =>
                val request: TraderServicesUpdateCaseRequest =
                  TraderServicesUpdateCaseRequest(
                    caseReferenceNumber,
                    typeOfAmendment,
                    model.responseText,
                    model.fileUploads.map(_.toUploadedFiles).getOrElse(Seq.empty),
                    eori = user
                  )
                updateCaseApi(request)
                  .flatMap { response =>
                    if (response.result.isDefined)
                      if (response.result.get == caseReferenceNumber)
                        goto(AmendCaseConfirmation(caseReferenceNumber))
                      else
                        fail(
                          new RuntimeException(
                            s"Received UpdateCase API response with different case reference number than requested, expected $caseReferenceNumber but got ${response.result.get}."
                          )
                        )
                    else {
                      val message = response.error.map(_.errorCode).map(_ + " ").getOrElse("") +
                        response.error.map(_.errorMessage).getOrElse("")
                      fail(new RuntimeException(message))
                    }
                  }

              case None =>
                goto(SelectTypeOfAmendment(model))
            }

          case None =>
            goto(EnterCaseReferenceNumber(model))
        }

      Transition {
        case s: FileUploadState =>
          val updatedModel = s.hostData.copy(fileUploads = Some(s.fileUploads))
          validateModelAndCallUpdateCase(updatedModel)

        case EnterResponseText(model) if model.typeOfAmendment.contains(TypeOfAmendment.WriteResponse) =>
          validateModelAndCallUpdateCase(model)
      }
    }
  }
}
