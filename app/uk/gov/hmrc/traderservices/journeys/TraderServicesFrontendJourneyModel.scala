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

object TraderServicesFrontendJourneyModel extends JourneyModel {

  sealed trait State
  sealed trait IsError
  sealed trait IsTransient

  override val root: State = State.Start

  object State {

    case object Start extends State

    case class EnterDeclarationDetails(
      declarationDetailsOpt: Option[DeclarationDetails] = None,
      exportQuestionsAnswersOpt: Option[ExportQuestions] = None,
      importQuestionsAnswersOpt: Option[ImportQuestions] = None
    ) extends State

    trait HasDeclarationDetails {
      def declarationDetails: DeclarationDetails
    }

    case object WorkInProgressDeadEnd extends State

    // EXPORT QUESTIONS

    trait HasExportQuestions {
      val exportQuestionsAnswers: ExportQuestions
    }

    sealed trait ExportQuestionsState extends State with HasDeclarationDetails with HasExportQuestions

    case class AnswerExportQuestionsRequestType(
      declarationDetails: DeclarationDetails,
      exportQuestionsAnswers: ExportQuestions
    ) extends ExportQuestionsState

    case class AnswerExportQuestionsRouteType(
      declarationDetails: DeclarationDetails,
      exportQuestionsAnswers: ExportQuestions
    ) extends ExportQuestionsState

    case class AnswerExportQuestionsHasPriorityGoods(
      declarationDetails: DeclarationDetails,
      exportQuestionsAnswers: ExportQuestions
    ) extends ExportQuestionsState

    case class AnswerExportQuestionsWhichPriorityGoods(
      declarationDetails: DeclarationDetails,
      exportQuestionsAnswers: ExportQuestions
    ) extends ExportQuestionsState

    case class AnswerExportQuestionsFreightType(
      declarationDetails: DeclarationDetails,
      exportQuestionsAnswers: ExportQuestions
    ) extends ExportQuestionsState

    case class AnswerExportQuestionsMandatoryVesselInfo(
      declarationDetails: DeclarationDetails,
      exportQuestionsAnswers: ExportQuestions
    ) extends ExportQuestionsState

    case class AnswerExportQuestionsOptionalVesselInfo(
      declarationDetails: DeclarationDetails,
      exportQuestionsAnswers: ExportQuestions
    ) extends ExportQuestionsState

    case class AnswerExportQuestionsContactInfo(
      declarationDetails: DeclarationDetails,
      exportQuestionsAnswers: ExportQuestions
    ) extends ExportQuestionsState

    case class ExportQuestionsSummary(
      declarationDetails: DeclarationDetails,
      exportQuestionsAnswers: ExportQuestions
    ) extends ExportQuestionsState

    // IMPORT QUESTIONS

    trait HasImportQuestions {
      val importQuestionsAnswers: ImportQuestions
    }

    sealed trait ImportQuestionsState extends State with HasDeclarationDetails with HasImportQuestions

    case class AnswerImportQuestionsRequestType(
      declarationDetails: DeclarationDetails,
      importQuestionsAnswers: ImportQuestions
    ) extends ImportQuestionsState

    case class AnswerImportQuestionsRouteType(
      declarationDetails: DeclarationDetails,
      importQuestionsAnswers: ImportQuestions
    ) extends ImportQuestionsState

    case class AnswerImportQuestionsHasPriorityGoods(
      declarationDetails: DeclarationDetails,
      importQuestionsAnswers: ImportQuestions
    ) extends ImportQuestionsState

    case class AnswerImportQuestionsWhichPriorityGoods(
      declarationDetails: DeclarationDetails,
      importQuestionsAnswers: ImportQuestions
    ) extends ImportQuestionsState

    case class AnswerImportQuestionsALVS(
      declarationDetails: DeclarationDetails,
      importQuestionsAnswers: ImportQuestions
    ) extends ImportQuestionsState

    case class AnswerImportQuestionsFreightType(
      declarationDetails: DeclarationDetails,
      importQuestionsAnswers: ImportQuestions
    ) extends ImportQuestionsState

