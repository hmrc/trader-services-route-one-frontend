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

object TraderServicesFrontendJourneyModel extends JourneyModel {

  sealed trait State
  sealed trait IsError

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

    case class AnswerImportQuestionsContactInfo(
      declarationDetails: DeclarationDetails,
      importQuestionsAnswers: ImportQuestions
    ) extends ImportQuestionsState

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
          if (exportRequestType == ExportRequestType.Hold)
            goto(
              AnswerExportQuestionsHasPriorityGoods(
                declarationDetails,
                exportQuestions.copy(requestType = Some(exportRequestType))
              )
            )
          else
            goto(
              AnswerExportQuestionsRouteType(
                declarationDetails,
                exportQuestions.copy(requestType = Some(exportRequestType))
              )
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
          if (exportQuestions.requestType.contains(ExportRequestType.C1601))
            goto(
              AnswerExportQuestionsMandatoryVesselInfo(
                declarationDetails,
                exportQuestions.copy(freightType = Some(exportFreightType))
              )
            )
          else
            goto(
              AnswerExportQuestionsOptionalVesselInfo(
                declarationDetails,
                exportQuestions.copy(freightType = Some(exportFreightType))
              )
            )
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

    def submittedImportQuestionsAnswersRequestType(user: String)(importRequestType: ImportRequestType) =
      Transition {
        case AnswerImportQuestionsRequestType(declarationDetails, importQuestions) =>
          if (importRequestType == ImportRequestType.Hold)
            goto(
              AnswerImportQuestionsHasPriorityGoods(
                declarationDetails,
                importQuestions.copy(requestType = Some(importRequestType))
              )
            )
          else
            goto(
              AnswerImportQuestionsRouteType(
                declarationDetails,
                importQuestions.copy(requestType = Some(importRequestType))
              )
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
          goto(
            AnswerImportQuestionsOptionalVesselInfo(
              declarationDetails,
              importQuestions.copy(freightType = Some(importFreightType))
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
        case _ =>
          goto(
            WorkInProgressDeadEnd
          )
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
    }
  }
}
