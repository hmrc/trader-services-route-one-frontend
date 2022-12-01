/*
 * Copyright 2022 HM Revenue & Customs
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

import uk.gov.hmrc.traderservices.connectors._
import uk.gov.hmrc.traderservices.models._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure
import scala.util.Success

object AmendCaseJourneyModel extends FileUploadJourneyModelMixin {

  sealed trait IsError

  override val root: State = State.Start

  sealed trait AmendCaseState extends State with CanEnterFileUpload {
    def model: AmendCaseModel
    override def hostData: FileUploadHostData = model
    override def fileUploadsOpt: Option[FileUploads] = model.fileUploads
  }

  final override val maxFileUploadsNumber: Int = 10

  final override val retreatFromFileUpload: Transition =
    Transitions.backFromFileUpload

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

    final case class AmendCaseSummary(model: AmendCaseModel) extends AmendCaseState

    final case class AmendCaseMissingInformationError(model: AmendCaseModel) extends AmendCaseState

    final case class EnterResponseText(model: AmendCaseModel) extends AmendCaseState

    final case class AmendCaseConfirmation(
      uploadedFiles: Seq[UploadedFile],
      model: AmendCaseModel,
      result: TraderServicesResult
    ) extends State

    final case object AmendCaseAlreadySubmitted extends State
  }

  /** This is where things happen a.k.a bussiness logic of the service. */
  object Transitions {

    import State._

    final val start =
      Transition { case _ =>
        goto(Start)
      }

    final val enterCaseReferenceNumber =
      Transition {
        case s: AmendCaseState =>
          goto(EnterCaseReferenceNumber(s.model))

        case _ =>
          goto(EnterCaseReferenceNumber())
      }

    final def submitedCaseReferenceNumber(caseReferenceNumber: String) =
      Transition { case EnterCaseReferenceNumber(model) =>
        goto(SelectTypeOfAmendment(model.copy(caseReferenceNumber = Some(caseReferenceNumber))))
      }

    final val backToSelectTypeOfAmendment =
      Transition {
        case s: AmendCaseState =>
          goto(SelectTypeOfAmendment(s.model))

        case s: FileUploadState =>
          goto(SelectTypeOfAmendment(s.hostData.copy(fileUploads = Some(s.fileUploads))))
      }

    final def submitedTypeOfAmendment(uploadMultipleFiles: Boolean)(
      upscanRequest: String => UpscanInitiateRequest
    )(
      upscanInitiate: UpscanInitiateApi
    )(typeOfAmendment: TypeOfAmendment)(implicit ec: ExecutionContext) =
      Transition { case current @ SelectTypeOfAmendment(model) =>
        val updatedModel = model.copy(typeOfAmendment = Some(typeOfAmendment))
        typeOfAmendment match {
          case TypeOfAmendment.WriteResponse =>
            gotoSummaryIfComplete(EnterResponseText(updatedModel.copy(fileUploads = None)))

          case TypeOfAmendment.WriteResponseAndUploadDocuments =>
            gotoSummaryIfComplete(EnterResponseText(updatedModel))

          case TypeOfAmendment.UploadDocuments =>
            if (uploadMultipleFiles)
              FileUploadTransitions.toUploadMultipleFiles.apply(
                current.copy(model = updatedModel.copy(responseText = None))
              )
            else
              gotoFileUploadOrUploaded(
                updatedModel.copy(responseText = None),
                upscanRequest,
                upscanInitiate,
                model.fileUploads,
                showUploadSummaryIfAny = true
              )
        }
      }

    final val backToEnterResponseText =
      Transition {
        case s: AmendCaseState if s.model.typeOfAmendment.exists(_.hasResponse) =>
          goto(EnterResponseText(s.model))

        case s: FileUploadState if s.hostData.typeOfAmendment.exists(_.hasResponse) =>
          goto(EnterResponseText(s.hostData.copy(fileUploads = Some(s.fileUploads))))
      }

    final def isComplete(model: AmendCaseModel): Boolean =
      model.caseReferenceNumber.nonEmpty &&
        model.typeOfAmendment.exists {
          case TypeOfAmendment.WriteResponse =>
            model.responseText.exists(_.nonEmpty)

          case TypeOfAmendment.UploadDocuments =>
            model.fileUploads.exists(_.nonEmpty) &&
            model.fileUploads.exists(_.acceptedCount <= maxFileUploadsNumber)

          case TypeOfAmendment.WriteResponseAndUploadDocuments =>
            model.responseText.exists(_.nonEmpty) &&
            model.fileUploads.exists(_.nonEmpty) &&
            model.fileUploads.exists(_.acceptedCount <= maxFileUploadsNumber)
        }

    final def gotoSummaryIfComplete(state: AmendCaseState): Future[State] =
      if (isComplete(state.model))
        goto(State.AmendCaseSummary(state.model))
      else
        goto(state)

    final def gotoSummaryIfCompleteOrApplyTransition(state: AmendCaseState)(transition: Transition): Future[State] =
      if (isComplete(state.model))
        goto(State.AmendCaseSummary(state.model))
      else
        transition.apply(state)

    final def submitedResponseText(uploadMultipleFiles: Boolean)(
      upscanRequest: String => UpscanInitiateRequest
    )(upscanInitiate: UpscanInitiateApi)(responseText: String)(implicit ec: ExecutionContext) =
      Transition {
        case current @ EnterResponseText(model)
            if model.hasTypeOfAmendment(
              TypeOfAmendment.WriteResponse,
              TypeOfAmendment.WriteResponseAndUploadDocuments
            ) =>
          val updatedModel = model.copy(responseText = Some(responseText))
          if (model.typeOfAmendment.contains(TypeOfAmendment.WriteResponse))
            gotoSummaryIfCompleteOrApplyTransition(EnterResponseText(updatedModel))(
              Transitions.enterCaseReferenceNumber
            )
          else if (uploadMultipleFiles)
            FileUploadTransitions.toUploadMultipleFiles
              .apply(current.copy(model = updatedModel))
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

    final val backFromFileUpload =
      Transition { case s: FileUploadState =>
        val model = s.hostData.copy(fileUploads = Some(s.fileUploads))
        s.hostData.typeOfAmendment.getOrElse(TypeOfAmendment.UploadDocuments) match {
          case TypeOfAmendment.WriteResponse | TypeOfAmendment.WriteResponseAndUploadDocuments =>
            goto(EnterResponseText(model))

          case TypeOfAmendment.UploadDocuments =>
            goto(SelectTypeOfAmendment(model))
        }
      }

    final val toAmendSummary =
      Transition {
        case state: AmendCaseConfirmation =>
          goto(AmendCaseAlreadySubmitted)

        case current: FileUploadState =>
          val updatedModel = current.hostData.copy(fileUploads = Some(current.fileUploads))
          goto(AmendCaseSummary(updatedModel))

        case current: AmendCaseState =>
          goto(AmendCaseSummary(current.model))
      }

    final val backToAmendCaseMissingInformationError =
      Transition { case s: AmendCaseState =>
        goto(AmendCaseMissingInformationError(s.model))
      }

    final def amendCase(
      updateCaseApi: UpdateCaseApi
    )(uidAndEori: (Option[String], Option[String]))(implicit ec: ExecutionContext) = {

      def callUpdateCase(model: AmendCaseModel) = {
        val caseReferenceNumber = model.caseReferenceNumber.get
        val request: TraderServicesUpdateCaseRequest =
          TraderServicesUpdateCaseRequest(
            caseReferenceNumber,
            model.typeOfAmendment.get,
            model.responseText,
            model.fileUploads.map(_.toUploadedFiles).getOrElse(Seq.empty),
            uidAndEori._2
          )
        updateCaseApi(request)
          .transformWith {
            case Failure(exception) =>
              JourneyLog.logUpdateCase(uidAndEori._1, request, exception)
              Future.failed(exception)

            case Success(response) =>
              JourneyLog.logUpdateCase(uidAndEori._1, request, response)
              if (response.result.isDefined)
                if (response.result.get.caseId == caseReferenceNumber)
                  goto(
                    AmendCaseConfirmation(
                      request.uploadedFiles,
                      model,
                      response.result.get
                    )
                  )
                else
                  fail(
                    TraderServicesAmendApiError(
                      CaseReferenceUpstreamException(
                        s"Received UpdateCase API response with different case reference number than requested, expected $caseReferenceNumber but got ${response.result.get}."
                      )
                    )
                  )
              else {
                val message = response.error.map(_.errorCode).map(_ + " ").getOrElse("") +
                  response.error.map(_.errorMessage).getOrElse("")
                fail(TraderServicesAmendApiError(CaseReferenceUpstreamException(message)))
              }
          }
      }

      Transition {
        case s: AmendCaseSummary if isComplete(s.model) =>
          callUpdateCase(s.model)

        case s: AmendCaseSummary =>
          goto(AmendCaseMissingInformationError(s.model))
      }
    }
  }
  case class CaseReferenceUpstreamException(message: String) extends RuntimeException(message)
}