    case class AnswerImportQuestionsOptionalVesselInfo(
      declarationDetails: DeclarationDetails,
      importQuestionsAnswers: ImportQuestions
    ) extends ImportQuestionsState

    case class AnswerImportQuestionsMandatoryVesselInfo(
      declarationDetails: DeclarationDetails,
      importQuestionsAnswers: ImportQuestions
    ) extends ImportQuestionsState

    case class AnswerImportQuestionsContactInfo(
      declarationDetails: DeclarationDetails,
      importQuestionsAnswers: ImportQuestions
    ) extends ImportQuestionsState

    case class ImportQuestionsSummary(
      declarationDetails: DeclarationDetails,
      importQuestionsAnswers: ImportQuestions
    ) extends ImportQuestionsState

    case class UploadFile(
      declarationDetails: DeclarationDetails,
      questionsAnswers: QuestionsAnswers,
      reference: String,
      uploadRequest: UploadRequest,
      fileUploads: FileUploads
    ) extends State

    case class WaitingForFileVerification(
      declarationDetails: DeclarationDetails,
      questionsAnswers: QuestionsAnswers,
      reference: String,
      uploadRequest: UploadRequest,
      currentFileUpload: FileUpload,
      fileUploads: FileUploads
    ) extends State with IsTransient

    case class FileUploaded(
      declarationDetails: DeclarationDetails,
      questionsAnswers: QuestionsAnswers,
      acceptedFile: FileUpload.Accepted,
      fileUploads: FileUploads,
      acknowledged: Boolean = false
    ) extends State

  }

  object Rules {

    val mandatoryVesselDetailsRequestTypes: Set[ExportRequestType] =
      Set(ExportRequestType.Hold, ExportRequestType.C1601, ExportRequestType.C1602)

    def shouldAskRouteQuestion(exportQuestions: ExportQuestions): Boolean =
      exportQuestions.requestType.forall(_ != ExportRequestType.Hold)

    def isVesselDetailsAnswerMandatory(exportQuestions: ExportQuestions): Boolean =
      exportQuestions.requestType.exists(mandatoryVesselDetailsRequestTypes.contains)

    def shouldAskRouteQuestion(importQuestions: ImportQuestions): Boolean =
      importQuestions.requestType.forall(_ != ImportRequestType.Hold)

    def isVesselDetailsAnswerMandatory(importQuestions: ImportQuestions): Boolean =
      importQuestions.requestType.contains(ImportRequestType.Hold)

    val maxFileUploadsNumber = 10

  }

  object Mergers {

    val copyDeclarationDetails = Merger[State.EnterDeclarationDetails] {
      case (s, d: State with State.HasDeclarationDetails with State.HasExportQuestions) =>
        s.copy(
          declarationDetailsOpt = Some(d.declarationDetails),
          exportQuestionsAnswersOpt = Some(d.exportQuestionsAnswers)
        )

      case (s, d: State with State.HasDeclarationDetails with State.HasImportQuestions) =>
        s.copy(
          declarationDetailsOpt = Some(d.declarationDetails),
          importQuestionsAnswersOpt = Some(d.importQuestionsAnswers)
        )

      case (s, d: State with State.HasDeclarationDetails) =>
        s.copy(
          declarationDetailsOpt = Some(d.declarationDetails)
        )
    }

    def copyExportQuestions[S <: State: Setters.SetExportQuestions] =
      Merger[S] {
        case (s, d: State with State.HasExportQuestions) =>
          implicitly[Setters.SetExportQuestions[S]].set(s, d.exportQuestionsAnswers)
      }

    def copyImportQuestions[S <: State: Setters.SetImportQuestions] =
      Merger[S] {
        case (s, d: State with State.HasImportQuestions) =>
          implicitly[Setters.SetImportQuestions[S]].set(s, d.importQuestionsAnswers)
      }
  }

  object Transitions {
    import State._

    def start(user: String) =
      Transition {
        case _ =>
          goto(Start)
      }

