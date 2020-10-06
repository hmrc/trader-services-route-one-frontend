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

package uk.gov.hmrc.traderservices.journey

import java.time.LocalDate

import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.traderservices.journeys.TraderServicesFrontendJourneyModel.State._
import uk.gov.hmrc.traderservices.journeys.TraderServicesFrontendJourneyModel.Transitions._
import uk.gov.hmrc.traderservices.journeys.TraderServicesFrontendJourneyModel.{State, Transition, TransitionNotAllowed}
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.services.TraderServicesFrontendJourneyService
import uk.gov.hmrc.traderservices.support.{InMemoryStore, StateMatchers}

import scala.concurrent.ExecutionContext.Implicits.global

class TraderServicesFrontendModelSpec extends UnitSpec with StateMatchers[State] with TestData {

  // dummy journey context
  case class DummyContext()
  implicit val dummyContext: DummyContext = DummyContext()

  "TraderServicesFrontendModel" when {
    "at state Start" should {
      "stay at Start when start" in {
        given(Start) when start(eoriNumber) should thenGo(Start)
      }

      "go to EnterDeclarationDetails when enterDeclarationDetails" in {
        given(Start) when enterDeclarationDetails(eoriNumber) should thenGo(EnterDeclarationDetails(None))
      }

      "raise exception if any other transition requested" in {
        an[TransitionNotAllowed] shouldBe thrownBy {
          given(Start) when submittedDeclarationDetails(eoriNumber)(
            exportDeclarationDetails
          )
        }
      }
    }

    "at state EnterDeclarationDetails" should {

      "goto AnswerExportQuestionsRequestType when submittedDeclarationDetails for export" in {
        given(EnterDeclarationDetails(None)) when submittedDeclarationDetails(eoriNumber)(
          exportDeclarationDetails
        ) should thenGo(AnswerExportQuestionsRequestType(exportDeclarationDetails, ExportQuestions()))
      }

      "goto AnswerImportQuestionsRequestType when submittedDeclarationDetails for import" in {
        given(EnterDeclarationDetails(None)) when submittedDeclarationDetails(eoriNumber)(
          importDeclarationDetails
        ) should thenGo(AnswerImportQuestionsRequestType(importDeclarationDetails, ImportQuestions()))
      }
    }

    "at state AnswerExportQuestionsRequestType" should {
      for (requestType <- ExportRequestType.values.filterNot(_ == ExportRequestType.Hold))
        s"go to AnswerExportQuestionsRouteType when submitted requestType of ${ExportRequestType.keyOf(requestType).get}" in {
          given(
            AnswerExportQuestionsRequestType(exportDeclarationDetails, ExportQuestions())
          ) when submittedExportQuestionsAnswerRequestType(eoriNumber)(
            requestType
          ) should thenGo(
            AnswerExportQuestionsRouteType(
              exportDeclarationDetails,
              ExportQuestions(requestType = Some(requestType))
            )
          )
        }

      "go to AnswerExportQuestionsGoodsPriority when submitted requestType of Hold" in {
        given(
          AnswerExportQuestionsRequestType(exportDeclarationDetails, ExportQuestions())
        ) when submittedExportQuestionsAnswerRequestType(eoriNumber)(
          ExportRequestType.Hold
        ) should thenGo(
          AnswerExportQuestionsHasPriorityGoods(
            exportDeclarationDetails,
            ExportQuestions(requestType = Some(ExportRequestType.Hold))
          )
        )
      }
    }

    "at state AnswerExportQuestionsRouteType" should {
      for (routeType <- ExportRouteType.values)
        s"go to AnswerExportQuestionsHasPriorityGoods when submitted routeType of ${ExportRouteType.keyOf(routeType).get}" in {
          given(
            AnswerExportQuestionsRouteType(
              exportDeclarationDetails,
              ExportQuestions(requestType = Some(ExportRequestType.New))
            )
          ) when submittedExportQuestionsAnswerRouteType(eoriNumber)(
            routeType
          ) should thenGo(
            AnswerExportQuestionsHasPriorityGoods(
              exportDeclarationDetails,
              ExportQuestions(requestType = Some(ExportRequestType.New), routeType = Some(routeType))
            )
          )
        }
    }

    "at state AnswerExportQuestionsHasPriorityGoods" should {
      "go to AnswerExportQuestionsWhichPriorityGoods when selected YES" in {
        given(
          AnswerExportQuestionsHasPriorityGoods(
            exportDeclarationDetails,
            ExportQuestions(requestType = Some(ExportRequestType.New), routeType = Some(ExportRouteType.Route1))
          )
        ) when submittedExportQuestionsAnswerHasPriorityGoods(eoriNumber)(true) should thenGo(
          AnswerExportQuestionsWhichPriorityGoods(
            exportDeclarationDetails,
            ExportQuestions(requestType = Some(ExportRequestType.New), routeType = Some(ExportRouteType.Route1))
          )
        )
      }
      "go to AnswerExportQuestionsWhichPriorityGoods when selected NO" in {
        given(
          AnswerExportQuestionsHasPriorityGoods(
            exportDeclarationDetails,
            ExportQuestions(requestType = Some(ExportRequestType.New), routeType = Some(ExportRouteType.Route1))
          )
        ) when submittedExportQuestionsAnswerHasPriorityGoods(eoriNumber)(false) should thenGo(
          AnswerExportQuestionsFreightType(
            exportDeclarationDetails,
            ExportQuestions(requestType = Some(ExportRequestType.New), routeType = Some(ExportRouteType.Route1))
          )
        )
      }
    }
  }

  case class given(initialState: State)
      extends TraderServicesFrontendJourneyService[DummyContext]
      with InMemoryStore[(State, List[State]), DummyContext] {

    await(save((initialState, Nil)))

    def withBreadcrumbs(breadcrumbs: State*): this.type = {
      val (state, _) = await(fetch).getOrElse((Start, Nil))
      await(save((state, breadcrumbs.toList)))
      this
    }

    def when(transition: Transition): (State, List[State]) =
      await(super.apply(transition))
  }
}

trait TestData {

  val eoriNumber = "foo"
  val correlationId = "123"

  val exportDeclarationDetails = DeclarationDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-09-23"))
  val importDeclarationDetails = DeclarationDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-09-23"))
  val invalidDeclarationDetails = DeclarationDetails(EPU(123), EntryNumber("0000000"), LocalDate.parse("2020-09-23"))

}
