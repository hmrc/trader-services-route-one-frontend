/*
 * Copyright 2021 HM Revenue & Customs
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
import uk.gov.hmrc.traderservices.connectors.{ApiError, TraderServicesCaseResponse, TraderServicesCreateCaseRequest, TraderServicesResult}
import uk.gov.hmrc.traderservices.connectors.UpscanInitiateRequest

object CreateCaseJourneyModel extends FileUploadJourneyModelMixin {

  sealed trait IsError

  override val root: State = State.Start

  /** Opaque data carried through the file upload process. */
  final case class FileUploadHostData(
    declarationDetails: DeclarationDetails,
    questionsAnswers: QuestionsAnswers
  )

  final override val maxFileUploadsNumber: Int = 10

  final override def retreatFromFileUpload: Transition =
    Transitions.backFromFileUpload

  /** Model parametrization and rules. */
  object Rules {

    val mandatoryVesselDetailsRequestTypes: Set[ExportRequestType] =
      Set(ExportRequestType.C1601, ExportRequestType.C1602)

    final def isVesselDetailsAnswerMandatory(exportQuestions: ExportQuestions): Boolean =
      exportQuestions.requestType.exists(mandatoryVesselDetailsRequestTypes.contains) || exportQuestions.routeType
        .contains(ExportRouteType.Hold)

    final def isVesselDetailsAnswerMandatory(importQuestions: ImportQuestions): Boolean =
      importQuestions.routeType.contains(ImportRouteType.Hold)

    /** Checks is all export questions answers and file uploads are in place. */
    final def isComplete(exportQuestionsStateModel: ExportQuestionsStateModel): Boolean = {
      val answers = exportQuestionsStateModel.exportQuestionsAnswers

      val isPriorityGoodsComplete =
        answers.hasPriorityGoods.map(b => if (b) answers.priorityGoods.isDefined else true).getOrElse(false)

      val isVesselDetailsComplete = answers.vesselDetails
        .map(b => if (isVesselDetailsAnswerMandatory(answers)) b.isComplete else true)
        .getOrElse(false)

      val isFileUploadComplete =
        exportQuestionsStateModel.fileUploadsOpt.exists(fu => fu.nonEmpty && fu.acceptedCount <= maxFileUploadsNumber)

      answers.requestType.isDefined &&
      answers.routeType.isDefined &&
      isPriorityGoodsComplete &&
      answers.freightType.isDefined &&
      isVesselDetailsComplete &&
      answers.contactInfo.isDefined &&
      isFileUploadComplete
    }

    /** Checks is all import questions answers and file uploads are in place. */
    final def isComplete(importQuestionsStateModel: ImportQuestionsStateModel): Boolean = {
      val answers = importQuestionsStateModel.importQuestionsAnswers

      val isPriorityGoodsComplete =
        answers.hasPriorityGoods.map(b => if (b) answers.priorityGoods.isDefined else true).getOrElse(false)

      val isVesselDetailsComplete = answers.vesselDetails
        .map(b => if (isVesselDetailsAnswerMandatory(answers)) b.isComplete else true)
        .getOrElse(false)

      val isFileUploadComplete =
        importQuestionsStateModel.fileUploadsOpt.exists((fu => fu.nonEmpty && fu.acceptedCount <= maxFileUploadsNumber))

      answers.requestType.isDefined &&
      answers.routeType.isDefined &&
      isPriorityGoodsComplete &&
      answers.freightType.isDefined &&
      answers.hasALVS.isDefined &&
      isVesselDetailsComplete &&
      answers.contactInfo.isDefined &&
      isFileUploadComplete
    }

  }

  /** All the possible states the journey can take. */
  object State {

    /** Root state of the journey. */
    case object Start extends State

    /** State intended to use only in the development of the model to fill loose ends. */
    case object WorkInProgressDeadEnd extends State

    // MARKER TRAITS

    sealed trait HasDeclarationDetails {
      def declarationDetails: DeclarationDetails
    }

    sealed trait HasExportQuestionsStateModel {
      val model: ExportQuestionsStateModel
    }

    sealed trait HasImportQuestionsStateModel {
      val model: ImportQuestionsStateModel
    }

    // SPECIALIZED STATE TRAITS

    sealed trait ExportQuestionsState extends State with HasDeclarationDetails with HasExportQuestionsStateModel {
      final def declarationDetails: DeclarationDetails = model.declarationDetails
    }

    sealed trait ImportQuestionsState extends State with HasDeclarationDetails with HasImportQuestionsStateModel {
      final def declarationDetails: DeclarationDetails = model.declarationDetails
    }

    sealed trait SummaryState extends State

    sealed trait EndState extends State

    // STATES

    final case class ChooseNewOrExistingCase(
      newOrExistingCaseOpt: Option[NewOrExistingCase] = None,
      declarationDetailsOpt: Option[DeclarationDetails] = None,
      exportQuestionsAnswersOpt: Option[ExportQuestions] = None,
      importQuestionsAnswersOpt: Option[ImportQuestions] = None,
      fileUploadsOpt: Option[FileUploads] = None,
      continueAmendCaseJourney: Boolean = true
    ) extends State

    final case class TurnToAmendCaseJourney(
      continueAmendCaseJourney: Boolean = true
    ) extends State

    final case class EnterDeclarationDetails(
      declarationDetailsOpt: Option[DeclarationDetails] = None,
      exportQuestionsAnswersOpt: Option[ExportQuestions] = None,
      importQuestionsAnswersOpt: Option[ImportQuestions] = None,
      fileUploadsOpt: Option[FileUploads] = None
    ) extends State

    // EXPORT QUESTIONS STATES

    final case class AnswerExportQuestionsRequestType(
      model: ExportQuestionsStateModel
    ) extends ExportQuestionsState

    final case class AnswerExportQuestionsRouteType(
      model: ExportQuestionsStateModel
    ) extends ExportQuestionsState

    final case class AnswerExportQuestionsHasPriorityGoods(
      model: ExportQuestionsStateModel
    ) extends ExportQuestionsState

    final case class AnswerExportQuestionsWhichPriorityGoods(
      model: ExportQuestionsStateModel
    ) extends ExportQuestionsState

    final case class AnswerExportQuestionsFreightType(
      model: ExportQuestionsStateModel
    ) extends ExportQuestionsState

    final case class AnswerExportQuestionsMandatoryVesselInfo(
      model: ExportQuestionsStateModel
    ) extends ExportQuestionsState

    final case class AnswerExportQuestionsOptionalVesselInfo(
      model: ExportQuestionsStateModel
    ) extends ExportQuestionsState

    final case class AnswerExportQuestionsContactInfo(
      model: ExportQuestionsStateModel
    ) extends ExportQuestionsState with CanEnterFileUpload {

      final def hostData: FileUploadHostData =
        FileUploadHostData(model.declarationDetails, model.exportQuestionsAnswers)

      final def fileUploadsOpt: Option[FileUploads] =
        model.fileUploadsOpt
    }

    final case class ExportQuestionsSummary(
      model: ExportQuestionsStateModel
    ) extends ExportQuestionsState with SummaryState with CanEnterFileUpload {

      final def hostData: FileUploadHostData =
        FileUploadHostData(model.declarationDetails, model.exportQuestionsAnswers)

      final def fileUploadsOpt: Option[FileUploads] =
        model.fileUploadsOpt
    }

    // IMPORT QUESTIONS STATES

    final case class AnswerImportQuestionsRequestType(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState

    final case class AnswerImportQuestionsRouteType(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState

    final case class AnswerImportQuestionsHasPriorityGoods(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState

    final case class AnswerImportQuestionsWhichPriorityGoods(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState

    final case class AnswerImportQuestionsALVS(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState

    final case class AnswerImportQuestionsFreightType(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState

    final case class AnswerImportQuestionsOptionalVesselInfo(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState

    final case class AnswerImportQuestionsMandatoryVesselInfo(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState

    final case class AnswerImportQuestionsContactInfo(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState with CanEnterFileUpload {

      final def hostData: FileUploadHostData =
        FileUploadHostData(model.declarationDetails, model.importQuestionsAnswers)

      final def fileUploadsOpt: Option[FileUploads] =
        model.fileUploadsOpt
    }

    final case class ImportQuestionsSummary(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState with SummaryState with CanEnterFileUpload {

      final def hostData: FileUploadHostData =
        FileUploadHostData(model.declarationDetails, model.importQuestionsAnswers)

      final def fileUploadsOpt: Option[FileUploads] =
        model.fileUploadsOpt
    }

    // END-OF-JOURNEY STATES

    final case class CreateCaseConfirmation(
      declarationDetails: DeclarationDetails,
      questionsAnswers: QuestionsAnswers,
      uploadedFiles: Seq[UploadedFile],
      result: TraderServicesResult
    ) extends EndState

    final case class CaseAlreadyExists(
      caseReferenceId: String
    ) extends EndState

  }

  /**
    * Function determining if all questions were answered
    * and the user can proceed straight to the summary,
    * or rather she shall go to the next question.
    */
  final def gotoSummaryIfCompleteOr(state: State): Future[State] =
    state match {
      case s: State.ExportQuestionsState =>
        if (Rules.isComplete(s.model)) goto(State.ExportQuestionsSummary(s.model))
        else goto(s)

      case s: State.ImportQuestionsState =>
        if (Rules.isComplete(s.model)) goto(State.ImportQuestionsSummary(s.model))
        else goto(s)

      case s => goto(s)
    }

  /**
    * Function determining if all questions were answered
    * and the user can proceed straight to the summary,
    * or rather she shall go to the next state.
    */
  final def gotoSummaryIfCompleteOrApplyTransition(state: State)(transition: Transition): Future[State] =
    state match {
      case s: State.ExportQuestionsState =>
        if (Rules.isComplete(s.model)) goto(State.ExportQuestionsSummary(s.model))
        else transition.apply(s)

      case s: State.ImportQuestionsState =>
        if (Rules.isComplete(s.model)) goto(State.ImportQuestionsSummary(s.model))
        else transition.apply(s)

      case s => goto(s)
    }

  /** This is where things happen a.k.a bussiness logic of the service. */
  object Transitions {
    import State._

    final def start(user: Option[String]) =
      Transition {
        case _ => goto(Start)
      }

    final def chooseNewOrExistingCase(user: Option[String]) =
      Transition {
        case EnterDeclarationDetails(a, b, c, d) =>
          goto(ChooseNewOrExistingCase(Some(NewOrExistingCase.New), a, b, c, d, continueAmendCaseJourney = false))

        case TurnToAmendCaseJourney(_) =>
          goto(ChooseNewOrExistingCase(Some(NewOrExistingCase.Existing)))

        case _ =>
          goto(ChooseNewOrExistingCase())
      }

    final def submittedNewOrExistingCaseChoice(user: Option[String])(newOrExisting: NewOrExistingCase) =
      Transition {
        case ChooseNewOrExistingCase(_, a, b, c, d, continue) =>
          newOrExisting match {
            case NewOrExistingCase.New =>
              goto(EnterDeclarationDetails(a, b, c, d))

            case NewOrExistingCase.Existing =>
              goto(TurnToAmendCaseJourney(continue))
          }

      }

    final def backToEnterDeclarationDetails(user: Option[String]) =
      Transition {
        case s: ExportQuestionsState =>
          goto(
            EnterDeclarationDetails(
              Some(s.model.declarationDetails),
              exportQuestionsAnswersOpt = Some(s.model.exportQuestionsAnswers),
              fileUploadsOpt = s.model.fileUploadsOpt
            )
          )

        case s: ImportQuestionsState =>
          goto(
            EnterDeclarationDetails(
              Some(s.model.declarationDetails),
              importQuestionsAnswersOpt = Some(s.model.importQuestionsAnswers),
              fileUploadsOpt = s.model.fileUploadsOpt
            )
          )

        case s: EndState =>
          goto(EnterDeclarationDetails())
      }

    final def submittedDeclarationDetails(user: Option[String])(declarationDetails: DeclarationDetails) =
      Transition {
        case EnterDeclarationDetails(_, exportQuestionsOpt, importQuestionsOpt, fileUploadsOpt) =>
          if (declarationDetails.isExportDeclaration)
            gotoSummaryIfCompleteOr(
              AnswerExportQuestionsRequestType(
                ExportQuestionsStateModel(
                  declarationDetails,
                  exportQuestionsOpt.getOrElse(ExportQuestions()),
                  fileUploadsOpt
                )
              )
            )
          else
            gotoSummaryIfCompleteOr(
              AnswerImportQuestionsRequestType(
                ImportQuestionsStateModel(
                  declarationDetails,
                  importQuestionsOpt.getOrElse(ImportQuestions()),
                  fileUploadsOpt
                )
              )
            )
      }

    final def backToAnswerExportQuestionsRequestType(user: Option[String]) =
      Transition {
        case s: ExportQuestionsState if s.model.exportQuestionsAnswers.requestType.isDefined =>
          goto(AnswerExportQuestionsRequestType(s.model))
      }

    final def submittedExportQuestionsAnswerRequestType(user: Option[String])(exportRequestType: ExportRequestType) =
      Transition {
        case AnswerExportQuestionsRequestType(model) =>
          val updatedExportQuestions = model.exportQuestionsAnswers.copy(requestType = Some(exportRequestType))
          gotoSummaryIfCompleteOr(AnswerExportQuestionsRouteType(model.updated(updatedExportQuestions)))
      }

    final def backToAnswerExportQuestionsRouteType(user: Option[String]) =
      Transition {
        case s: ExportQuestionsState if s.model.exportQuestionsAnswers.routeType.isDefined =>
          goto(AnswerExportQuestionsRouteType(s.model))
      }

    final def submittedExportQuestionsAnswerRouteType(user: Option[String])(exportRouteType: ExportRouteType) =
      Transition {
        case AnswerExportQuestionsRouteType(model) =>
          gotoSummaryIfCompleteOr(
            AnswerExportQuestionsHasPriorityGoods(
              model.updated(model.exportQuestionsAnswers.copy(routeType = Some(exportRouteType)))
            )
          )
      }

    final def backToAnswerExportQuestionsHasPriorityGoods(user: Option[String]) =
      Transition {
        case s: ExportQuestionsState if s.model.exportQuestionsAnswers.hasPriorityGoods.isDefined =>
          goto(AnswerExportQuestionsHasPriorityGoods(s.model))
      }

    final def submittedExportQuestionsAnswerHasPriorityGoods(user: Option[String])(exportHasPriorityGoods: Boolean) =
      Transition {
        case AnswerExportQuestionsHasPriorityGoods(model) =>
          if (exportHasPriorityGoods)
            gotoSummaryIfCompleteOr(
              AnswerExportQuestionsWhichPriorityGoods(
                model.updated(model.exportQuestionsAnswers.copy(hasPriorityGoods = Some(true)))
              )
            )
          else
            gotoSummaryIfCompleteOr(
              AnswerExportQuestionsFreightType(
                model.updated(
                  model.exportQuestionsAnswers.copy(hasPriorityGoods = Some(false), priorityGoods = None)
                )
              )
            )
      }

    final def backToAnswerExportQuestionsWhichPriorityGoods(user: Option[String]) =
      Transition {
        case s: ExportQuestionsState if s.model.exportQuestionsAnswers.priorityGoods.isDefined =>
          goto(AnswerExportQuestionsWhichPriorityGoods(s.model))
      }

    final def submittedExportQuestionsAnswerWhichPriorityGoods(
      user: Option[String]
    )(exportPriorityGoods: ExportPriorityGoods) =
      Transition {
        case AnswerExportQuestionsWhichPriorityGoods(model) =>
          gotoSummaryIfCompleteOr(
            AnswerExportQuestionsFreightType(
              model.updated(model.exportQuestionsAnswers.copy(priorityGoods = Some(exportPriorityGoods)))
            )
          )
      }

    final def backToAnswerExportQuestionsFreightType(user: Option[String]) =
      Transition {
        case s: ExportQuestionsState if s.model.exportQuestionsAnswers.freightType.isDefined =>
          goto(AnswerExportQuestionsFreightType(s.model))
      }

    final def submittedExportQuestionsAnswerFreightType(user: Option[String])(exportFreightType: ExportFreightType) =
      Transition {
        case AnswerExportQuestionsFreightType(model) =>
          val updatedExportQuestions = model.exportQuestionsAnswers.copy(freightType = Some(exportFreightType))
          if (Rules.isVesselDetailsAnswerMandatory(updatedExportQuestions))
            gotoSummaryIfCompleteOr(AnswerExportQuestionsMandatoryVesselInfo(model.updated(updatedExportQuestions)))
          else
            gotoSummaryIfCompleteOr(AnswerExportQuestionsOptionalVesselInfo(model.updated(updatedExportQuestions)))
      }

    final def backToAnswerExportQuestionsMandatoryVesselInfo(user: Option[String]) =
      Transition {
        case s: ExportQuestionsState if s.model.exportQuestionsAnswers.vesselDetails.isDefined =>
          goto(AnswerExportQuestionsMandatoryVesselInfo(s.model))
      }

    final def submittedExportQuestionsMandatoryVesselDetails(user: Option[String])(vesselDetails: VesselDetails) =
      Transition {
        case AnswerExportQuestionsMandatoryVesselInfo(model) if vesselDetails.isComplete =>
          gotoSummaryIfCompleteOr(
            AnswerExportQuestionsContactInfo(
              model.updated(model.exportQuestionsAnswers.copy(vesselDetails = Some(vesselDetails)))
            )
          )
      }

    final def backToAnswerExportQuestionsOptionalVesselInfo(user: Option[String]) =
      Transition {
        case s: ExportQuestionsState if Rules.isVesselDetailsAnswerMandatory(s.model.exportQuestionsAnswers) =>
          goto(AnswerExportQuestionsMandatoryVesselInfo(s.model))
        case s: ExportQuestionsState if s.model.exportQuestionsAnswers.vesselDetails.isDefined =>
          goto(AnswerExportQuestionsOptionalVesselInfo(s.model))
      }

    final def submittedExportQuestionsOptionalVesselDetails(user: Option[String])(vesselDetails: VesselDetails) =
      Transition {
        case AnswerExportQuestionsOptionalVesselInfo(model) =>
          gotoSummaryIfCompleteOr(
            AnswerExportQuestionsContactInfo(
              model.updated(
                model.exportQuestionsAnswers.copy(vesselDetails = Some(vesselDetails))
              )
            )
          )
      }

    final def backToAnswerExportQuestionsContactInfo(user: Option[String]) =
      Transition {
        case s: ExportQuestionsState if s.model.exportQuestionsAnswers.contactInfo.isDefined =>
          goto(AnswerExportQuestionsContactInfo(s.model))

        case s: FileUploadState =>
          s.hostData.questionsAnswers match {
            case exportQuestionsAnswers: ExportQuestions =>
              goto(
                AnswerExportQuestionsContactInfo(
                  ExportQuestionsStateModel(
                    declarationDetails = s.hostData.declarationDetails,
                    exportQuestionsAnswers = exportQuestionsAnswers,
                    fileUploadsOpt = Some(s.fileUploads)
                  )
                )
              )

            case importQuestionsAnswers: ImportQuestions =>
              goto(Start)
          }
      }

    final def submittedExportQuestionsContactInfo(uploadMultipleFiles: Boolean)(
      upscanRequest: UpscanInitiateRequest
    )(
      upscanInitiate: UpscanInitiateApi
    )(user: Option[String])(contactInfo: ExportContactInfo)(implicit ec: ExecutionContext) =
      Transition {
        case AnswerExportQuestionsContactInfo(model) =>
          gotoSummaryIfCompleteOrApplyTransition(
            AnswerExportQuestionsContactInfo(
              model.updated(model.exportQuestionsAnswers.copy(contactInfo = Some(contactInfo)))
            )
          )(
            if (uploadMultipleFiles)
              FileUploadTransitions.toUploadMultipleFiles
            else
              FileUploadTransitions.initiateFileUpload(upscanRequest)(upscanInitiate)
          )
      }

    final val backFromFileUpload =
      Transition {
        case s: FileUploadState =>
          s.hostData.questionsAnswers match {
            case answers: ExportQuestions =>
              goto(
                AnswerExportQuestionsContactInfo(
                  ExportQuestionsStateModel(s.hostData.declarationDetails, answers, Some(s.fileUploads))
                )
              )

            case answers: ImportQuestions =>
              goto(
                AnswerImportQuestionsContactInfo(
                  ImportQuestionsStateModel(s.hostData.declarationDetails, answers, Some(s.fileUploads))
                )
              )
          }
      }

    final def backToAnswerImportQuestionsRequestType(user: Option[String]) =
      Transition {
        case s: ImportQuestionsState if s.model.importQuestionsAnswers.requestType.isDefined =>
          goto(AnswerImportQuestionsRequestType(s.model))
      }

    final def submittedImportQuestionsAnswersRequestType(user: Option[String])(importRequestType: ImportRequestType) =
      Transition {
        case AnswerImportQuestionsRequestType(model) =>
          val updatedImportQuestions = model.importQuestionsAnswers.copy(requestType = Some(importRequestType))
          gotoSummaryIfCompleteOr(AnswerImportQuestionsRouteType(model.updated(updatedImportQuestions)))
      }

    final def backToAnswerImportQuestionsRouteType(user: Option[String]) =
      Transition {
        case s: ImportQuestionsState if s.model.importQuestionsAnswers.routeType.isDefined =>
          goto(AnswerImportQuestionsRouteType(s.model))
      }

    final def submittedImportQuestionsAnswerRouteType(user: Option[String])(importRouteType: ImportRouteType) =
      Transition {
        case AnswerImportQuestionsRouteType(model) =>
          gotoSummaryIfCompleteOr(
            AnswerImportQuestionsHasPriorityGoods(
              model.updated(model.importQuestionsAnswers.copy(routeType = Some(importRouteType)))
            )
          )
      }

    final def backToAnswerImportQuestionsHasPriorityGoods(user: Option[String]) =
      Transition {
        case s: ImportQuestionsState if s.model.importQuestionsAnswers.hasPriorityGoods.isDefined =>
          goto(AnswerImportQuestionsHasPriorityGoods(s.model))
      }

    final def submittedImportQuestionsAnswerHasPriorityGoods(user: Option[String])(importHasPriorityGoods: Boolean) =
      Transition {
        case AnswerImportQuestionsHasPriorityGoods(model) =>
          if (importHasPriorityGoods)
            gotoSummaryIfCompleteOr(
              AnswerImportQuestionsWhichPriorityGoods(
                model.updated(model.importQuestionsAnswers.copy(hasPriorityGoods = Some(true)))
              )
            )
          else
            gotoSummaryIfCompleteOr(
              AnswerImportQuestionsALVS(
                model.updated(model.importQuestionsAnswers.copy(hasPriorityGoods = Some(false), priorityGoods = None))
              )
            )
      }

    final def backToAnswerImportQuestionsWhichPriorityGoods(user: Option[String]) =
      Transition {
        case s: ImportQuestionsState if s.model.importQuestionsAnswers.priorityGoods.isDefined =>
          goto(AnswerImportQuestionsWhichPriorityGoods(s.model))
      }

    final def submittedImportQuestionsAnswerWhichPriorityGoods(
      user: Option[String]
    )(importPriorityGoods: ImportPriorityGoods) =
      Transition {
        case AnswerImportQuestionsWhichPriorityGoods(model) =>
          gotoSummaryIfCompleteOr(
            AnswerImportQuestionsALVS(
              model.updated(model.importQuestionsAnswers.copy(priorityGoods = Some(importPriorityGoods)))
            )
          )
      }

    final def backToAnswerImportQuestionsALVS(user: Option[String]) =
      Transition {
        case s: ImportQuestionsState if s.model.importQuestionsAnswers.hasALVS.isDefined =>
          goto(AnswerImportQuestionsALVS(s.model))
      }

    final def submittedImportQuestionsAnswerHasALVS(user: Option[String])(importHasALVS: Boolean) =
      Transition {
        case AnswerImportQuestionsALVS(model) =>
          gotoSummaryIfCompleteOr(
            AnswerImportQuestionsFreightType(
              model.updated(model.importQuestionsAnswers.copy(hasALVS = Some(importHasALVS)))
            )
          )
      }

    final def backToAnswerImportQuestionsFreightType(user: Option[String]) =
      Transition {
        case s: ImportQuestionsState if s.model.importQuestionsAnswers.freightType.isDefined =>
          goto(AnswerImportQuestionsFreightType(s.model))
      }

    final def submittedImportQuestionsAnswerFreightType(user: Option[String])(importFreightType: ImportFreightType) =
      Transition {
        case AnswerImportQuestionsFreightType(model) =>
          val updatedImportQuestions = model.importQuestionsAnswers.copy(freightType = Some(importFreightType))
          if (Rules.isVesselDetailsAnswerMandatory(updatedImportQuestions))
            gotoSummaryIfCompleteOr(AnswerImportQuestionsMandatoryVesselInfo(model.updated(updatedImportQuestions)))
          else
            gotoSummaryIfCompleteOr(AnswerImportQuestionsOptionalVesselInfo(model.updated(updatedImportQuestions)))
      }

    final def backToAnswerImportQuestionsMandatoryVesselInfo(user: Option[String]) =
      Transition {
        case s: ImportQuestionsState if s.model.importQuestionsAnswers.vesselDetails.isDefined =>
          goto(AnswerImportQuestionsMandatoryVesselInfo(s.model))
      }

    final def submittedImportQuestionsMandatoryVesselDetails(user: Option[String])(vesselDetails: VesselDetails) =
      Transition {
        case AnswerImportQuestionsMandatoryVesselInfo(model) if vesselDetails.isComplete =>
          gotoSummaryIfCompleteOr(
            AnswerImportQuestionsContactInfo(
              model.updated(
                model.importQuestionsAnswers.copy(vesselDetails =
                  if (vesselDetails.isEmpty) None else Some(vesselDetails)
                )
              )
            )
          )
      }

    final def backToAnswerImportQuestionsOptionalVesselInfo(user: Option[String]) =
      Transition {
        case s: ImportQuestionsState if Rules.isVesselDetailsAnswerMandatory(s.model.importQuestionsAnswers) =>
          goto(AnswerImportQuestionsMandatoryVesselInfo(s.model))
        case s: ImportQuestionsState if s.model.importQuestionsAnswers.vesselDetails.isDefined =>
          goto(AnswerImportQuestionsOptionalVesselInfo(s.model))
      }

    final def submittedImportQuestionsOptionalVesselDetails(user: Option[String])(vesselDetails: VesselDetails) =
      Transition {
        case AnswerImportQuestionsOptionalVesselInfo(model) =>
          gotoSummaryIfCompleteOr(
            AnswerImportQuestionsContactInfo(
              model.updated(
                model.importQuestionsAnswers.copy(vesselDetails = Some(vesselDetails))
              )
            )
          )
      }

    final def backToAnswerImportQuestionsContactInfo(user: Option[String]) =
      Transition {
        case s: ImportQuestionsState if s.model.importQuestionsAnswers.contactInfo.isDefined =>
          goto(AnswerImportQuestionsContactInfo(s.model))

        case s: FileUploadState =>
          s.hostData.questionsAnswers match {
            case exportQuestionsAnswers: ExportQuestions =>
              goto(Start)

            case importQuestionsAnswers: ImportQuestions =>
              goto(
                AnswerImportQuestionsContactInfo(
                  ImportQuestionsStateModel(
                    declarationDetails = s.hostData.declarationDetails,
                    importQuestionsAnswers = importQuestionsAnswers,
                    fileUploadsOpt = Some(s.fileUploads)
                  )
                )
              )
          }
      }

    final def submittedImportQuestionsContactInfo(uploadMultipleFiles: Boolean)(
      upscanRequest: UpscanInitiateRequest
    )(
      upscanInitiate: UpscanInitiateApi
    )(user: Option[String])(contactInfo: ImportContactInfo)(implicit ec: ExecutionContext) =
      Transition {
        case AnswerImportQuestionsContactInfo(model) =>
          gotoSummaryIfCompleteOrApplyTransition(
            AnswerImportQuestionsContactInfo(
              model.updated(model.importQuestionsAnswers.copy(contactInfo = Some(contactInfo)))
            )
          )(
            if (uploadMultipleFiles)
              FileUploadTransitions.toUploadMultipleFiles
            else
              FileUploadTransitions.initiateFileUpload(upscanRequest)(upscanInitiate)
          )
      }

    final val toSummary =
      Transition {
        case state: FileUploadState =>
          state.hostData.questionsAnswers match {
            case exportQuestionsAnswers: ExportQuestions =>
              val updatedModel = ExportQuestionsStateModel(
                declarationDetails = state.hostData.declarationDetails,
                exportQuestionsAnswers = exportQuestionsAnswers,
                fileUploadsOpt = Some(state.fileUploads)
              )
              if (Rules.isComplete(updatedModel))
                goto(ExportQuestionsSummary(updatedModel))
              else
                goto(state)

            case importQuestionsAnswers: ImportQuestions =>
              val updatedModel =
                ImportQuestionsStateModel(
                  declarationDetails = state.hostData.declarationDetails,
                  importQuestionsAnswers = importQuestionsAnswers,
                  fileUploadsOpt = Some(state.fileUploads)
                )
              if (Rules.isComplete(updatedModel))
                goto(ImportQuestionsSummary(updatedModel))
              else
                goto(state)
          }
      }

    type CreateCaseApi = TraderServicesCreateCaseRequest => Future[TraderServicesCaseResponse]

    final def createCase(createCaseApi: CreateCaseApi)(eori: Option[String])(implicit ec: ExecutionContext) = {

      def invokeCreateCaseApi(request: TraderServicesCreateCaseRequest) =
        createCaseApi(request).flatMap { response =>
          if (response.result.isDefined)
            goto(
              CreateCaseConfirmation(
                request.declarationDetails,
                request.questionsAnswers,
                request.uploadedFiles,
                response.result.get
              )
            )
          else
            response.error match {
              case Some(ApiError("409", Some(caseReferenceId))) =>
                goto(CaseAlreadyExists(caseReferenceId))

              case _ =>
                val message = response.error.map(_.errorCode).map(_ + " ").getOrElse("") +
                  response.error.map(_.errorMessage).getOrElse("")
                fail(new RuntimeException(message))
            }
        }

      Transition {
        case state: ExportQuestionsSummary =>
          val uploadedFiles =
            state.model.fileUploadsOpt.getOrElse(FileUploads()).toUploadedFiles
          val createCaseRequest =
            TraderServicesCreateCaseRequest(
              state.model.declarationDetails,
              state.model.exportQuestionsAnswers,
              uploadedFiles,
              eori
            )
          invokeCreateCaseApi(createCaseRequest)

        case state: ImportQuestionsSummary =>
          val uploadedFiles =
            state.model.fileUploadsOpt.getOrElse(FileUploads()).toUploadedFiles
          val createCaseRequest =
            TraderServicesCreateCaseRequest(
              state.model.declarationDetails,
              state.model.importQuestionsAnswers,
              uploadedFiles,
              eori
            )
          invokeCreateCaseApi(createCaseRequest)
      }
    }
  }
}