    def enterDeclarationDetails(user: String) =
      Transition {
        case s: HasDeclarationDetails =>
          goto(EnterDeclarationDetails(Some(s.declarationDetails)))

        case _ =>
          goto(EnterDeclarationDetails(None))
      }

    def submittedDeclarationDetails(user: String)(declarationDetails: DeclarationDetails) =
      Transition {
        case EnterDeclarationDetails(_, exportQuestionsOpt, importQuestionsOpt) =>
          if (declarationDetails.isExportDeclaration)
            goto(AnswerExportQuestionsRequestType(declarationDetails, exportQuestionsOpt.getOrElse(ExportQuestions())))
          else
            goto(AnswerImportQuestionsRequestType(declarationDetails, importQuestionsOpt.getOrElse(ImportQuestions())))
      }

    def submittedExportQuestionsAnswerRequestType(user: String)(exportRequestType: ExportRequestType) =
      Transition {
        case AnswerExportQuestionsRequestType(declarationDetails, exportQuestions) =>
          val updatedExportQuestions = exportQuestions.copy(requestType = Some(exportRequestType))
          if (Rules.shouldAskRouteQuestion(updatedExportQuestions))
            goto(AnswerExportQuestionsRouteType(declarationDetails, updatedExportQuestions))
          else
            goto(
              AnswerExportQuestionsHasPriorityGoods(declarationDetails, updatedExportQuestions.copy(routeType = None))
            )
      }

    def submittedExportQuestionsAnswerRouteType(user: String)(exportRouteType: ExportRouteType) =
      Transition {
        case AnswerExportQuestionsRouteType(declarationDetails, exportQuestions) =>
          goto(
            AnswerExportQuestionsHasPriorityGoods(
              declarationDetails,
              exportQuestions.copy(routeType = Some(exportRouteType))
            )
          )
      }

    def submittedExportQuestionsAnswerHasPriorityGoods(user: String)(exportHasPriorityGoods: Boolean) =
      Transition {
        case AnswerExportQuestionsHasPriorityGoods(declarationDetails, exportQuestions) =>
          if (exportHasPriorityGoods)
            goto(
              AnswerExportQuestionsWhichPriorityGoods(
                declarationDetails,
                exportQuestions.copy(hasPriorityGoods = Some(exportHasPriorityGoods))
              )
            )
          else
            goto(
              AnswerExportQuestionsFreightType(
                declarationDetails,
                exportQuestions.copy(hasPriorityGoods = Some(exportHasPriorityGoods))
              )
            )
      }

    def submittedExportQuestionsAnswerWhichPriorityGoods(user: String)(exportPriorityGoods: ExportPriorityGoods) =
      Transition {
        case AnswerExportQuestionsWhichPriorityGoods(declarationDetails, exportQuestions) =>
          goto(
            AnswerExportQuestionsFreightType(
              declarationDetails,
              exportQuestions.copy(priorityGoods = Some(exportPriorityGoods))
            )
          )
      }

    def submittedExportQuestionsAnswerFreightType(user: String)(exportFreightType: ExportFreightType) =
      Transition {
        case AnswerExportQuestionsFreightType(declarationDetails, exportQuestions) =>
          val updatedExportQuestions = exportQuestions.copy(freightType = Some(exportFreightType))
          if (Rules.isVesselDetailsAnswerMandatory(updatedExportQuestions))
            goto(AnswerExportQuestionsMandatoryVesselInfo(declarationDetails, updatedExportQuestions))
          else
            goto(AnswerExportQuestionsOptionalVesselInfo(declarationDetails, updatedExportQuestions))
      }

    def submittedExportQuestionsMandatoryVesselDetails(user: String)(vesselDetails: VesselDetails) =
      Transition {
        case AnswerExportQuestionsMandatoryVesselInfo(declarationDetails, exportQuestions)
            if vesselDetails.isComplete =>
          goto(
            AnswerExportQuestionsContactInfo(
              declarationDetails,
              exportQuestions.copy(vesselDetails = Some(vesselDetails))
            )
          )
      }

