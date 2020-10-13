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
import java.time.LocalTime

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

    "at state AnswerExportQuestionsWhichPriorityGoods" should {
      "go to AnswerExportQuestionsFreightType when submittedExportQuestionsAnswerWhichPriorityGoods" in {
        given(
          AnswerExportQuestionsWhichPriorityGoods(
            exportDeclarationDetails,
            ExportQuestions(requestType = Some(ExportRequestType.C1601), routeType = Some(ExportRouteType.Route3))
          )
        ) when submittedExportQuestionsAnswerWhichPriorityGoods(eoriNumber)(
          ExportPriorityGoods.ExplosivesOrFireworks
        ) should thenGo(
          AnswerExportQuestionsFreightType(
            exportDeclarationDetails,
            ExportQuestions(
              requestType = Some(ExportRequestType.C1601),
              routeType = Some(ExportRouteType.Route3),
              priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks)
            )
          )
        )
      }
    }

    "at state AnswerExportQuestionsFreightType" should {
      for (
        freightType <- ExportFreightType.values;
        requestType <- ExportRequestType.values.filterNot(_ == ExportRequestType.C1601)
      )
        s"go to AnswerExportQuestionsOptionalVesselInfo when submittedExportQuestionsAnswerFreightType and requestType=${ExportRequestType
          .keyOf(requestType)
          .get}, and freightType=${ExportFreightType.keyOf(freightType).get}" in {
          given(
            AnswerExportQuestionsFreightType(
              exportDeclarationDetails,
              ExportQuestions(
                requestType = Some(requestType),
                routeType = Some(ExportRouteType.Route3),
                priorityGoods = Some(ExportPriorityGoods.ClassADrugs)
              )
            )
          ) when submittedExportQuestionsAnswerFreightType(eoriNumber)(
            freightType
          ) should thenGo(
            AnswerExportQuestionsOptionalVesselInfo(
              exportDeclarationDetails,
              ExportQuestions(
                requestType = Some(requestType),
                routeType = Some(ExportRouteType.Route3),
                priorityGoods = Some(ExportPriorityGoods.ClassADrugs),
                freightType = Some(freightType)
              )
            )
          )
        }

      for (freightType <- ExportFreightType.values)
        s"go to AnswerExportQuestionsMandatoryVesselInfo when submittedExportQuestionsAnswerFreightType and requestType==C1601, and freightType=${ExportFreightType.keyOf(freightType).get}" in {
          given(
            AnswerExportQuestionsFreightType(
              exportDeclarationDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.C1601),
                routeType = Some(ExportRouteType.Route3),
                priorityGoods = Some(ExportPriorityGoods.ClassADrugs)
              )
            )
          ) when submittedExportQuestionsAnswerFreightType(eoriNumber)(
            freightType
          ) should thenGo(
            AnswerExportQuestionsMandatoryVesselInfo(
              exportDeclarationDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.C1601),
                routeType = Some(ExportRouteType.Route3),
                priorityGoods = Some(ExportPriorityGoods.ClassADrugs),
                freightType = Some(freightType)
              )
            )
          )
        }
    }

    "at state AnswerExportQuestionsMandatoryVesselInfo" should {
      "go to AnswerExportQuestionsContactInfo when submittedExportQuestionsMandatoryVesselDetails with complete vessel details" in {
        given(
          AnswerExportQuestionsMandatoryVesselInfo(
            exportDeclarationDetails,
            ExportQuestions(
              requestType = Some(ExportRequestType.C1601),
              routeType = Some(ExportRouteType.Route3),
              priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
              freightType = Some(ExportFreightType.Air)
            )
          )
        ) when submittedExportQuestionsMandatoryVesselDetails(eoriNumber)(
          VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))
        ) should thenGo(
          AnswerExportQuestionsContactInfo(
            exportDeclarationDetails,
            ExportQuestions(
              requestType = Some(ExportRequestType.C1601),
              routeType = Some(ExportRouteType.Route3),
              priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
              freightType = Some(ExportFreightType.Air),
              vesselDetails =
                Some(VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00"))))
            )
          )
        )
      }

      "stay when submittedExportQuestionsMandatoryVesselDetails with incomplete vessel details" in {
        an[TransitionNotAllowed] shouldBe thrownBy {
          given(
            AnswerExportQuestionsMandatoryVesselInfo(
              exportDeclarationDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.C1601),
                routeType = Some(ExportRouteType.Route3),
                priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ExportFreightType.Air)
              )
            )
          ) when submittedExportQuestionsMandatoryVesselDetails(eoriNumber)(
            VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), None)
          )
        }
      }
    }

    "at state AnswerExportQuestionsOptionalVesselInfo" should {
      "go to AnswerExportQuestionsContactInfo when submittedExportQuestionsOptionalVesselDetails with some vessel details" in {
        given(
          AnswerExportQuestionsOptionalVesselInfo(
            exportDeclarationDetails,
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route3),
              priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
              freightType = Some(ExportFreightType.Air)
            )
          )
        ) when submittedExportQuestionsOptionalVesselDetails(eoriNumber)(
          VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))
        ) should thenGo(
          AnswerExportQuestionsContactInfo(
            exportDeclarationDetails,
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route3),
              priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
              freightType = Some(ExportFreightType.Air),
              vesselDetails =
                Some(VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00"))))
            )
          )
        )
      }

      "go to AnswerExportQuestionsContactInfo when submittedExportQuestionsOptionalVesselDetails without vessel details" in {
        given(
          AnswerExportQuestionsOptionalVesselInfo(
            exportDeclarationDetails,
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route3),
              priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
              freightType = Some(ExportFreightType.Air)
            )
          )
        ) when submittedExportQuestionsOptionalVesselDetails(eoriNumber)(
          VesselDetails()
        ) should thenGo(
          AnswerExportQuestionsContactInfo(
            exportDeclarationDetails,
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route3),
              priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
              freightType = Some(ExportFreightType.Air),
              vesselDetails = None
            )
          )
        )
      }
    }

    "at state AnswerImportQuestionsRequestType" should {
      for (requestType <- ImportRequestType.values.filterNot(_ == ImportRequestType.Hold))
        s"go to AnswerImportQuestionsRequestType when submitted requestType of ${ImportRequestType.keyOf(requestType).get}" in {
          given(
            AnswerImportQuestionsRequestType(importDeclarationDetails, ImportQuestions())
          ) when submittedImportQuestionsAnswersRequestType(eoriNumber)(
            requestType
          ) should thenGo(
            AnswerImportQuestionsRouteType(
              importDeclarationDetails,
              ImportQuestions(requestType = Some(requestType))
            )
          )
        }

      "go to AnswerImportQuestionsHasPriorityGoods when submitted requestType of Hold" in {
        given(
          AnswerImportQuestionsRequestType(importDeclarationDetails, ImportQuestions())
        ) when submittedImportQuestionsAnswersRequestType(eoriNumber)(
          ImportRequestType.Hold
        ) should thenGo(
          AnswerImportQuestionsHasPriorityGoods(
            importDeclarationDetails,
            ImportQuestions(requestType = Some(ImportRequestType.Hold))
          )
        )
      }
    }

    "at state AnswerImportQuestionsRouteType" should {
      for (routeType <- ImportRouteType.values)
        s"go to AnswerImportQuestionsHasPriorityGoods when submitted routeType of ${ImportRouteType.keyOf(routeType).get}" in {
          given(
            AnswerImportQuestionsRouteType(
              importDeclarationDetails,
              ImportQuestions(requestType = Some(ImportRequestType.New))
            )
          ) when submittedImportQuestionsAnswerRouteType(eoriNumber)(
            routeType
          ) should thenGo(
            AnswerImportQuestionsHasPriorityGoods(
              importDeclarationDetails,
              ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(routeType))
            )
          )
        }
    }

    "at state AnswerImportQuestionsHasPriorityGoods" should {
      "go to AnswerImportQuestionsWhichPriorityGoods when selected YES" in {
        given(
          AnswerImportQuestionsHasPriorityGoods(
            importDeclarationDetails,
            ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route1))
          )
        ) when submittedImportQuestionsAnswerHasPriorityGoods(eoriNumber)(true) should thenGo(
          AnswerImportQuestionsWhichPriorityGoods(
            importDeclarationDetails,
            ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route1))
          )
        )
      }
      "go to AnswerImportQuestionsWhichPriorityGoods when selected NO" in {
        given(
          AnswerImportQuestionsHasPriorityGoods(
            importDeclarationDetails,
            ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route1))
          )
        ) when submittedImportQuestionsAnswerHasPriorityGoods(eoriNumber)(false) should thenGo(
          AnswerImportQuestionsALVS(
            importDeclarationDetails,
            ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route1))
          )
        )
      }
    }

    "at state AnswerImportQuestionsWhichPriorityGoods" should {
      for (priorityGoods <- ImportPriorityGoods.values)
        s"go to AnswerImportQuestionsALVS when submittedImportQuestionsAnswerWhichPriorityGoods with ${ImportPriorityGoods.keyOf(priorityGoods).get}" in {
          given(
            AnswerImportQuestionsWhichPriorityGoods(
              importDeclarationDetails,
              ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route3))
            )
          ) when submittedImportQuestionsAnswerWhichPriorityGoods(eoriNumber)(
            priorityGoods
          ) should thenGo(
            AnswerImportQuestionsALVS(
              importDeclarationDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route3),
                priorityGoods = Some(priorityGoods)
              )
            )
          )
        }
    }

    "at state AnswerImportQuestionsALVS" should {
      "go to AnswerImportQuestionsFreightType when selected YES" in {
        given(
          AnswerImportQuestionsALVS(
            importDeclarationDetails,
            ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route1))
          )
        ) when submittedImportQuestionsAnswerHasALVS(eoriNumber)(true) should thenGo(
          AnswerImportQuestionsFreightType(
            importDeclarationDetails,
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route1),
              hasALVS = Some(true)
            )
          )
        )
      }

      "go to AnswerImportQuestionsFreightType when selected NO" in {
        given(
          AnswerImportQuestionsALVS(
            importDeclarationDetails,
            ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route1))
          )
        ) when submittedImportQuestionsAnswerHasALVS(eoriNumber)(false) should thenGo(
          AnswerImportQuestionsFreightType(
            importDeclarationDetails,
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route1),
              hasALVS = Some(false)
            )
          )
        )
      }
    }

    "at state AnswerImportQuestionsFreightType" should {
      for (
        freightType <- ImportFreightType.values.filterNot(_ == ImportFreightType.Maritime);
        requestType <- ImportRequestType.values
      )
        s"go to AnswerImportQuestionsOptionalVesselInfo when submittedImportQuestionsAnswerFreightType and requestType=${ImportRequestType
          .keyOf(requestType)
          .get}, and freightType=${ImportFreightType.keyOf(freightType).get}" in {
          given(
            AnswerImportQuestionsFreightType(
              importDeclarationDetails,
              ImportQuestions(
                requestType = Some(requestType),
                routeType = Some(ImportRouteType.Route3),
                hasALVS = Some(false)
              )
            )
          ) when submittedImportQuestionsAnswerFreightType(eoriNumber)(
            freightType
          ) should thenGo(
            AnswerImportQuestionsOptionalVesselInfo(
              importDeclarationDetails,
              ImportQuestions(
                requestType = Some(requestType),
                routeType = Some(ImportRouteType.Route3),
                freightType = Some(freightType),
                hasALVS = Some(false)
              )
            )
          )
        }

      for (requestType <- ImportRequestType.values)
        s"go to AnswerImportQuestionsOptionalVesselInfo when submittedImportQuestionsAnswerFreightType and requestType=${ImportRequestType
          .keyOf(requestType)
          .get}, and freightType=Maritime" in {
          given(
            AnswerImportQuestionsFreightType(
              importDeclarationDetails,
              ImportQuestions(
                requestType = Some(requestType),
                routeType = Some(ImportRouteType.Route3),
                hasALVS = Some(true)
              )
            )
          ) when submittedImportQuestionsAnswerFreightType(eoriNumber)(
            ImportFreightType.Maritime
          ) should thenGo(
            AnswerImportQuestionsOptionalVesselInfo(
              importDeclarationDetails,
              ImportQuestions(
                requestType = Some(requestType),
                routeType = Some(ImportRouteType.Route3),
                freightType = Some(ImportFreightType.Maritime),
                hasALVS = Some(true)
              )
            )
          )
        }
    }

    "at state AnswerImportQuestionsOptionalVesselInfo" should {
      "go to AnswerImportQuestionsContactInfo when submittedImportQuestionsOptionalVesselDetails with some vessel details" in {
        given(
          AnswerImportQuestionsOptionalVesselInfo(
            importDeclarationDetails,
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route3),
              priorityGoods = Some(ImportPriorityGoods.ExplosivesOrFireworks),
              freightType = Some(ImportFreightType.Air)
            )
          )
        ) when submittedImportQuestionsOptionalVesselDetails(eoriNumber)(
          VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))
        ) should thenGo(
          AnswerImportQuestionsContactInfo(
            importDeclarationDetails,
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route3),
              priorityGoods = Some(ImportPriorityGoods.ExplosivesOrFireworks),
              freightType = Some(ImportFreightType.Air),
              vesselDetails =
                Some(VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00"))))
            )
          )
        )
      }

      "go to AnswerImportQuestionsContactInfo when submittedImportQuestionsOptionalVesselDetails without vessel details" in {
        given(
          AnswerImportQuestionsOptionalVesselInfo(
            importDeclarationDetails,
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route3),
              priorityGoods = Some(ImportPriorityGoods.ExplosivesOrFireworks),
              freightType = Some(ImportFreightType.Air)
            )
          )
        ) when submittedImportQuestionsOptionalVesselDetails(eoriNumber)(
          VesselDetails()
        ) should thenGo(
          AnswerImportQuestionsContactInfo(
            importDeclarationDetails,
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route3),
              priorityGoods = Some(ImportPriorityGoods.ExplosivesOrFireworks),
              freightType = Some(ImportFreightType.Air),
              vesselDetails = None
            )
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
