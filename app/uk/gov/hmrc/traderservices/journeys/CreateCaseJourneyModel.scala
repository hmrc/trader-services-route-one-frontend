/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.mvc.RequestHeader
import uk.gov.hmrc.traderservices.models._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import uk.gov.hmrc.traderservices.connectors.{ApiError, TraderServicesCaseResponse, TraderServicesCreateCaseRequest, TraderServicesResult}
import uk.gov.hmrc.traderservices.connectors.UpscanInitiateRequest
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.Rules.isVesselDetailsAnswerMandatory
import uk.gov.hmrc.traderservices.views.CommonUtilsHelper.DateTimeUtilities

import scala.util.Success
import scala.util.Failure

object CreateCaseJourneyModel extends FileUploadJourneyModelMixin {

  sealed trait IsError

  override val root: State = CreateCaseJourneyState.Start

  /** Opaque data carried through the file upload process. */
  final case class FileUploadHostData(
    entryDetails: EntryDetails,
    questionsAnswers: QuestionsAnswers
  )

  final override val maxFileUploadsNumber: Int = 10

  final override def retreatFromFileUpload: Transition[State] =
    Transitions.backFromFileUpload

  /** Model parametrization and rules. */
  object Rules {

    val mandatoryVesselDetailsRequestTypes: Set[ExportRequestType] =
      Set(ExportRequestType.C1601, ExportRequestType.C1602)

    val mandatoryReasonTypes: Set[ExportRequestType] =
      Set(ExportRequestType.Cancellation, ExportRequestType.WithdrawalOrReturn)

    final def isVesselDetailsAnswerMandatory(exportQuestions: ExportQuestions): Boolean =
      exportQuestions.requestType.exists(mandatoryVesselDetailsRequestTypes.contains) || exportQuestions.routeType
        .contains(ExportRouteType.Hold)

    final def isVesselDetailsAnswerMandatory(importQuestions: ImportQuestions): Boolean =
      importQuestions.routeType.contains(ImportRouteType.Hold)

    final def isReasonMandatory(exportQuestions: ExportQuestions): Boolean =
      exportQuestions.requestType.exists(mandatoryReasonTypes.contains) || exportQuestions.routeType
        .contains(ExportRouteType.Route3)

    final def isReasonMandatory(importQuestions: ImportQuestions): Boolean =
      importQuestions.requestType.contains(ImportRequestType.Cancellation) || importQuestions.routeType
        .contains(ImportRouteType.Route3)

    /** Checks is all export questions answers and file uploads are in place. */
    final def isComplete(exportQuestionsStateModel: ExportQuestionsStateModel): Boolean = {
      val answers = exportQuestionsStateModel.exportQuestionsAnswers

      val isReasonComplete = answers.reason match {
        case None if isReasonMandatory(answers) => false
        case _                                  => true
      }

      val isPriorityGoodsComplete =
        answers.hasPriorityGoods.map(b => if (b) answers.priorityGoods.isDefined else true).getOrElse(false)

      val isVesselDetailsComplete = answers.vesselDetails match {
        case Some(vesselDetails: VesselDetails) if isVesselDetailsAnswerMandatory(answers) => vesselDetails.isComplete
        case None if isVesselDetailsAnswerMandatory(answers)                               => false
        case _                                                                             => true
      }

      val isFileUploadComplete =
        exportQuestionsStateModel.fileUploadsOpt.exists(fu => fu.nonEmpty && fu.acceptedCount <= maxFileUploadsNumber)

      answers.requestType.isDefined &&
      answers.routeType.isDefined &&
      isReasonComplete &&
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

      val isVesselDetailsComplete = answers.vesselDetails match {
        case Some(vesselDetails: VesselDetails) if isVesselDetailsAnswerMandatory(answers) => vesselDetails.isComplete
        case None if isVesselDetailsAnswerMandatory(answers)                               => false
        case _                                                                             => true
      }

      val isReasonComplete = answers.reason match {
        case None if isReasonMandatory(answers) => false
        case _                                  => true
      }

      val isFileUploadComplete =
        importQuestionsStateModel.fileUploadsOpt.exists(fu => fu.nonEmpty && fu.acceptedCount <= maxFileUploadsNumber)

      answers.requestType.isDefined &&
      answers.routeType.isDefined &&
      isReasonComplete &&
      isPriorityGoodsComplete &&
      answers.freightType.isDefined &&
      answers.hasALVS.isDefined &&
      isVesselDetailsComplete &&
      answers.contactInfo.isDefined &&
      isFileUploadComplete
    }

  }