    def submittedExportQuestionsOptionalVesselDetails(user: String)(vesselDetails: VesselDetails) =
      Transition {
        case AnswerExportQuestionsOptionalVesselInfo(declarationDetails, exportQuestions) =>
          goto(
            AnswerExportQuestionsContactInfo(
              declarationDetails,
              exportQuestions.copy(vesselDetails = if (vesselDetails.isEmpty) None else Some(vesselDetails))
            )
          )
      }

    def submittedExportQuestionsContactInfo(user: String)(contactInfo: ExportContactInfo) =
      Transition {
        case AnswerExportQuestionsContactInfo(declarationDetails, exportQuestions) =>
          goto(
            ExportQuestionsSummary(
              declarationDetails,
              exportQuestions.copy(contactInfo = Some(contactInfo))
            )
          )
      }

    def submittedImportQuestionsAnswersRequestType(user: String)(importRequestType: ImportRequestType) =
      Transition {
        case AnswerImportQuestionsRequestType(declarationDetails, importQuestions) =>
          val updatedImportQuestions = importQuestions.copy(requestType = Some(importRequestType))
          if (Rules.shouldAskRouteQuestion(updatedImportQuestions))
            goto(AnswerImportQuestionsRouteType(declarationDetails, updatedImportQuestions))
          else
            goto(
              AnswerImportQuestionsHasPriorityGoods(declarationDetails, updatedImportQuestions.copy(routeType = None))
            )

      }

    def submittedImportQuestionsAnswerRouteType(user: String)(importRouteType: ImportRouteType) =
      Transition {
        case AnswerImportQuestionsRouteType(declarationDetails, importQuestions) =>
          goto(
            AnswerImportQuestionsHasPriorityGoods(
              declarationDetails,
              importQuestions.copy(routeType = Some(importRouteType))
            )
          )
      }

    def submittedImportQuestionsAnswerHasPriorityGoods(user: String)(importHasPriorityGoods: Boolean) =
      Transition {
        case AnswerImportQuestionsHasPriorityGoods(declarationDetails, importQuestions) =>
          if (importHasPriorityGoods)
            goto(
              AnswerImportQuestionsWhichPriorityGoods(
                declarationDetails,
                importQuestions.copy(hasPriorityGoods = Some(importHasPriorityGoods))
              )
            )
          else
            goto(
              AnswerImportQuestionsALVS(
                declarationDetails,
                importQuestions.copy(hasPriorityGoods = Some(importHasPriorityGoods))
              )
            )
      }

    def submittedImportQuestionsAnswerWhichPriorityGoods(user: String)(importPriorityGoods: ImportPriorityGoods) =
      Transition {
        case AnswerImportQuestionsWhichPriorityGoods(declarationDetails, importQuestions) =>
          goto(
            AnswerImportQuestionsALVS(
              declarationDetails,
              importQuestions.copy(priorityGoods = Some(importPriorityGoods))
            )
          )
      }

    def submittedImportQuestionsAnswerHasALVS(user: String)(importHasALVS: Boolean) =
      Transition {
        case AnswerImportQuestionsALVS(declarationDetails, importQuestions) =>
          goto(
            AnswerImportQuestionsFreightType(declarationDetails, importQuestions.copy(hasALVS = Some(importHasALVS)))
          )
      }

    def submittedImportQuestionsAnswerFreightType(user: String)(importFreightType: ImportFreightType) =
      Transition {
        case AnswerImportQuestionsFreightType(declarationDetails, importQuestions) =>
          val updatedImportQuestions = importQuestions.copy(freightType = Some(importFreightType))
          if (Rules.isVesselDetailsAnswerMandatory(updatedImportQuestions))
            goto(AnswerImportQuestionsMandatoryVesselInfo(declarationDetails, updatedImportQuestions))
          else
            goto(AnswerImportQuestionsOptionalVesselInfo(declarationDetails, updatedImportQuestions))
      }

