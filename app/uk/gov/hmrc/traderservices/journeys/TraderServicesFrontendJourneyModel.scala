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

    case class AnswerExportQuestionsRequestType(
      declarationDetails: DeclarationDetails,
      exportQuestionsAnswers: ExportQuestions
    ) extends State

    case class AnswerExportQuestionsRouteType(
      declarationDetails: DeclarationDetails,
      exportQuestionsAnswers: ExportQuestions
    ) extends State

    case class AnswerExportQuestionsGoodsPriority(
      declarationDetails: DeclarationDetails,
      exportQuestionsAnswers: ExportQuestions
    ) extends State

    case class AnswerExportQuestionsFreightType(
      declarationDetails: DeclarationDetails,
      exportQuestionsAnswers: ExportQuestions
    ) extends State

    case class AnswerImportQuestionsRequestType(
      declarationDetails: DeclarationDetails,
      importQuestionsOpt: ImportQuestions
    ) extends State

    case class AnswerImportQuestionsRouteType(
      declarationDetails: DeclarationDetails,
      importQuestionsOpt: ImportQuestions
    ) extends State

    case class AnswerImportQuestionsGoodsPriority(
      declarationDetails: DeclarationDetails,
      importQuestionsOpt: ImportQuestions
    ) extends State

    case class AnswerImportQuestionsFreightType(
      declarationDetails: DeclarationDetails,
      importQuestionsOpt: ImportQuestions
    ) extends State

    case object WorkInProgressDeadEnd extends State

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

    def submittedExportQuestionsAnswersRequestType(user: String)(exportRequestType: ExportRequestType) =
      Transition {
        case AnswerExportQuestionsRequestType(declarationDetails, exportQuestions) =>
          goto(
            AnswerExportQuestionsRouteType(
              declarationDetails,
              exportQuestions.copy(requestType = Some(exportRequestType))
            )
          )
      }

    def submittedImportQuestionsAnswersRequestType(user: String)(importRequestType: ImportRequestType) =
      Transition {
        case AnswerImportQuestionsRequestType(declarationDetails, importQuestions) =>
          goto(
            AnswerImportQuestionsRouteType(
              declarationDetails,
              importQuestions.copy(requestType = Some(importRequestType))
            )
          )
      }
  }

}