  /** All the possible states the journey can take. */
  object CreateCaseJourneyState {

    /** Root state of the journey. */
    case object Start extends State

    /** State intended to use only in the development of the model to fill loose ends. */
    case object WorkInProgressDeadEnd extends State

    // MARKER TRAITS

    sealed trait HasEntryDetails {
      def entryDetails: EntryDetails
    }

    sealed trait HasExportRequestType {
      def exportRequestType: ExportRequestType
    }

    sealed trait HasExportQuestionsStateModel {
      val model: ExportQuestionsStateModel
    }

    sealed trait HasImportQuestionsStateModel {
      val model: ImportQuestionsStateModel
    }

    // SPECIALIZED STATE TRAITS

    sealed trait ExportQuestionsState extends State with HasEntryDetails with HasExportQuestionsStateModel {
      final def entryDetails: EntryDetails = model.entryDetails
    }

    sealed trait ImportQuestionsState extends State with HasEntryDetails with HasImportQuestionsStateModel {
      final def entryDetails: EntryDetails = model.entryDetails
    }

    sealed trait SummaryState extends State

    sealed trait EndState extends State

    // STATES

    final case class ChooseNewOrExistingCase(
      newOrExistingCaseOpt: Option[NewOrExistingCase] = None,
      entryDetailsOpt: Option[EntryDetails] = None,
      exportQuestionsAnswersOpt: Option[ExportQuestions] = None,
      importQuestionsAnswersOpt: Option[ImportQuestions] = None,
      fileUploadsOpt: Option[FileUploads] = None,
      continueAmendCaseJourney: Boolean = true
    ) extends State

    final case class TurnToAmendCaseJourney(
      continueAmendCaseJourney: Boolean = true
    ) extends State