    def submittedImportQuestionsMandatoryVesselDetails(user: String)(vesselDetails: VesselDetails) =
      Transition {
        case AnswerImportQuestionsMandatoryVesselInfo(declarationDetails, importQuestions)
            if vesselDetails.isComplete =>
          goto(
            AnswerImportQuestionsContactInfo(
              declarationDetails,
              importQuestions.copy(vesselDetails = if (vesselDetails.isEmpty) None else Some(vesselDetails))
            )
          )
      }

    def submittedImportQuestionsOptionalVesselDetails(user: String)(vesselDetails: VesselDetails) =
      Transition {
        case AnswerImportQuestionsOptionalVesselInfo(declarationDetails, importQuestions) =>
          goto(
            AnswerImportQuestionsContactInfo(
              declarationDetails,
              importQuestions.copy(vesselDetails = if (vesselDetails.isEmpty) None else Some(vesselDetails))
            )
          )
      }

    def submittedImportQuestionsContactInfo(user: String)(contactInfo: ImportContactInfo) =
      Transition {
        case AnswerImportQuestionsContactInfo(declarationDetails, importQuestions) =>
          goto(
            ImportQuestionsSummary(
              declarationDetails,
              importQuestions.copy(contactInfo = Some(contactInfo))
            )
          )
      }

    type UpscanInitiate = UpscanInitiateRequest => Future[UpscanInitiateResponse]

