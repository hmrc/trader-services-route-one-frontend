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

import uk.gov.hmrc.play.fsm.JourneyModel
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.connectors.UpscanInitiateRequest
import scala.concurrent.Future
import uk.gov.hmrc.traderservices.connectors.UpscanInitiateResponse
import scala.concurrent.ExecutionContext
import uk.gov.hmrc.traderservices.connectors.TraderServicesCreateCaseRequest
import uk.gov.hmrc.traderservices.connectors.TraderServicesCreateCaseResponse
import uk.gov.hmrc.traderservices.journeys.TraderServicesFrontendJourneyModel.State.EnterDeclarationDetails
import uk.gov.hmrc.traderservices.journeys.TraderServicesFrontendJourneyModel.State.ExportQuestionsSummary

object TraderServicesFrontendJourneyModel extends JourneyModel {

  sealed trait State
  sealed trait IsError
  sealed trait IsTransient

  override val root: State = State.Start

  /** Model parametrization and rules. */
  object Rules {

    val mandatoryVesselDetailsRequestTypes: Set[ExportRequestType] =
      Set(ExportRequestType.C1601, ExportRequestType.C1602)

    def isVesselDetailsAnswerMandatory(exportQuestions: ExportQuestions): Boolean =
      exportQuestions.requestType.exists(mandatoryVesselDetailsRequestTypes.contains) || exportQuestions.routeType
        .contains(ExportRouteType.Hold)

    def isVesselDetailsAnswerMandatory(importQuestions: ImportQuestions): Boolean =
      importQuestions.routeType.contains(ImportRouteType.Hold)

    val maxFileUploadsNumber: Int = 10

    /** Checks is all export questions answers are in place. */
    def isComplete(exportQuestionsStateModel: ExportQuestionsStateModel): Boolean = {
      val answers = exportQuestionsStateModel.exportQuestionsAnswers

      val isPriorityGoodsComplete =
        answers.hasPriorityGoods.map(b => if (b) answers.priorityGoods.isDefined else true).getOrElse(false)

      val isVesselDetailsComplete = answers.vesselDetails
        .map(b => if (isVesselDetailsAnswerMandatory(answers)) b.isComplete else true)
        .getOrElse(false)

      answers.requestType.isDefined &&
      answers.routeType.isDefined &&
      isPriorityGoodsComplete &&
      answers.freightType.isDefined &&
      isVesselDetailsComplete &&
      answers.contactInfo.isDefined
    }