    final case class EnterEntryDetails(
      entryDetailsOpt: Option[EntryDetails] = None,
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

    final case class AnswerExportQuestionsReason(
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
        FileUploadHostData(model.entryDetails, model.exportQuestionsAnswers)

      final def fileUploadsOpt: Option[FileUploads] =
        model.fileUploadsOpt
    }

    final case class ExportQuestionsSummary(
      model: ExportQuestionsStateModel
    ) extends ExportQuestionsState with SummaryState with CanEnterFileUpload {

      final def hostData: FileUploadHostData =
        FileUploadHostData(model.entryDetails, model.exportQuestionsAnswers)

      final def fileUploadsOpt: Option[FileUploads] =
        model.fileUploadsOpt
    }

    final case class ExportQuestionsMissingInformationError(
      model: ExportQuestionsStateModel
    ) extends ExportQuestionsState

    // IMPORT QUESTIONS STATES

    final case class AnswerImportQuestionsRequestType(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState

    final case class AnswerImportQuestionsRouteType(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState

    final case class AnswerImportQuestionsReason(
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
        FileUploadHostData(model.entryDetails, model.importQuestionsAnswers)

      final def fileUploadsOpt: Option[FileUploads] =
        model.fileUploadsOpt
    }

    final case class ImportQuestionsSummary(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState with SummaryState with CanEnterFileUpload {

      final def hostData: FileUploadHostData =
        FileUploadHostData(model.entryDetails, model.importQuestionsAnswers)

      final def fileUploadsOpt: Option[FileUploads] =
        model.fileUploadsOpt
    }

    final case class ImportQuestionsMissingInformationError(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState

    // END-OF-JOURNEY STATES

    final case class CreateCaseConfirmation(
      entryDetails: EntryDetails,
      questionsAnswers: QuestionsAnswers,
      uploadedFiles: Seq[UploadedFile],
      result: TraderServicesResult,
      caseSLA: CaseSLA
    ) extends EndState

    final case class CaseAlreadyExists(
      caseReferenceId: String
    ) extends EndState

    final case object CaseAlreadySubmitted extends State

  }

  /** Function determining if all questions were answered and the user can proceed straight to the summary, or rather
    * she shall go to the next question.
    */
  final def gotoSummaryIfCompleteOr(state: State): Future[State] =
    state match {
      case s: CreateCaseJourneyState.ExportQuestionsState =>
        if (Rules.isComplete(s.model)) goto(CreateCaseJourneyState.ExportQuestionsSummary(s.model))
        else goto(s)

      case s: CreateCaseJourneyState.ImportQuestionsState =>
        if (Rules.isComplete(s.model)) goto(CreateCaseJourneyState.ImportQuestionsSummary(s.model))
        else goto(s)

      case s => goto(s)
    }

  /** Function determining if all questions were answered and the user can proceed straight to the summary, or rather
    * she shall go to the next state.
    */
  final def gotoSummaryIfCompleteOrApplyTransition(state: State)(transition: Transition[State]): Future[State] =
    state match {
      case s: CreateCaseJourneyState.ExportQuestionsState =>
        if (Rules.isComplete(s.model)) goto(CreateCaseJourneyState.ExportQuestionsSummary(s.model))
        else transition.apply(s)

      case s: CreateCaseJourneyState.ImportQuestionsState =>
        if (Rules.isComplete(s.model)) goto(CreateCaseJourneyState.ImportQuestionsSummary(s.model))
        else transition.apply(s)

      case s => goto(s)
    }

  /** This is where things happen a.k.a bussiness logic of the service. */
  object Transitions {
    import CreateCaseJourneyState._

    final val start =
      Transition[State] { case _ =>
        goto(Start)
      }

    final val chooseNewOrExistingCase =
      Transition[State] {
        case EnterEntryDetails(a, b, c, d) =>
          goto(ChooseNewOrExistingCase(Some(NewOrExistingCase.New), a, b, c, d, continueAmendCaseJourney = false))

        case TurnToAmendCaseJourney(_) =>
          goto(ChooseNewOrExistingCase(Some(NewOrExistingCase.Existing)))

        case _ =>
          goto(ChooseNewOrExistingCase())
      }

    final def submittedNewOrExistingCaseChoice(newOrExisting: NewOrExistingCase) =
      Transition[State] { case ChooseNewOrExistingCase(_, a, b, c, d, continue) =>
        newOrExisting match {
          case NewOrExistingCase.New =>
            goto(EnterEntryDetails(a, b, c, d))

          case NewOrExistingCase.Existing =>
            goto(TurnToAmendCaseJourney(continue))
        }

      }

    final val backToEnterEntryDetails =
      Transition[State] {
        case s: ExportQuestionsState =>
          goto(
            EnterEntryDetails(
              Some(s.model.entryDetails),
              exportQuestionsAnswersOpt = Some(s.model.exportQuestionsAnswers),
              fileUploadsOpt = s.model.fileUploadsOpt
            )
          )

        case s: ImportQuestionsState =>
          goto(
            EnterEntryDetails(
              Some(s.model.entryDetails),
              importQuestionsAnswersOpt = Some(s.model.importQuestionsAnswers),
              fileUploadsOpt = s.model.fileUploadsOpt
            )
          )

        case s: EndState =>
          goto(EnterEntryDetails())
      }

    final def submittedEntryDetails(entryDetails: EntryDetails) =
      Transition[State] { case EnterEntryDetails(_, exportQuestionsOpt, importQuestionsOpt, fileUploadsOpt) =>
        if (entryDetails.isExportDeclaration)
          gotoSummaryIfCompleteOr(
            AnswerExportQuestionsRequestType(
              ExportQuestionsStateModel(
                entryDetails,
                exportQuestionsOpt.getOrElse(ExportQuestions()),
                fileUploadsOpt
              )
            )
          )
        else
          gotoSummaryIfCompleteOr(
            AnswerImportQuestionsRequestType(
              ImportQuestionsStateModel(
                entryDetails,
                importQuestionsOpt.getOrElse(ImportQuestions()),
                fileUploadsOpt
              )
            )
          )
      }

    final val backToAnswerExportQuestionsRequestType =
      Transition[State] { case s: ExportQuestionsState =>
        goto(AnswerExportQuestionsRequestType(s.model))
      }

    final def submittedExportQuestionsAnswerRequestType(
      requireOptionalTransportPage: Boolean
    )(exportRequestType: ExportRequestType) =
      Transition[State] { case AnswerExportQuestionsRequestType(model) =>
        val updatedExportQuestions = model.exportQuestionsAnswers.copy(requestType = Some(exportRequestType))
        gotoSummaryIfCompleteOr(
          AnswerExportQuestionsRouteType(
            model
              .updated(
                updatedExportQuestions.copy(
                  vesselDetails =
                    if (requireOptionalTransportPage || isVesselDetailsAnswerMandatory(updatedExportQuestions))
                      model.exportQuestionsAnswers.vesselDetails
                    else None,
                  reason =
                    if (Rules.isReasonMandatory(updatedExportQuestions)) updatedExportQuestions.reason
                    else None
                )
              )
          )
        )
      }

    final val backToAnswerExportQuestionsRouteType =
      Transition[State] { case s: ExportQuestionsState =>
        goto(AnswerExportQuestionsRouteType(s.model))
      }

    final def submittedExportQuestionsAnswerRouteType(
      requireOptionalTransportPage: Boolean
    )(exportRouteType: ExportRouteType) =
      Transition[State] { case AnswerExportQuestionsRouteType(model) =>
        val updatedExportQuestions = model.exportQuestionsAnswers.copy(routeType = Some(exportRouteType))

        if (Rules.isReasonMandatory(updatedExportQuestions))
          gotoSummaryIfCompleteOr(
            AnswerExportQuestionsReason(
              model.updated(
                updatedExportQuestions.copy(
                  vesselDetails =
                    if (requireOptionalTransportPage || isVesselDetailsAnswerMandatory(updatedExportQuestions))
                      model.exportQuestionsAnswers.vesselDetails
                    else None
                )
              )
            )
          )
        else
          gotoSummaryIfCompleteOr(
            AnswerExportQuestionsHasPriorityGoods(
              model.updated(
                updatedExportQuestions.copy(
                  reason = None,
                  vesselDetails =
                    if (requireOptionalTransportPage || isVesselDetailsAnswerMandatory(updatedExportQuestions))
                      model.exportQuestionsAnswers.vesselDetails
                    else None
                )
              )
            )
          )
      }

    final val backToAnswerExportQuestionsReason =
      Transition[State] {
        case s: ExportQuestionsState if Rules.isReasonMandatory(s.model.exportQuestionsAnswers) =>
          goto(AnswerExportQuestionsReason(s.model))
      }

    final def submittedExportQuestionsAnswerReason(reason: String) =
      Transition[State] { case AnswerExportQuestionsReason(model) =>
        gotoSummaryIfCompleteOr(
          AnswerExportQuestionsHasPriorityGoods(
            model.updated(model.exportQuestionsAnswers.copy(reason = Some(reason)))
          )
        )
      }

    final val backToAnswerExportQuestionsHasPriorityGoods =
      Transition[State] { case s: ExportQuestionsState =>
        goto(AnswerExportQuestionsHasPriorityGoods(s.model))
      }

    final def submittedExportQuestionsAnswerHasPriorityGoods(exportHasPriorityGoods: Boolean) =
      Transition[State] { case AnswerExportQuestionsHasPriorityGoods(model) =>
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

    final val backToAnswerExportQuestionsWhichPriorityGoods =
      Transition[State] { case s: ExportQuestionsState =>
        goto(AnswerExportQuestionsWhichPriorityGoods(s.model))
      }

    final def submittedExportQuestionsAnswerWhichPriorityGoods(exportPriorityGoods: ExportPriorityGoods) =
      Transition[State] { case AnswerExportQuestionsWhichPriorityGoods(model) =>
        gotoSummaryIfCompleteOr(
          AnswerExportQuestionsFreightType(
            model.updated(model.exportQuestionsAnswers.copy(priorityGoods = Some(exportPriorityGoods)))
          )
        )
      }

    final val backToAnswerExportQuestionsFreightType =
      Transition[State] { case s: ExportQuestionsState =>
        goto(AnswerExportQuestionsFreightType(s.model))
      }

    final def submittedExportQuestionsAnswerFreightType(
      requireOptionalTransportPage: Boolean
    )(exportFreightType: ExportFreightType) =
      Transition[State] { case AnswerExportQuestionsFreightType(model) =>
        val updatedExportQuestions = model.exportQuestionsAnswers.copy(freightType = Some(exportFreightType))
        if (Rules.isVesselDetailsAnswerMandatory(updatedExportQuestions))
          gotoSummaryIfCompleteOr(AnswerExportQuestionsMandatoryVesselInfo(model.updated(updatedExportQuestions)))
        else if (requireOptionalTransportPage)
          gotoSummaryIfCompleteOr(AnswerExportQuestionsOptionalVesselInfo(model.updated(updatedExportQuestions)))
        else
          gotoSummaryIfCompleteOr(
            AnswerExportQuestionsContactInfo(model.updated(updatedExportQuestions))
          )
      }

    final val backToAnswerExportQuestionsVesselInfo =
      Transition[State] {
        case s: ExportQuestionsState if Rules.isVesselDetailsAnswerMandatory(s.model.exportQuestionsAnswers) =>
          goto(AnswerExportQuestionsMandatoryVesselInfo(s.model))
        case s: ExportQuestionsState =>
          goto(AnswerExportQuestionsOptionalVesselInfo(s.model))
      }

    final def submittedExportQuestionsMandatoryVesselDetails(vesselDetails: VesselDetails) =
      Transition[State] {
        case AnswerExportQuestionsMandatoryVesselInfo(model) if vesselDetails.isComplete =>
          gotoSummaryIfCompleteOr(
            AnswerExportQuestionsContactInfo(
              model.updated(model.exportQuestionsAnswers.copy(vesselDetails = Some(vesselDetails)))
            )
          )
      }

    final def submittedExportQuestionsOptionalVesselDetails(vesselDetails: VesselDetails) =
      Transition[State] { case AnswerExportQuestionsOptionalVesselInfo(model) =>
        gotoSummaryIfCompleteOr(
          AnswerExportQuestionsContactInfo(
            model.updated(
              model.exportQuestionsAnswers.copy(vesselDetails = Some(vesselDetails))
            )
          )
        )
      }

    final val backToAnswerExportQuestionsContactInfo =
      Transition[State] {
        case s: ExportQuestionsState =>
          goto(AnswerExportQuestionsContactInfo(s.model))

        case s: FileUploadState =>
          s.hostData.questionsAnswers match {
            case exportQuestionsAnswers: ExportQuestions =>
              goto(
                AnswerExportQuestionsContactInfo(
                  ExportQuestionsStateModel(
                    entryDetails = s.hostData.entryDetails,
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
      upscanRequest: String => UpscanInitiateRequest
    )(
      upscanInitiate: UpscanInitiateApi
    )(contactInfo: ExportContactInfo)(implicit ec: ExecutionContext) =
      Transition[State] { case AnswerExportQuestionsContactInfo(model) =>
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
      Transition[State] {
        case s: FileUploadState =>
          s.hostData.questionsAnswers match {
            case answers: ExportQuestions =>
              goto(
                AnswerExportQuestionsContactInfo(
                  ExportQuestionsStateModel(s.hostData.entryDetails, answers, Some(s.fileUploads))
                )
              )

            case answers: ImportQuestions =>
              goto(
                AnswerImportQuestionsContactInfo(
                  ImportQuestionsStateModel(s.hostData.entryDetails, answers, Some(s.fileUploads))
                )
              )
          }

        case s: ImportQuestionsState =>
          goto(AnswerImportQuestionsContactInfo(s.model))

        case s: ExportQuestionsState =>
          goto(AnswerExportQuestionsContactInfo(s.model))
      }

    final val backToAnswerImportQuestionsRequestType =
      Transition[State] { case s: ImportQuestionsState =>
        goto(AnswerImportQuestionsRequestType(s.model))
      }

    final def submittedImportQuestionsAnswersRequestType(importRequestType: ImportRequestType) =
      Transition[State] { case AnswerImportQuestionsRequestType(model) =>
        val updatedImportQuestions = model.importQuestionsAnswers.copy(requestType = Some(importRequestType))
        gotoSummaryIfCompleteOr(
          AnswerImportQuestionsRouteType(
            model.updated(
              updatedImportQuestions.copy(reason =
                if (Rules.isReasonMandatory(updatedImportQuestions)) updatedImportQuestions.reason else None
              )
            )
          )
        )
      }

    final val backToAnswerImportQuestionsRouteType =
      Transition[State] { case s: ImportQuestionsState =>
        goto(AnswerImportQuestionsRouteType(s.model))
      }

    final def submittedImportQuestionsAnswerRouteType(
      requireOptionalTransportPage: Boolean
    )(importRouteType: ImportRouteType) =
      Transition[State] { case AnswerImportQuestionsRouteType(model) =>
        val updatedImportQuestions = model.importQuestionsAnswers.copy(routeType = Some(importRouteType))
        if (Rules.isReasonMandatory(updatedImportQuestions))
          gotoSummaryIfCompleteOr(
            AnswerImportQuestionsReason(
              model.updated(
                updatedImportQuestions.copy(
                  vesselDetails =
                    if (
                      requireOptionalTransportPage || isVesselDetailsAnswerMandatory(
                        model.importQuestionsAnswers.copy(routeType = Some(importRouteType))
                      )
                    )
                      model.importQuestionsAnswers.vesselDetails
                    else None
                )
              )
            )
          )
        else
          gotoSummaryIfCompleteOr(
            AnswerImportQuestionsHasPriorityGoods(
              model.updated(
                updatedImportQuestions.copy(
                  vesselDetails =
                    if (
                      requireOptionalTransportPage || isVesselDetailsAnswerMandatory(
                        model.importQuestionsAnswers.copy(routeType = Some(importRouteType))
                      )
                    )
                      model.importQuestionsAnswers.vesselDetails
                    else None,
                  reason = None
                )
              )
            )
          )
      }

    final val backToAnswerImportQuestionsReason =
      Transition[State] {
        case s: ImportQuestionsState if Rules.isReasonMandatory(s.model.importQuestionsAnswers) =>
          goto(AnswerImportQuestionsReason(s.model))
      }
    final def submittedImportQuestionsAnswerReason(reason: String) =
      Transition[State] {
        case AnswerImportQuestionsReason(model) if reason.nonEmpty =>
          gotoSummaryIfCompleteOr(
            AnswerImportQuestionsHasPriorityGoods(
              model.updated(model.importQuestionsAnswers.copy(reason = Some(reason)))
            )
          )
      }

    final val backToAnswerImportQuestionsHasPriorityGoods =
      Transition[State] { case s: ImportQuestionsState =>
        goto(AnswerImportQuestionsHasPriorityGoods(s.model))
      }

    final def submittedImportQuestionsAnswerHasPriorityGoods(importHasPriorityGoods: Boolean) =
      Transition[State] { case AnswerImportQuestionsHasPriorityGoods(model) =>
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

    final val backToAnswerImportQuestionsWhichPriorityGoods =
      Transition[State] { case s: ImportQuestionsState =>
        goto(AnswerImportQuestionsWhichPriorityGoods(s.model))
      }

    final def submittedImportQuestionsAnswerWhichPriorityGoods(importPriorityGoods: ImportPriorityGoods) =
      Transition[State] { case AnswerImportQuestionsWhichPriorityGoods(model) =>
        gotoSummaryIfCompleteOr(
          AnswerImportQuestionsALVS(
            model.updated(model.importQuestionsAnswers.copy(priorityGoods = Some(importPriorityGoods)))
          )
        )
      }

    final val backToAnswerImportQuestionsALVS =
      Transition[State] { case s: ImportQuestionsState =>
        goto(AnswerImportQuestionsALVS(s.model))
      }

    final def submittedImportQuestionsAnswerHasALVS(importHasALVS: Boolean) =
      Transition[State] { case AnswerImportQuestionsALVS(model) =>
        gotoSummaryIfCompleteOr(
          AnswerImportQuestionsFreightType(
            model.updated(model.importQuestionsAnswers.copy(hasALVS = Some(importHasALVS)))
          )
        )
      }

    final val backToAnswerImportQuestionsFreightType =
      Transition[State] { case s: ImportQuestionsState =>
        goto(AnswerImportQuestionsFreightType(s.model))
      }

    final def submittedImportQuestionsAnswerFreightType(
      requireOptionalTransportPage: Boolean
    )(importFreightType: ImportFreightType) =
      Transition[State] { case AnswerImportQuestionsFreightType(model) =>
        val updatedImportQuestions = model.importQuestionsAnswers.copy(freightType = Some(importFreightType))
        if (Rules.isVesselDetailsAnswerMandatory(updatedImportQuestions))
          gotoSummaryIfCompleteOr(AnswerImportQuestionsMandatoryVesselInfo(model.updated(updatedImportQuestions)))
        else if (requireOptionalTransportPage)
          gotoSummaryIfCompleteOr(AnswerImportQuestionsOptionalVesselInfo(model.updated(updatedImportQuestions)))
        else
          gotoSummaryIfCompleteOr(
            AnswerImportQuestionsContactInfo(model.updated(updatedImportQuestions))
          )
      }

    final def submittedImportQuestionsMandatoryVesselDetails(vesselDetails: VesselDetails) =
      Transition[State] {
        case AnswerImportQuestionsMandatoryVesselInfo(model) if vesselDetails.isComplete =>
          gotoSummaryIfCompleteOr(
            AnswerImportQuestionsContactInfo(
              model.updated(
                model.importQuestionsAnswers.copy(vesselDetails = Some(vesselDetails))
              )
            )
          )
      }

    final val backToAnswerImportQuestionsVesselInfo =
      Transition[State] {
        case s: ImportQuestionsState if Rules.isVesselDetailsAnswerMandatory(s.model.importQuestionsAnswers) =>
          goto(AnswerImportQuestionsMandatoryVesselInfo(s.model))
        case s: ImportQuestionsState =>
          goto(AnswerImportQuestionsOptionalVesselInfo(s.model))
      }

    final def submittedImportQuestionsOptionalVesselDetails(vesselDetails: VesselDetails) =
      Transition[State] { case AnswerImportQuestionsOptionalVesselInfo(model) =>
        gotoSummaryIfCompleteOr(
          AnswerImportQuestionsContactInfo(
            model.updated(
              model.importQuestionsAnswers.copy(vesselDetails = Some(vesselDetails))
            )
          )
        )
      }

    final val backToAnswerImportQuestionsContactInfo =
      Transition[State] {
        case s: ImportQuestionsState =>
          goto(AnswerImportQuestionsContactInfo(s.model))

        case s: FileUploadState =>
          s.hostData.questionsAnswers match {
            case exportQuestionsAnswers: ExportQuestions =>
              goto(Start)

            case importQuestionsAnswers: ImportQuestions =>
              goto(
                AnswerImportQuestionsContactInfo(
                  ImportQuestionsStateModel(
                    entryDetails = s.hostData.entryDetails,
                    importQuestionsAnswers = importQuestionsAnswers,
                    fileUploadsOpt = Some(s.fileUploads)
                  )
                )
              )
          }
      }

    final def submittedImportQuestionsContactInfo(uploadMultipleFiles: Boolean)(
      upscanRequest: String => UpscanInitiateRequest
    )(
      upscanInitiate: UpscanInitiateApi
    )(contactInfo: ImportContactInfo)(implicit ec: ExecutionContext) =
      Transition[State] { case AnswerImportQuestionsContactInfo(model) =>
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
      Transition[State] {
        case state: CreateCaseConfirmation =>
          goto(CaseAlreadySubmitted)

        case state: FileUploadState =>
          state.hostData.questionsAnswers match {
            case exportQuestionsAnswers: ExportQuestions =>
              val updatedModel = ExportQuestionsStateModel(
                entryDetails = state.hostData.entryDetails,
                exportQuestionsAnswers = exportQuestionsAnswers,
                fileUploadsOpt = Some(state.fileUploads)
              )
              goto(ExportQuestionsSummary(updatedModel))

            case importQuestionsAnswers: ImportQuestions =>
              val updatedModel =
                ImportQuestionsStateModel(
                  entryDetails = state.hostData.entryDetails,
                  importQuestionsAnswers = importQuestionsAnswers,
                  fileUploadsOpt = Some(state.fileUploads)
                )
              goto(ImportQuestionsSummary(updatedModel))
          }

        case state: EnterEntryDetails =>
          goto(state.entryDetailsOpt match {
            case None => state
            case Some(entryDetails) =>
              state.exportQuestionsAnswersOpt
                .map { answers =>
                  val model = ExportQuestionsStateModel(entryDetails, answers, state.fileUploadsOpt)
                  ExportQuestionsSummary(model)
                }
                .orElse(
                  state.importQuestionsAnswersOpt.map { answers =>
                    val model = ImportQuestionsStateModel(entryDetails, answers, state.fileUploadsOpt)
                    ImportQuestionsSummary(model)
                  }
                )
                .getOrElse(state)
          })

        case state: ImportQuestionsState =>
          goto(ImportQuestionsSummary(state.model))

        case state: ExportQuestionsState =>
          goto(ExportQuestionsSummary(state.model))

      }

    final val backToImportQuestionsMissingInformationError =
      Transition[State] {
        case s: EnterEntryDetails if s.entryDetailsOpt.isDefined && s.importQuestionsAnswersOpt.isDefined =>
          goto(
            ImportQuestionsMissingInformationError(
              ImportQuestionsStateModel(s.entryDetailsOpt.get, s.importQuestionsAnswersOpt.get, s.fileUploadsOpt)
            )
          )
        case s: ImportQuestionsState =>
          goto(ImportQuestionsMissingInformationError(s.model))
      }

    final val backToExportQuestionsMissingInformationError =
      Transition[State] {
        case s: EnterEntryDetails if s.entryDetailsOpt.isDefined && s.exportQuestionsAnswersOpt.isDefined =>
          goto(
            ExportQuestionsMissingInformationError(
              ExportQuestionsStateModel(s.entryDetailsOpt.get, s.exportQuestionsAnswersOpt.get, s.fileUploadsOpt)
            )
          )
        case s: ExportQuestionsState =>
          goto(ExportQuestionsMissingInformationError(s.model))
      }

    type CreateCaseApi = TraderServicesCreateCaseRequest => Future[TraderServicesCaseResponse]

    object IsReadyForCreateCaseRequest {
      def unapply(s: State): Option[TraderServicesCreateCaseRequest] =
        s match {
          case state: ExportQuestionsSummary if Rules.isComplete(state.model) =>
            val uploadedFiles =
              state.model.fileUploadsOpt.get.toUploadedFiles
            Some(
              TraderServicesCreateCaseRequest(
                state.model.entryDetails,
                state.model.exportQuestionsAnswers,
                uploadedFiles,
                None
              )
            )

          case state: ImportQuestionsSummary if Rules.isComplete(state.model) =>
            val uploadedFiles =
              state.model.fileUploadsOpt.get.toUploadedFiles
            Some(
              TraderServicesCreateCaseRequest(
                state.model.entryDetails,
                state.model.importQuestionsAnswers,
                uploadedFiles,
                None
              )
            )

          case _ => None
        }

    }

    final def createCase(
      createCaseApi: CreateCaseApi
    )(uidAndEori: (Option[String], Option[String]))(implicit ec: ExecutionContext) = {

      def invokeCreateCaseApi(request: TraderServicesCreateCaseRequest) =
        createCaseApi(request)
          .transformWith {
            case Failure(exception) =>
              JourneyLog.logCreateCase(uidAndEori._1, request, exception)
              Future.failed(exception)

            case Success(response) =>
              JourneyLog.logCreateCase(uidAndEori._1, request, response)
              if (response.result.isDefined) {
                val createCaseResult = response.result.get
                goto(
                  CreateCaseConfirmation(
                    request.entryDetails,
                    request.questionsAnswers,
                    request.uploadedFiles,
                    createCaseResult,
                    CaseSLA.calculateFrom(
                      createCaseResult.generatedAt.asLondonClockTime,
                      request.questionsAnswers
                    )
                  )
                )
              } else
                response.error match {
                  case Some(ApiError("409", Some(caseReferenceId))) =>
                    goto(CaseAlreadyExists(caseReferenceId))

                  case _ =>
                    val message = response.error.map(_.errorCode).map(_ + " ").getOrElse("") +
                      response.error.map(_.errorMessage).getOrElse("")
                    fail(new RuntimeException(message))
                }
          }

      Transition[State] {
        case IsReadyForCreateCaseRequest(request) =>
          invokeCreateCaseApi(request.copy(eori = uidAndEori._2))

        case state: ExportQuestionsSummary =>
          goto(ExportQuestionsMissingInformationError(state.model))

        case state: ImportQuestionsSummary =>
          goto(ImportQuestionsMissingInformationError(state.model))
      }
    }
  }
}