    def initiateFileUpload(
      callbackUrl: String,
      successRedirect: String,
      errorRedirect: String
    )(upscanInitiate: UpscanInitiate)(user: String)(implicit ec: ExecutionContext) =
      Transition {
        case ExportQuestionsSummary(declarationDetails, exportQuestionsAnswers) =>
          for {
            upscanResponse <- upscanInitiate(
                                UpscanInitiateRequest(
                                  callbackUrl = callbackUrl,
                                  successRedirect = Some(successRedirect),
                                  errorRedirect = Some(errorRedirect)
                                )
                              )
          } yield UploadFile(
            declarationDetails,
            exportQuestionsAnswers,
            upscanResponse.reference,
            upscanResponse.uploadRequest,
            FileUploads(files = Seq(FileUpload.Initiated(1, upscanResponse.reference)))
          )

        case ImportQuestionsSummary(declarationDetails, importQuestionsAnswers) =>
          for {
            upscanResponse <- upscanInitiate(
                                UpscanInitiateRequest(
                                  callbackUrl = callbackUrl,
                                  successRedirect = Some(successRedirect),
                                  errorRedirect = Some(errorRedirect)
                                )
                              )
          } yield UploadFile(
            declarationDetails,
            importQuestionsAnswers,
            upscanResponse.reference,
            upscanResponse.uploadRequest,
            FileUploads(files = Seq(FileUpload.Initiated(1, upscanResponse.reference)))
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

        case FileUploaded(declarationDetails, questionsAnswers, acceptedFile, fileUploads, _) =>
          for {
            upscanResponse <- upscanInitiate(
                                UpscanInitiateRequest(
                                  callbackUrl = callbackUrl,
                                  successRedirect = Some(successRedirect),
                                  errorRedirect = Some(errorRedirect)
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

    def waitForFileVerification(user: String) =
      Transition {
        case current @ UploadFile(declarationDetails, questionsAnswers, reference, uploadRequest, fileUploads) =>
          val updatedFileUploads = fileUploads.copy(files = fileUploads.files.map {
            case FileUpload.Initiated(orderNumber, ref) if ref == reference => FileUpload.Posted(orderNumber, reference)
            case u                                                          => u
          })
          updatedFileUploads.files.find(_.reference == reference) match {
            case Some(acceptedFile: FileUpload.Accepted) =>
              goto(FileUploaded(declarationDetails, questionsAnswers, acceptedFile, updatedFileUploads))

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
              goto(FileUploaded(declarationDetails, questionsAnswers, acceptedFile, fileUploads))

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
                FileUpload.Rejected(orderNumber, ref, failureDetails.failureReason, failureDetails.message)
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
              goto(FileUploaded(declarationDetails, questionsAnswers, acceptedFile, updatedFileUploads))

            case _ =>
              goto(UploadFile(declarationDetails, questionsAnswers, reference, uploadRequest, updatedFileUploads))

          }

        case UploadFile(declarationDetails, questionsAnswers, reference, uploadRequest, fileUploads) =>
          val updatedFileUploads = updateFileUploads(fileUploads)
          updatedFileUploads.files.find(_.reference == reference) match {
            case Some(acceptedFile: FileUpload.Accepted) =>
              goto(FileUploaded(declarationDetails, questionsAnswers, acceptedFile, updatedFileUploads))

            case _ =>
              goto(UploadFile(declarationDetails, questionsAnswers, reference, uploadRequest, updatedFileUploads))

          }
      }
    }
  }

  object Setters {
    import State._

    /** Typeclass of exportQuestions setters */
    trait SetExportQuestions[S <: State] {
      def set(state: S, exportQuestions: ExportQuestions): S
    }

    /** Typeclass of importQuestions setters */
    trait SetImportQuestions[S <: State] {
      def set(state: S, importQuestions: ImportQuestions): S
    }

    object SetExportQuestions {
      private def of[S <: State](fx: (S, ExportQuestions) => S): SetExportQuestions[S] =
        new SetExportQuestions[S] {
          override def set(state: S, exportQuestions: ExportQuestions): S = fx(state, exportQuestions)
        }

      implicit val s1 = of[AnswerExportQuestionsRequestType]((s, e) => s.copy(exportQuestionsAnswers = e))
      implicit val s2 = of[AnswerExportQuestionsRouteType]((s, e) => s.copy(exportQuestionsAnswers = e))
      implicit val s3 = of[AnswerExportQuestionsHasPriorityGoods]((s, e) => s.copy(exportQuestionsAnswers = e))
      implicit val s4 = of[AnswerExportQuestionsWhichPriorityGoods]((s, e) => s.copy(exportQuestionsAnswers = e))
      implicit val s5 = of[AnswerExportQuestionsFreightType]((s, e) => s.copy(exportQuestionsAnswers = e))
      implicit val s6 = of[AnswerExportQuestionsMandatoryVesselInfo]((s, e) => s.copy(exportQuestionsAnswers = e))
      implicit val s7 = of[AnswerExportQuestionsOptionalVesselInfo]((s, e) => s.copy(exportQuestionsAnswers = e))
      implicit val s8 = of[AnswerExportQuestionsContactInfo]((s, e) => s.copy(exportQuestionsAnswers = e))
    }

    object SetImportQuestions {
      private def of[S <: State](fx: (S, ImportQuestions) => S): SetImportQuestions[S] =
        new SetImportQuestions[S] {
          override def set(state: S, importQuestions: ImportQuestions): S = fx(state, importQuestions)
        }

      implicit val s1 = of[AnswerImportQuestionsRequestType]((s, e) => s.copy(importQuestionsAnswers = e))
      implicit val s2 = of[AnswerImportQuestionsRouteType]((s, e) => s.copy(importQuestionsAnswers = e))
      implicit val s3 = of[AnswerImportQuestionsHasPriorityGoods]((s, e) => s.copy(importQuestionsAnswers = e))
      implicit val s4 = of[AnswerImportQuestionsWhichPriorityGoods]((s, e) => s.copy(importQuestionsAnswers = e))
      implicit val s5 = of[AnswerImportQuestionsFreightType]((s, e) => s.copy(importQuestionsAnswers = e))
      implicit val s6 = of[AnswerImportQuestionsALVS]((s, e) => s.copy(importQuestionsAnswers = e))
      implicit val s7 = of[AnswerImportQuestionsOptionalVesselInfo]((s, e) => s.copy(importQuestionsAnswers = e))
      implicit val s8 = of[AnswerImportQuestionsContactInfo]((s, e) => s.copy(importQuestionsAnswers = e))
      implicit val s9 = of[AnswerImportQuestionsMandatoryVesselInfo]((s, e) => s.copy(importQuestionsAnswers = e))
    }
  }
}