    /** Checks is all import questions answers are in place. */
    def isComplete(importQuestionsStateModel: ImportQuestionsStateModel): Boolean = {
      val answers = importQuestionsStateModel.importQuestionsAnswers

      val isPriorityGoodsComplete =
        answers.hasPriorityGoods.map(b => if (b) answers.priorityGoods.isDefined else true).getOrElse(false)

      val isVesselDetailsComplete = answers.vesselDetails
        .map(b => if (isVesselDetailsAnswerMandatory(answers)) b.isComplete else true)
        .getOrElse(false)

      answers.requestType.isDefined &&
      answers.routeType.isDefined &&
      isPriorityGoodsComplete &&
      answers.freightType.isDefined &&
      answers.hasALVS.isDefined &&
      isVesselDetailsComplete &&
      answers.contactInfo.isDefined
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

    sealed trait HasQuestionsAnswers {
      def questionsAnswers: QuestionsAnswers
    }

    sealed trait HasExportQuestionsStateModel {
      val model: ExportQuestionsStateModel
    }

    sealed trait HasImportQuestionsStateModel {
      val model: ImportQuestionsStateModel
    }

    sealed trait HasFileUploads {
      val fileUploads: FileUploads
    }

    // SPECIALIZED STATE TRAITS

    sealed trait ExportQuestionsState
        extends State with HasDeclarationDetails with HasExportQuestionsStateModel with HasQuestionsAnswers {
      def declarationDetails: DeclarationDetails = model.declarationDetails
      def exportQuestionsAnswers: ExportQuestions = model.exportQuestionsAnswers
      def questionsAnswers: QuestionsAnswers = exportQuestionsAnswers
    }

    sealed trait ImportQuestionsState
        extends State with HasDeclarationDetails with HasImportQuestionsStateModel with HasQuestionsAnswers {
      def declarationDetails: DeclarationDetails = model.declarationDetails
      def importQuestionsAnswers: ImportQuestions = model.importQuestionsAnswers
      def questionsAnswers: QuestionsAnswers = importQuestionsAnswers
    }

    sealed trait SummaryState extends State

    sealed trait FileUploadState extends State with HasDeclarationDetails with HasFileUploads with HasQuestionsAnswers

    // DECLARATION DETAILS

    case class EnterDeclarationDetails(
      declarationDetailsOpt: Option[DeclarationDetails] = None,
      exportQuestionsAnswersOpt: Option[ExportQuestions] = None,
      importQuestionsAnswersOpt: Option[ImportQuestions] = None,
      fileUploadsOpt: Option[FileUploads] = None
    ) extends State

    // EXPORT QUESTIONS

    case class AnswerExportQuestionsRequestType(
      model: ExportQuestionsStateModel
    ) extends ExportQuestionsState

    case class AnswerExportQuestionsRouteType(
      model: ExportQuestionsStateModel
    ) extends ExportQuestionsState

    case class AnswerExportQuestionsHasPriorityGoods(
      model: ExportQuestionsStateModel
    ) extends ExportQuestionsState

    case class AnswerExportQuestionsWhichPriorityGoods(
      model: ExportQuestionsStateModel
    ) extends ExportQuestionsState

    case class AnswerExportQuestionsFreightType(
      model: ExportQuestionsStateModel
    ) extends ExportQuestionsState

    case class AnswerExportQuestionsMandatoryVesselInfo(
      model: ExportQuestionsStateModel
    ) extends ExportQuestionsState

    case class AnswerExportQuestionsOptionalVesselInfo(
      model: ExportQuestionsStateModel
    ) extends ExportQuestionsState

    case class AnswerExportQuestionsContactInfo(
      model: ExportQuestionsStateModel
    ) extends ExportQuestionsState

    case class ExportQuestionsSummary(
      model: ExportQuestionsStateModel
    ) extends ExportQuestionsState with SummaryState

    // IMPORT QUESTIONS

    case class AnswerImportQuestionsRequestType(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState

    case class AnswerImportQuestionsRouteType(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState

    case class AnswerImportQuestionsHasPriorityGoods(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState

    case class AnswerImportQuestionsWhichPriorityGoods(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState

    case class AnswerImportQuestionsALVS(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState

    case class AnswerImportQuestionsFreightType(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState

    case class AnswerImportQuestionsOptionalVesselInfo(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState

    case class AnswerImportQuestionsMandatoryVesselInfo(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState

    case class AnswerImportQuestionsContactInfo(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState

    case class ImportQuestionsSummary(
      model: ImportQuestionsStateModel
    ) extends ImportQuestionsState with SummaryState

    // FILE UPLOAD

    case class UploadFile(
      declarationDetails: DeclarationDetails,
      questionsAnswers: QuestionsAnswers,
      reference: String,
      uploadRequest: UploadRequest,
      fileUploads: FileUploads,
      maybeUploadError: Option[FileUploadError] = None
    ) extends FileUploadState

    case class WaitingForFileVerification(
      declarationDetails: DeclarationDetails,
      questionsAnswers: QuestionsAnswers,
      reference: String,
      uploadRequest: UploadRequest,
      currentFileUpload: FileUpload,
      fileUploads: FileUploads
    ) extends FileUploadState with IsTransient

    case class FileUploaded(
      declarationDetails: DeclarationDetails,
      questionsAnswers: QuestionsAnswers,
      fileUploads: FileUploads,
      acknowledged: Boolean = false
    ) extends FileUploadState

    case class CreateCaseConfirmation(
      declarationDetails: DeclarationDetails,
      questionsAnswers: QuestionsAnswers,
      uploadedFiles: Seq[UploadedFile],
      caseReferenceId: String
    ) extends State

  }

  /**
    * Function determining if all questions were answered
    * and the user can proceed straight to the summary,
    * or rather shall she go to the next question.
    */
  def gotoSummaryIfCompleteOr(state: State): Future[State] =
    state match {
      case s: State.ExportQuestionsState =>
        if (Rules.isComplete(s.model)) goto(State.ExportQuestionsSummary(s.model))
        else goto(s)

      case s: State.ImportQuestionsState =>
        if (Rules.isComplete(s.model)) goto(State.ImportQuestionsSummary(s.model))
        else goto(s)

      case s => goto(s)
    }

  /** This is where things happen a.k.a bussiness logic of the service. */
  object Transitions {
    import State._

    def start(user: String) =
      Transition {
        case _ =>
          goto(Start)
      }

    def enterDeclarationDetails(user: String) =
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

        case _ =>
          goto(EnterDeclarationDetails(None))
      }

    def submittedDeclarationDetails(user: String)(declarationDetails: DeclarationDetails) =
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

    def backToAnswerExportQuestionsRequestType(user: String) =
      Transition {
        case s: ExportQuestionsState if s.model.exportQuestionsAnswers.requestType.isDefined =>
          goto(AnswerExportQuestionsRequestType(s.model))
      }

    def submittedExportQuestionsAnswerRequestType(user: String)(exportRequestType: ExportRequestType) =
      Transition {
        case AnswerExportQuestionsRequestType(model) =>
          val updatedExportQuestions = model.exportQuestionsAnswers.copy(requestType = Some(exportRequestType))
          gotoSummaryIfCompleteOr(AnswerExportQuestionsRouteType(model.updated(updatedExportQuestions)))
      }

    def backToAnswerExportQuestionsRouteType(user: String) =
      Transition {
        case s: ExportQuestionsState if s.model.exportQuestionsAnswers.routeType.isDefined =>
          goto(AnswerExportQuestionsRouteType(s.model))
      }

    def submittedExportQuestionsAnswerRouteType(user: String)(exportRouteType: ExportRouteType) =
      Transition {
        case AnswerExportQuestionsRouteType(model) =>
          gotoSummaryIfCompleteOr(
            AnswerExportQuestionsHasPriorityGoods(
              model.updated(model.exportQuestionsAnswers.copy(routeType = Some(exportRouteType)))
            )
          )
      }

    def backToAnswerExportQuestionsHasPriorityGoods(user: String) =
      Transition {
        case s: ExportQuestionsState if s.model.exportQuestionsAnswers.hasPriorityGoods.isDefined =>
          goto(AnswerExportQuestionsHasPriorityGoods(s.model))
      }

    def submittedExportQuestionsAnswerHasPriorityGoods(user: String)(exportHasPriorityGoods: Boolean) =
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

    def backToAnswerExportQuestionsWhichPriorityGoods(user: String) =
      Transition {
        case s: ExportQuestionsState if s.model.exportQuestionsAnswers.priorityGoods.isDefined =>
          goto(AnswerExportQuestionsWhichPriorityGoods(s.model))
      }

    def submittedExportQuestionsAnswerWhichPriorityGoods(user: String)(exportPriorityGoods: ExportPriorityGoods) =
      Transition {
        case AnswerExportQuestionsWhichPriorityGoods(model) =>
          gotoSummaryIfCompleteOr(
            AnswerExportQuestionsFreightType(
              model.updated(model.exportQuestionsAnswers.copy(priorityGoods = Some(exportPriorityGoods)))
            )
          )
      }

    def backToAnswerExportQuestionsFreightType(user: String) =
      Transition {
        case s: ExportQuestionsState if s.model.exportQuestionsAnswers.freightType.isDefined =>
          goto(AnswerExportQuestionsFreightType(s.model))
      }

    def submittedExportQuestionsAnswerFreightType(user: String)(exportFreightType: ExportFreightType) =
      Transition {
        case AnswerExportQuestionsFreightType(model) =>
          val updatedExportQuestions = model.exportQuestionsAnswers.copy(freightType = Some(exportFreightType))
          if (Rules.isVesselDetailsAnswerMandatory(updatedExportQuestions))
            gotoSummaryIfCompleteOr(AnswerExportQuestionsMandatoryVesselInfo(model.updated(updatedExportQuestions)))
          else
            gotoSummaryIfCompleteOr(AnswerExportQuestionsOptionalVesselInfo(model.updated(updatedExportQuestions)))
      }

    def backToAnswerExportQuestionsMandatoryVesselInfo(user: String) =
      Transition {
        case s: ExportQuestionsState if s.model.exportQuestionsAnswers.vesselDetails.isDefined =>
          goto(AnswerExportQuestionsMandatoryVesselInfo(s.model))
      }

    def submittedExportQuestionsMandatoryVesselDetails(user: String)(vesselDetails: VesselDetails) =
      Transition {
        case AnswerExportQuestionsMandatoryVesselInfo(model) if vesselDetails.isComplete =>
          gotoSummaryIfCompleteOr(
            AnswerExportQuestionsContactInfo(
              model.updated(model.exportQuestionsAnswers.copy(vesselDetails = Some(vesselDetails)))
            )
          )
      }

    def backToAnswerExportQuestionsOptionalVesselInfo(user: String) =
      Transition {
        case s: ExportQuestionsState if s.model.exportQuestionsAnswers.vesselDetails.isDefined =>
          goto(AnswerExportQuestionsOptionalVesselInfo(s.model))
      }

    def submittedExportQuestionsOptionalVesselDetails(user: String)(vesselDetails: VesselDetails) =
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

    def backToAnswerExportQuestionsContactInfo(user: String) =
      Transition {
        case s: ExportQuestionsState if s.model.exportQuestionsAnswers.contactInfo.isDefined =>
          goto(AnswerExportQuestionsContactInfo(s.model))
      }

    def submittedExportQuestionsContactInfo(user: String)(contactInfo: ExportContactInfo) =
      Transition {
        case AnswerExportQuestionsContactInfo(model) =>
          gotoSummaryIfCompleteOr(
            ExportQuestionsSummary(
              model.updated(model.exportQuestionsAnswers.copy(contactInfo = Some(contactInfo)))
            )
          )
      }

    def backToQuestionsSummary(user: String) =
      Transition {
        case s: FileUploadState =>
          s.questionsAnswers match {
            case answers: ExportQuestions =>
              goto(
                ExportQuestionsSummary(
                  ExportQuestionsStateModel(s.declarationDetails, answers, Some(s.fileUploads))
                )
              )

            case answers: ImportQuestions =>
              goto(
                ImportQuestionsSummary(
                  ImportQuestionsStateModel(s.declarationDetails, answers, Some(s.fileUploads))
                )
              )
          }
      }

    def backToAnswerImportQuestionsRequestType(user: String) =
      Transition {
        case s: ImportQuestionsState if s.model.importQuestionsAnswers.requestType.isDefined =>
          goto(AnswerImportQuestionsRequestType(s.model))
      }

    def submittedImportQuestionsAnswersRequestType(user: String)(importRequestType: ImportRequestType) =
      Transition {
        case AnswerImportQuestionsRequestType(model) =>
          val updatedImportQuestions = model.importQuestionsAnswers.copy(requestType = Some(importRequestType))
          gotoSummaryIfCompleteOr(AnswerImportQuestionsRouteType(model.updated(updatedImportQuestions)))
      }

    def backToAnswerImportQuestionsRouteType(user: String) =
      Transition {
        case s: ImportQuestionsState if s.model.importQuestionsAnswers.routeType.isDefined =>
          goto(AnswerImportQuestionsRouteType(s.model))
      }

    def submittedImportQuestionsAnswerRouteType(user: String)(importRouteType: ImportRouteType) =
      Transition {
        case AnswerImportQuestionsRouteType(model) =>
          gotoSummaryIfCompleteOr(
            AnswerImportQuestionsHasPriorityGoods(
              model.updated(model.importQuestionsAnswers.copy(routeType = Some(importRouteType)))
            )
          )
      }

    def backToAnswerImportQuestionsHasPriorityGoods(user: String) =
      Transition {
        case s: ImportQuestionsState if s.model.importQuestionsAnswers.hasPriorityGoods.isDefined =>
          goto(AnswerImportQuestionsHasPriorityGoods(s.model))
      }

    def submittedImportQuestionsAnswerHasPriorityGoods(user: String)(importHasPriorityGoods: Boolean) =
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

    def backToAnswerImportQuestionsWhichPriorityGoods(user: String) =
      Transition {
        case s: ImportQuestionsState if s.model.importQuestionsAnswers.priorityGoods.isDefined =>
          goto(AnswerImportQuestionsWhichPriorityGoods(s.model))
      }

    def submittedImportQuestionsAnswerWhichPriorityGoods(user: String)(importPriorityGoods: ImportPriorityGoods) =
      Transition {
        case AnswerImportQuestionsWhichPriorityGoods(model) =>
          gotoSummaryIfCompleteOr(
            AnswerImportQuestionsALVS(
              model.updated(model.importQuestionsAnswers.copy(priorityGoods = Some(importPriorityGoods)))
            )
          )
      }

    def backToAnswerImportQuestionsALVS(user: String) =
      Transition {
        case s: ImportQuestionsState if s.model.importQuestionsAnswers.hasALVS.isDefined =>
          goto(AnswerImportQuestionsALVS(s.model))
      }

    def submittedImportQuestionsAnswerHasALVS(user: String)(importHasALVS: Boolean) =
      Transition {
        case AnswerImportQuestionsALVS(model) =>
          gotoSummaryIfCompleteOr(
            AnswerImportQuestionsFreightType(
              model.updated(model.importQuestionsAnswers.copy(hasALVS = Some(importHasALVS)))
            )
          )
      }

    def backToAnswerImportQuestionsFreightType(user: String) =
      Transition {
        case s: ImportQuestionsState if s.model.importQuestionsAnswers.freightType.isDefined =>
          goto(AnswerImportQuestionsFreightType(s.model))
      }

    def submittedImportQuestionsAnswerFreightType(user: String)(importFreightType: ImportFreightType) =
      Transition {
        case AnswerImportQuestionsFreightType(model) =>
          val updatedImportQuestions = model.importQuestionsAnswers.copy(freightType = Some(importFreightType))
          if (Rules.isVesselDetailsAnswerMandatory(updatedImportQuestions))
            gotoSummaryIfCompleteOr(AnswerImportQuestionsMandatoryVesselInfo(model.updated(updatedImportQuestions)))
          else
            gotoSummaryIfCompleteOr(AnswerImportQuestionsOptionalVesselInfo(model.updated(updatedImportQuestions)))
      }

    def backToAnswerImportQuestionsMandatoryVesselInfo(user: String) =
      Transition {
        case s: ImportQuestionsState if s.model.importQuestionsAnswers.vesselDetails.isDefined =>
          goto(AnswerImportQuestionsMandatoryVesselInfo(s.model))
      }

    def submittedImportQuestionsMandatoryVesselDetails(user: String)(vesselDetails: VesselDetails) =
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

    def backToAnswerImportQuestionsOptionalVesselInfo(user: String) =
      Transition {
        case s: ImportQuestionsState if s.model.importQuestionsAnswers.vesselDetails.isDefined =>
          goto(AnswerImportQuestionsOptionalVesselInfo(s.model))
      }

    def submittedImportQuestionsOptionalVesselDetails(user: String)(vesselDetails: VesselDetails) =
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

    def backToAnswerImportQuestionsContactInfo(user: String) =
      Transition {
        case s: ImportQuestionsState if s.model.importQuestionsAnswers.contactInfo.isDefined =>
          goto(AnswerImportQuestionsContactInfo(s.model))
      }

    def submittedImportQuestionsContactInfo(user: String)(contactInfo: ImportContactInfo) =
      Transition {
        case AnswerImportQuestionsContactInfo(model) =>
          gotoSummaryIfCompleteOr(
            ImportQuestionsSummary(
              model.updated(model.importQuestionsAnswers.copy(contactInfo = Some(contactInfo)))
            )
          )
      }

    type UpscanInitiateApi = UpscanInitiateRequest => Future[UpscanInitiateResponse]

    def initiateFileUpload(
      callbackUrl: String,
      successRedirect: String,
      errorRedirect: String,
      maxFileSizeMb: Int
    )(upscanInitiate: UpscanInitiateApi)(user: String)(implicit ec: ExecutionContext) =
      Transition {
        case ExportQuestionsSummary(model) =>
          val fileUploads = model.fileUploadsOpt.getOrElse(FileUploads())
          if (fileUploads.nonEmpty || fileUploads.acceptedCount >= Rules.maxFileUploadsNumber)
            goto(
              FileUploaded(
                model.declarationDetails,
                model.exportQuestionsAnswers,
                fileUploads
              )
            )
          else
            for {
              upscanResponse <- upscanInitiate(
                                  UpscanInitiateRequest(
                                    callbackUrl = callbackUrl,
                                    successRedirect = Some(successRedirect),
                                    errorRedirect = Some(errorRedirect),
                                    maximumFileSize = Some(maxFileSizeMb * 1024 * 1024)
                                  )
                                )
            } yield UploadFile(
              model.declarationDetails,
              model.exportQuestionsAnswers,
              upscanResponse.reference,
              upscanResponse.uploadRequest,
              fileUploads.copy(files =
                fileUploads.files :+ FileUpload.Initiated(fileUploads.files.size + 1, upscanResponse.reference)
              )
            )

        case ImportQuestionsSummary(model) =>
          val fileUploads = model.fileUploadsOpt.getOrElse(FileUploads())
          if (fileUploads.nonEmpty || fileUploads.acceptedCount >= Rules.maxFileUploadsNumber)
            goto(
              FileUploaded(
                model.declarationDetails,
                model.importQuestionsAnswers,
                fileUploads
              )
            )
          else
            for {
              upscanResponse <- upscanInitiate(
                                  UpscanInitiateRequest(
                                    callbackUrl = callbackUrl,
                                    successRedirect = Some(successRedirect),
                                    errorRedirect = Some(errorRedirect),
                                    maximumFileSize = Some(maxFileSizeMb * 1024 * 1024)
                                  )
                                )
            } yield UploadFile(
              model.declarationDetails,
              model.importQuestionsAnswers,
              upscanResponse.reference,
              upscanResponse.uploadRequest,
              fileUploads.copy(files =
                fileUploads.files :+ FileUpload.Initiated(fileUploads.files.size + 1, upscanResponse.reference)
              )
            )

        case WaitingForFileVerification(
              declarationDetails,
              questionsAnswers,
              reference,
              uploadRequest,
              currentFileUpload,
              fileUploads
            ) =>
          goto(UploadFile(declarationDetails, questionsAnswers, reference, uploadRequest, fileUploads))

        case current @ FileUploaded(declarationDetails, questionsAnswers, fileUploads, _) =>
          if (fileUploads.acceptedCount >= Rules.maxFileUploadsNumber)
            goto(current)
          else
            for {
              upscanResponse <- upscanInitiate(
                                  UpscanInitiateRequest(
                                    callbackUrl = callbackUrl,
                                    successRedirect = Some(successRedirect),
                                    errorRedirect = Some(errorRedirect),
                                    maximumFileSize = Some(maxFileSizeMb * 1024 * 1024)
                                  )
                                )
            } yield UploadFile(
              declarationDetails,
              questionsAnswers,
              upscanResponse.reference,
              upscanResponse.uploadRequest,
              fileUploads.copy(files =
                fileUploads.files :+ FileUpload.Initiated(fileUploads.files.size + 1, upscanResponse.reference)
              )
            )
      }

    def fileUploadWasRejected(user: String)(error: S3UploadError) =
      Transition {
        case current @ UploadFile(
              declarationDetails,
              questionsAnswers,
              reference,
              uploadRequest,
              fileUploads,
              maybeUploadError
            ) =>
          val updatedFileUploads = fileUploads.copy(files = fileUploads.files.map {
            case FileUpload.Initiated(orderNumber, ref) if ref == error.key =>
              FileUpload.Rejected(orderNumber, reference, error)
            case u => u
          })
          goto(current.copy(fileUploads = updatedFileUploads, maybeUploadError = Some(FileTransmissionFailed(error))))
      }

    def waitForFileVerification(user: String) =
      Transition {
        case current @ UploadFile(
              declarationDetails,
              questionsAnswers,
              reference,
              uploadRequest,
              fileUploads,
              errorOpt
            ) =>
          val updatedFileUploads = fileUploads.copy(files = fileUploads.files.map {
            case f: FileUpload.Accepted => f
            case FileUpload(orderNumber, ref) if ref == reference =>
              FileUpload.Posted(orderNumber, reference)
            case f => f
          })
          updatedFileUploads.files.find(_.reference == reference) match {
            case Some(upload: FileUpload.Posted) =>
              goto(
                WaitingForFileVerification(
                  declarationDetails,
                  questionsAnswers,
                  reference,
                  uploadRequest,
                  upload,
                  updatedFileUploads
                )
              )

            case Some(acceptedFile: FileUpload.Accepted) =>
              goto(FileUploaded(declarationDetails, questionsAnswers, updatedFileUploads))

            case Some(failedFile: FileUpload.Failed) =>
              goto(
                UploadFile(
                  declarationDetails,
                  questionsAnswers,
                  reference,
                  uploadRequest,
                  updatedFileUploads,
                  Some(FileVerificationFailed(failedFile.details))
                )
              )

            case _ =>
              goto(UploadFile(declarationDetails, questionsAnswers, reference, uploadRequest, updatedFileUploads))
          }

        case current @ WaitingForFileVerification(
              declarationDetails,
              questionsAnswers,
              reference,
              uploadRequest,
              currentFileUpload,
              fileUploads
            ) =>
          fileUploads.files.find(_.reference == reference) match {
            case Some(upload: FileUpload.Posted) =>
              goto(current)

            case Some(acceptedFile: FileUpload.Accepted) =>
              goto(FileUploaded(declarationDetails, questionsAnswers, fileUploads))

            case Some(failedFile: FileUpload.Failed) =>
              goto(
                UploadFile(
                  declarationDetails,
                  questionsAnswers,
                  reference,
                  uploadRequest,
                  fileUploads,
                  Some(FileVerificationFailed(failedFile.details))
                )
              )

            case _ =>
              goto(UploadFile(declarationDetails, questionsAnswers, reference, uploadRequest, fileUploads))
          }

        case state: FileUploaded =>
          goto(state.copy(acknowledged = true))
      }

    def upscanCallbackArrived(notification: UpscanNotification) = {

      def updateFileUploads(fileUploads: FileUploads) =
        fileUploads.copy(files = fileUploads.files.map {
          case FileUpload(orderNumber, ref) if ref == notification.reference =>
            notification match {
              case UpscanFileReady(_, url, uploadDetails) =>
                FileUpload.Accepted(
                  orderNumber,
                  ref,
                  url,
                  uploadDetails.uploadTimestamp,
                  uploadDetails.checksum,
                  uploadDetails.fileName,
                  uploadDetails.fileMimeType
                )
              case UpscanFileFailed(_, failureDetails) =>
                FileUpload.Failed(
                  orderNumber,
                  ref,
                  failureDetails
                )
            }
          case u => u
        })

      Transition {
        case WaitingForFileVerification(
              declarationDetails,
              questionsAnswers,
              reference,
              uploadRequest,
              currentFileUpload,
              fileUploads
            ) =>
          val updatedFileUploads = updateFileUploads(fileUploads)
          updatedFileUploads.files.find(_.reference == reference) match {
            case None =>
              goto(
                WaitingForFileVerification(
                  declarationDetails,
                  questionsAnswers,
                  reference,
                  uploadRequest,
                  currentFileUpload,
                  updatedFileUploads
                )
              )

            case Some(upload: FileUpload.Posted) =>
              goto(
                WaitingForFileVerification(
                  declarationDetails,
                  questionsAnswers,
                  reference,
                  uploadRequest,
                  upload,
                  updatedFileUploads
                )
              )

            case Some(acceptedFile: FileUpload.Accepted) =>
              goto(FileUploaded(declarationDetails, questionsAnswers, updatedFileUploads))

            case Some(failedFile: FileUpload.Failed) =>
              goto(
                UploadFile(
                  declarationDetails,
                  questionsAnswers,
                  reference,
                  uploadRequest,
                  updatedFileUploads,
                  Some(FileVerificationFailed(failedFile.details))
                )
              )

            case _ =>
              goto(UploadFile(declarationDetails, questionsAnswers, reference, uploadRequest, updatedFileUploads))

          }

        case UploadFile(declarationDetails, questionsAnswers, reference, uploadRequest, fileUploads, errorOpt) =>
          val updatedFileUploads = updateFileUploads(fileUploads)
          updatedFileUploads.files.find(_.reference == reference) match {
            case Some(acceptedFile: FileUpload.Accepted) =>
              goto(FileUploaded(declarationDetails, questionsAnswers, updatedFileUploads))

            case Some(failedFile: FileUpload.Failed) =>
              goto(
                UploadFile(
                  declarationDetails,
                  questionsAnswers,
                  reference,
                  uploadRequest,
                  updatedFileUploads,
                  Some(FileVerificationFailed(failedFile.details))
                )
              )

            case _ =>
              goto(UploadFile(declarationDetails, questionsAnswers, reference, uploadRequest, updatedFileUploads))

          }
      }
    }

    type CreateCaseApi = TraderServicesCreateCaseRequest => Future[TraderServicesCreateCaseResponse]

    def submitedUploadAnotherFileChoice(
      callbackUrl: String,
      successRedirect: String,
      errorRedirect: String,
      maxFileSizeMb: Int
    )(
      upscanInitiate: UpscanInitiateApi
    )(createCaseApi: CreateCaseApi)(user: String)(uploadAnotherFile: Boolean)(implicit ec: ExecutionContext) =
      Transition {
        case current @ FileUploaded(declarationDetails, questionsAnswers, fileUploads, acknowledged) =>
          if (uploadAnotherFile && fileUploads.acceptedCount < Rules.maxFileUploadsNumber)
            initiateFileUpload(callbackUrl, successRedirect, errorRedirect, maxFileSizeMb)(upscanInitiate)(user)
              .apply(current)
          else
            createCase(createCaseApi)(user).apply(current)
      }

    def removeFileUploadByReference(reference: String)(
      callbackUrl: String,
      successRedirect: String,
      errorRedirect: String,
      maxFileSizeMb: Int
    )(upscanInitiate: UpscanInitiateApi)(user: String)(implicit ec: ExecutionContext) =
      Transition {
        case current: FileUploaded =>
          val updatedFileUploads = current.fileUploads
            .copy(files = current.fileUploads.files.filterNot(_.reference == reference))
          val updatedCurrentState = current.copy(fileUploads = updatedFileUploads)
          if (updatedFileUploads.isEmpty)
            initiateFileUpload(callbackUrl, successRedirect, errorRedirect, maxFileSizeMb)(upscanInitiate)(user)
              .apply(updatedCurrentState)
          else
            goto(updatedCurrentState)
      }

    def createCase(createCaseApi: CreateCaseApi)(eori: String)(implicit ec: ExecutionContext) =
      Transition {
        case FileUploaded(declarationDetails, questionsAnswers, fileUploads, _) =>
          val request =
            TraderServicesCreateCaseRequest(declarationDetails, questionsAnswers, fileUploads.toUploadedFiles, eori)
          createCaseApi(request).flatMap { response =>
            if (response.result.isDefined)
              goto(
                CreateCaseConfirmation(
                  declarationDetails,
                  questionsAnswers,
                  fileUploads.toUploadedFiles,
                  response.result.get
                )
              )
            else {
              val error = response.error.map(_.errorCode).map(_ + " ").getOrElse("") +
                response.error.map(_.errorMessage).getOrElse("")
              fail(new RuntimeException(error))
            }
          }
      }
  }
}
