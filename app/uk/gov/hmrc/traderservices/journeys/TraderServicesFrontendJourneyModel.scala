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

    case class EnterDeclarationDetails(declarationDetailsOpt: Option[DeclarationDetails]) extends State

    trait HasDeclarationDetails {
      def declarationDetails: DeclarationDetails
    }

    case object WorkInProgressDeadEnd extends State

    // EXPORT QUESTIONS

    case class AnswerExportQuestionsRequestType(
      declarationDetails: DeclarationDetails,
      exportQuestionsAnswers: ExportQuestions
    ) extends State with HasDeclarationDetails

    case class AnswerExportQuestionsRouteType(
      declarationDetails: DeclarationDetails,
      exportQuestionsAnswers: ExportQuestions
    ) extends State with HasDeclarationDetails

    case class AnswerExportQuestionsHasPriorityGoods(
      declarationDetails: DeclarationDetails,
      exportQuestionsAnswers: ExportQuestions
    ) extends State with HasDeclarationDetails

    case class AnswerExportQuestionsWhichPriorityGoods(
      declarationDetails: DeclarationDetails,
      exportQuestionsAnswers: ExportQuestions
    ) extends State with HasDeclarationDetails

    case class AnswerExportQuestionsFreightType(
      declarationDetails: DeclarationDetails,
      exportQuestionsAnswers: ExportQuestions
    ) extends State with HasDeclarationDetails

    case class AnswerExportQuestionsMandatoryVesselInfo(
      declarationDetails: DeclarationDetails,
      exportQuestionsAnswers: ExportQuestions
    ) extends State with HasDeclarationDetails

    case class AnswerExportQuestionsOptionalVesselInfo(
      declarationDetails: DeclarationDetails,
      exportQuestionsAnswers: ExportQuestions
    ) extends State with HasDeclarationDetails

    case class AnswerExportQuestionsContactInfo(
      declarationDetails: DeclarationDetails,
      exportQuestionsAnswers: ExportQuestions
    ) extends State with HasDeclarationDetails

    // IMPORT QUESTIONS

    case class AnswerImportQuestionsRequestType(
      declarationDetails: DeclarationDetails,
      importQuestionsOpt: ImportQuestions
    ) extends State with HasDeclarationDetails

    case class AnswerImportQuestionsRouteType(
      declarationDetails: DeclarationDetails,
      importQuestionsOpt: ImportQuestions
    ) extends State with HasDeclarationDetails

    case class AnswerImportQuestionsHasPriorityGoods(
      declarationDetails: DeclarationDetails,
      importQuestionsOpt: ImportQuestions
    ) extends State with HasDeclarationDetails

    case class AnswerImportQuestionsWhichPriorityGoods(
      declarationDetails: DeclarationDetails,
      importQuestionsOpt: ImportQuestions
    ) extends State with HasDeclarationDetails

    case class AnswerImportQuestionsALVS(
      declarationDetails: DeclarationDetails,
      importQuestionsOpt: ImportQuestions
    ) extends State with HasDeclarationDetails

    case class AnswerImportQuestionsFreightType(
      declarationDetails: DeclarationDetails,
      importQuestionsOpt: ImportQuestions
    ) extends State with HasDeclarationDetails

    case class AnswerImportQuestionsMandatoryVesselInfo(
      declarationDetails: DeclarationDetails,
      importQuestionsAnswers: ImportQuestions
    ) extends State with HasDeclarationDetails

    case class AnswerImportQuestionsOptionalVesselInfo(
      declarationDetails: DeclarationDetails,
      importQuestionsAnswers: ImportQuestions
    ) extends State with HasDeclarationDetails

    case class AnswerImportQuestionsContactInfo(
      declarationDetails: DeclarationDetails,
      importQuestionsAnswers: ImportQuestions
    ) extends State with HasDeclarationDetails

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
        case EnterDeclarationDetails(_) =>
          if (declarationDetails.isExportDeclaration)
            goto(AnswerExportQuestionsRequestType(declarationDetails, ExportQuestions()))
          else goto(AnswerImportQuestionsRequestType(declarationDetails, ImportQuestions()))
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
            goto(AnswerExportQuestionsWhichPriorityGoods(declarationDetails, exportQuestions))
          else
            goto(AnswerExportQuestionsFreightType(declarationDetails, exportQuestions))
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
            goto(AnswerImportQuestionsWhichPriorityGoods(declarationDetails, importQuestions))
          else
            goto(AnswerImportQuestionsALVS(declarationDetails, importQuestions))
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
          if (importFreightType == ImportFreightType.Maritime)
            goto(
              AnswerImportQuestionsMandatoryVesselInfo(
                declarationDetails,
                importQuestions.copy(freightType = Some(importFreightType))
              )
            )
          else
            goto(
              AnswerImportQuestionsOptionalVesselInfo(
                declarationDetails,
                importQuestions.copy(freightType = Some(importFreightType))
              )
            )
      }
  }

}
