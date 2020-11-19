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
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.State._
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.FileUploadState._
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.Transitions._
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.FileUploadTransitions._
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.Rules._
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.{Merger, State, Transition, TransitionNotAllowed, UpscanInitiateApi}
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.services.CreateCaseJourneyService
import uk.gov.hmrc.traderservices.support.{InMemoryStore, StateMatchers}

import scala.concurrent.ExecutionContext.Implicits.global
import java.time.LocalTime
import scala.reflect.ClassTag
import uk.gov.hmrc.traderservices.connectors.UpscanInitiateRequest
import uk.gov.hmrc.traderservices.connectors.UpscanInitiateResponse
import scala.concurrent.Future
import java.time.ZonedDateTime
import _root_.uk.gov.hmrc.traderservices.connectors.TraderServicesCreateCaseResponse
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.FileUploadHostData
import scala.util.Try

class CreateCaseJourneyModelSpec extends UnitSpec with StateMatchers[State] with TestData {

  import scala.concurrent.duration._
  override implicit val defaultTimeout: FiniteDuration = 60 seconds

  // dummy journey context
  case class DummyContext()
  implicit val dummyContext: DummyContext = DummyContext()

  "CreateCaseJourneyModel" when {
    "at state Start" should {
      "stay at Start when start" in {
        given(Start) when start(eoriNumber) should thenGo(Start)
      }

      "go to clean ChooseNewOrExistingCase" in {
        given(Start) when chooseNewOrExistingCase(eoriNumber) should thenGo(ChooseNewOrExistingCase())
      }

      "fail when enterDeclarationDetails" in {
        given(Start) shouldFailWhen backToEnterDeclarationDetails(eoriNumber)
      }

      "fail if any other transition requested" in {
        given(Start) shouldFailWhen submittedDeclarationDetails(eoriNumber)(
          exportDeclarationDetails
        )
      }
    }

    "at state ChooseNewOrExistingCase" should {
      "go to clean EnterDeclarationDetails when selected New and no former answers" in {
        given(ChooseNewOrExistingCase()) when submittedNewOrExistingCaseChoice(eoriNumber)(
          NewOrExistingCase.New
        ) should thenGo(EnterDeclarationDetails())
      }

      "go to EnterDeclarationDetails when selected New and keep former answers" in {
        given(
          ChooseNewOrExistingCase(
            declarationDetailsOpt = Some(importDeclarationDetails),
            importQuestionsAnswersOpt = Some(completeImportQuestionsAnswers),
            fileUploadsOpt = Some(nonEmptyFileUploads)
          )
        ) when submittedNewOrExistingCaseChoice(eoriNumber)(
          NewOrExistingCase.New
        ) should thenGo(
          EnterDeclarationDetails(
            declarationDetailsOpt = Some(importDeclarationDetails),
            importQuestionsAnswersOpt = Some(completeImportQuestionsAnswers),
            fileUploadsOpt = Some(nonEmptyFileUploads)
          )
        )
      }

      "go to TurnToAmendCaseJourney when selected Existing and shouldn't continue" in {
        given(ChooseNewOrExistingCase(continueAmendCaseJourney = false)) when submittedNewOrExistingCaseChoice(
          eoriNumber
        )(
          NewOrExistingCase.Existing
        ) should thenGo(TurnToAmendCaseJourney(continueAmendCaseJourney = false))
      }

      "go to TurnToAmendCaseJourney when selected Existing and must continue" in {
        given(ChooseNewOrExistingCase(continueAmendCaseJourney = true)) when submittedNewOrExistingCaseChoice(
          eoriNumber
        )(
          NewOrExistingCase.Existing
        ) should thenGo(TurnToAmendCaseJourney(continueAmendCaseJourney = true))
      }

      "go back to Start when start" in {
        given(
          ChooseNewOrExistingCase(
            declarationDetailsOpt = Some(importDeclarationDetails),
            importQuestionsAnswersOpt = Some(completeImportQuestionsAnswers)
          )
        ) when start(eoriNumber) should thenGo(Start)
      }
    }

    "at state TurnToAmendCaseJourney" should {
      "go back to ChooseNewOrExistingCase and reset continue flag" in {
        given(TurnToAmendCaseJourney(continueAmendCaseJourney = false)) when chooseNewOrExistingCase(
          eoriNumber
        ) should thenGo(
          ChooseNewOrExistingCase(Some(NewOrExistingCase.Existing))
        )
      }

      "go back to ChooseNewOrExistingCase" in {
        given(TurnToAmendCaseJourney(continueAmendCaseJourney = true)) when chooseNewOrExistingCase(
          eoriNumber
        ) should thenGo(
          ChooseNewOrExistingCase(Some(NewOrExistingCase.Existing))
        )
      }
    }

    "at state EnterDeclarationDetails" should {
      "go to AnswerExportQuestionsRequestType when submitted declaration details for export" in {
        given(EnterDeclarationDetails(None)) when submittedDeclarationDetails(eoriNumber)(
          exportDeclarationDetails
        ) should thenGo(
          AnswerExportQuestionsRequestType(ExportQuestionsStateModel(exportDeclarationDetails, ExportQuestions()))
        )
      }

      "go to ExportQuestionsSummary when submitted declaration details for export and answers are complete" in {
        given(
          EnterDeclarationDetails(
            declarationDetailsOpt = None,
            exportQuestionsAnswersOpt = Some(completeExportQuestionsAnswers)
          )
        ) when submittedDeclarationDetails(eoriNumber)(
          exportDeclarationDetails
        ) should thenGo(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              completeExportQuestionsAnswers
            )
          )
        )
      }

      "go to AnswerImportQuestionsRequestType when submitted declaration details for import" in {
        given(EnterDeclarationDetails(None)) when submittedDeclarationDetails(eoriNumber)(
          importDeclarationDetails
        ) should thenGo(
          AnswerImportQuestionsRequestType(ImportQuestionsStateModel(importDeclarationDetails, ImportQuestions()))
        )
      }

      "go to ImportQuestionsSummary when submitted declaration details for import and answers are complete" in {
        given(
          EnterDeclarationDetails(
            declarationDetailsOpt = None,
            importQuestionsAnswersOpt = Some(completeImportQuestionsAnswers)
          )
        ) when submittedDeclarationDetails(eoriNumber)(
          importDeclarationDetails
        ) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              completeImportQuestionsAnswers
            )
          )
        )
      }

      "go back to ChooseNewOrExistingCase when chooseNewOrExistingCase and keep answers" in {
        given(
          EnterDeclarationDetails(
            declarationDetailsOpt = Some(importDeclarationDetails),
            importQuestionsAnswersOpt = Some(completeImportQuestionsAnswers),
            fileUploadsOpt = Some(nonEmptyFileUploads)
          )
        ) when chooseNewOrExistingCase(eoriNumber) should thenGo(
          ChooseNewOrExistingCase(
            newOrExistingCaseOpt = Some(NewOrExistingCase.New),
            declarationDetailsOpt = Some(importDeclarationDetails),
            importQuestionsAnswersOpt = Some(completeImportQuestionsAnswers),
            fileUploadsOpt = Some(nonEmptyFileUploads),
            continueAmendCaseJourney = false
          )
        )
      }
    }

    "at state AnswerExportQuestionsRequestType" should {
      for (requestType <- ExportRequestType.values) {
        s"go to AnswerExportQuestionsRouteType when submitted request type ${ExportRequestType.keyOf(requestType).get}" in {
          given(
            AnswerExportQuestionsRequestType(ExportQuestionsStateModel(exportDeclarationDetails, ExportQuestions()))
          ) when submittedExportQuestionsAnswerRequestType(eoriNumber)(
            requestType
          ) should thenGo(
            AnswerExportQuestionsRouteType(
              ExportQuestionsStateModel(exportDeclarationDetails, ExportQuestions(requestType = Some(requestType)))
            )
          )
        }

        s"go to ExportQuestionsSummary when submitted request type ${ExportRequestType.keyOf(requestType).get} and answers are complete" in {
          given(
            AnswerExportQuestionsRequestType(
              ExportQuestionsStateModel(exportDeclarationDetails, completeExportQuestionsAnswers)
            )
          ) when submittedExportQuestionsAnswerRequestType(eoriNumber)(
            requestType
          ) should thenGo(
            ExportQuestionsSummary(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                completeExportQuestionsAnswers.copy(requestType = Some(requestType))
              )
            )
          )
        }
      }

    }

    "at state AnswerExportQuestionsRouteType" should {
      for (routeType <- ExportRouteType.values) {
        s"go to AnswerExportQuestionsHasPriorityGoods when submitted route ${ExportRouteType.keyOf(routeType).get}" in {
          given(
            AnswerExportQuestionsRouteType(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                ExportQuestions(requestType = Some(ExportRequestType.New))
              )
            )
          ) when submittedExportQuestionsAnswerRouteType(eoriNumber)(
            routeType
          ) should thenGo(
            AnswerExportQuestionsHasPriorityGoods(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                ExportQuestions(requestType = Some(ExportRequestType.New), routeType = Some(routeType))
              )
            )
          )
        }

        s"go to ExportQuestionsSummary when submitted route ${ExportRouteType.keyOf(routeType).get} and answers are complete" in {
          given(
            AnswerExportQuestionsRouteType(
              ExportQuestionsStateModel(exportDeclarationDetails, completeExportQuestionsAnswers)
            )
          ) when submittedExportQuestionsAnswerRouteType(eoriNumber)(
            routeType
          ) should thenGo(
            ExportQuestionsSummary(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                completeExportQuestionsAnswers.copy(routeType = Some(routeType))
              )
            )
          )
        }
      }

    }

    "at state AnswerExportQuestionsHasPriorityGoods" should {
      "go to AnswerExportQuestionsWhichPriorityGoods when selected YES" in {
        given(
          AnswerExportQuestionsHasPriorityGoods(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              ExportQuestions(requestType = Some(ExportRequestType.New), routeType = Some(ExportRouteType.Route1))
            )
          )
        ) when submittedExportQuestionsAnswerHasPriorityGoods(eoriNumber)(true) should thenGo(
          AnswerExportQuestionsWhichPriorityGoods(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route1),
                hasPriorityGoods = Some(true)
              )
            )
          )
        )
      }
      "go to AnswerExportQuestionsWhichPriorityGoods when selected NO" in {
        given(
          AnswerExportQuestionsHasPriorityGoods(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              ExportQuestions(requestType = Some(ExportRequestType.New), routeType = Some(ExportRouteType.Route1))
            )
          )
        ) when submittedExportQuestionsAnswerHasPriorityGoods(eoriNumber)(false) should thenGo(
          AnswerExportQuestionsFreightType(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route1),
                hasPriorityGoods = Some(false)
              )
            )
          )
        )
      }

      "go to AnswerExportQuestionsWhichPriorityGoods when selected YES and answer was YES before but no priority goods selected" in {
        val answers = completeExportQuestionsAnswers
          .copy(hasPriorityGoods = Some(true), priorityGoods = None)
        given(
          AnswerExportQuestionsHasPriorityGoods(
            ExportQuestionsStateModel(exportDeclarationDetails, answers)
          )
        ) when submittedExportQuestionsAnswerHasPriorityGoods(eoriNumber)(true) should thenGo(
          AnswerExportQuestionsWhichPriorityGoods(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              answers.copy(hasPriorityGoods = Some(true))
            )
          )
        )
      }

      "go to AnswerExportQuestionsWhichPriorityGoods when selected YES and answer was NO before, and other answers are complete" in {
        val answers = completeExportQuestionsAnswers
          .copy(hasPriorityGoods = Some(false), priorityGoods = None)
        given(
          AnswerExportQuestionsHasPriorityGoods(
            ExportQuestionsStateModel(exportDeclarationDetails, answers)
          )
        ) when submittedExportQuestionsAnswerHasPriorityGoods(eoriNumber)(true) should thenGo(
          AnswerExportQuestionsWhichPriorityGoods(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              answers.copy(hasPriorityGoods = Some(true))
            )
          )
        )
      }

      "go to ExportQuestionsSummary when selected YES and answers are complete" in {
        val answers = completeExportQuestionsAnswers
          .copy(hasPriorityGoods = Some(true), priorityGoods = Some(ExportPriorityGoods.HumanRemains))
        given(
          AnswerExportQuestionsHasPriorityGoods(
            ExportQuestionsStateModel(exportDeclarationDetails, answers)
          )
        ) when submittedExportQuestionsAnswerHasPriorityGoods(eoriNumber)(true) should thenGo(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              answers
            )
          )
        )
      }

    }

    "at state AnswerExportQuestionsWhichPriorityGoods" should {
      for (priorityGood <- ExportPriorityGoods.values) {
        s"go to AnswerExportQuestionsFreightType when submitted priority good ${ExportPriorityGoods.keyOf(priorityGood).get}" in {
          given(
            AnswerExportQuestionsWhichPriorityGoods(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                ExportQuestions(requestType = Some(ExportRequestType.C1601), routeType = Some(ExportRouteType.Route3))
              )
            )
          ) when submittedExportQuestionsAnswerWhichPriorityGoods(eoriNumber)(
            priorityGood
          ) should thenGo(
            AnswerExportQuestionsFreightType(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                ExportQuestions(
                  requestType = Some(ExportRequestType.C1601),
                  routeType = Some(ExportRouteType.Route3),
                  priorityGoods = Some(priorityGood)
                )
              )
            )
          )
        }

        s"go to ExportQuestionsSummary when submitted priority good ${ExportPriorityGoods.keyOf(priorityGood).get} and answers are complete" in {
          given(
            AnswerExportQuestionsWhichPriorityGoods(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                completeExportQuestionsAnswers
              )
            )
          ) when submittedExportQuestionsAnswerWhichPriorityGoods(eoriNumber)(
            priorityGood
          ) should thenGo(
            ExportQuestionsSummary(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                completeExportQuestionsAnswers.copy(priorityGoods = Some(priorityGood))
              )
            )
          )
        }
      }

    }

    "at state AnswerExportQuestionsFreightType" should {
      for (
        freightType <- ExportFreightType.values;
        requestType <- ExportRequestType.values.diff(mandatoryVesselDetailsRequestTypes)
      ) {
        s"go to AnswerExportQuestionsOptionalVesselInfo when submitted freight type ${ExportFreightType.keyOf(freightType).get} and requestType is ${ExportRequestType
          .keyOf(requestType)
          .get}" in {
          given(
            AnswerExportQuestionsFreightType(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                ExportQuestions(
                  requestType = Some(requestType),
                  routeType = Some(ExportRouteType.Route3),
                  priorityGoods = Some(ExportPriorityGoods.ClassADrugs)
                )
              )
            )
          ) when submittedExportQuestionsAnswerFreightType(eoriNumber)(
            freightType
          ) should thenGo(
            AnswerExportQuestionsOptionalVesselInfo(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                ExportQuestions(
                  requestType = Some(requestType),
                  routeType = Some(ExportRouteType.Route3),
                  priorityGoods = Some(ExportPriorityGoods.ClassADrugs),
                  freightType = Some(freightType)
                )
              )
            )
          )
        }

        s"go to ExportQuestionsSummary when submitted freight type ${ExportFreightType.keyOf(freightType).get} and requestType is ${ExportRequestType
          .keyOf(requestType)
          .get}, and answers are complete" in {
          given(
            AnswerExportQuestionsFreightType(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                completeExportQuestionsAnswers.copy(requestType = Some(requestType))
              )
            )
          ) when submittedExportQuestionsAnswerFreightType(eoriNumber)(
            freightType
          ) should thenGo(
            ExportQuestionsSummary(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                completeExportQuestionsAnswers.copy(freightType = Some(freightType), requestType = Some(requestType))
              )
            )
          )
        }
      }

      for (
        freightType <- ExportFreightType.values;
        requestType <- mandatoryVesselDetailsRequestTypes
      ) {
        s"go to AnswerExportQuestionsMandatoryVesselInfo when submitted freight type ${ExportFreightType.keyOf(freightType).get} and requestType is ${ExportRequestType
          .keyOf(requestType)
          .get}" in {
          given(
            AnswerExportQuestionsFreightType(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                ExportQuestions(
                  requestType = Some(requestType),
                  routeType = Some(ExportRouteType.Route3),
                  priorityGoods = Some(ExportPriorityGoods.ClassADrugs)
                )
              )
            )
          ) when submittedExportQuestionsAnswerFreightType(eoriNumber)(
            freightType
          ) should thenGo(
            AnswerExportQuestionsMandatoryVesselInfo(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                ExportQuestions(
                  requestType = Some(requestType),
                  routeType = Some(ExportRouteType.Route3),
                  priorityGoods = Some(ExportPriorityGoods.ClassADrugs),
                  freightType = Some(freightType)
                )
              )
            )
          )
        }

        s"go to ExportQuestionsSummary when submitted freight type ${ExportFreightType.keyOf(freightType).get} and requestType is ${ExportRequestType
          .keyOf(requestType)
          .get}, and answers are complete" in {
          given(
            AnswerExportQuestionsFreightType(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                completeExportQuestionsAnswers.copy(requestType = Some(requestType))
              )
            )
          ) when submittedExportQuestionsAnswerFreightType(eoriNumber)(
            freightType
          ) should thenGo(
            ExportQuestionsSummary(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                completeExportQuestionsAnswers.copy(freightType = Some(freightType), requestType = Some(requestType))
              )
            )
          )
        }
      }

      for (
        freightType <- ExportFreightType.values;
        requestType <- ExportRequestType.values
      ) {
        s"go to AnswerExportQuestionsMandatoryVesselInfo when submitted freight type ${ExportFreightType.keyOf(freightType).get} regardless of request type ${ExportRequestType
          .keyOf(requestType)
          .get} when route is Hold" in {
          given(
            AnswerExportQuestionsFreightType(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                ExportQuestions(
                  requestType = Some(requestType),
                  routeType = Some(ExportRouteType.Hold),
                  priorityGoods = Some(ExportPriorityGoods.ClassADrugs)
                )
              )
            )
          ) when submittedExportQuestionsAnswerFreightType(eoriNumber)(
            freightType
          ) should thenGo(
            AnswerExportQuestionsMandatoryVesselInfo(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                ExportQuestions(
                  requestType = Some(requestType),
                  routeType = Some(ExportRouteType.Hold),
                  priorityGoods = Some(ExportPriorityGoods.ClassADrugs),
                  freightType = Some(freightType)
                )
              )
            )
          )
        }

        s"go to ExportQuestionsSummary when submitted freight type ${ExportFreightType.keyOf(freightType).get} regardless of request type ${ExportRequestType
          .keyOf(requestType)
          .get} when route is Hold and answers are complete" in {
          given(
            AnswerExportQuestionsFreightType(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                completeExportQuestionsAnswers.copy(
                  requestType = Some(requestType),
                  routeType = Some(ExportRouteType.Hold)
                )
              )
            )
          ) when submittedExportQuestionsAnswerFreightType(eoriNumber)(
            freightType
          ) should thenGo(
            ExportQuestionsSummary(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                completeExportQuestionsAnswers.copy(
                  freightType = Some(freightType),
                  requestType = Some(requestType),
                  routeType = Some(ExportRouteType.Hold)
                )
              )
            )
          )
        }
      }

    }

    "at state AnswerExportQuestionsMandatoryVesselInfo" should {
      "go to AnswerExportQuestionsContactInfo when submitted required vessel details" in {
        given(
          AnswerExportQuestionsMandatoryVesselInfo(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.C1601),
                routeType = Some(ExportRouteType.Route3),
                priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ExportFreightType.Air)
              )
            )
          )
        ) when submittedExportQuestionsMandatoryVesselDetails(eoriNumber)(
          VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))
        ) should thenGo(
          AnswerExportQuestionsContactInfo(
            ExportQuestionsStateModel(
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
        )
      }

      "go to ExportQuestionsSummary when submitted required vessel details and answers are complete" in {
        val vesselDetails =
          VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))
        given(
          AnswerExportQuestionsMandatoryVesselInfo(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              completeExportQuestionsAnswers
            )
          )
        ) when submittedExportQuestionsMandatoryVesselDetails(eoriNumber)(vesselDetails) should thenGo(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              completeExportQuestionsAnswers.copy(vesselDetails = Some(vesselDetails))
            )
          )
        )
      }

      "stay when submitted partial vessel details" in {
        an[TransitionNotAllowed] shouldBe thrownBy {
          given(
            AnswerExportQuestionsMandatoryVesselInfo(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                ExportQuestions(
                  requestType = Some(ExportRequestType.C1601),
                  routeType = Some(ExportRouteType.Route3),
                  priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
                  freightType = Some(ExportFreightType.Air)
                )
              )
            )
          ) when submittedExportQuestionsMandatoryVesselDetails(eoriNumber)(
            VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), None)
          )
        }
      }
    }

    "at state AnswerExportQuestionsOptionalVesselInfo" should {
      "go to AnswerExportQuestionsContactInfo when submitted required vessel details" in {
        given(
          AnswerExportQuestionsOptionalVesselInfo(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route3),
                priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ExportFreightType.Air)
              )
            )
          )
        ) when submittedExportQuestionsOptionalVesselDetails(eoriNumber)(
          VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))
        ) should thenGo(
          AnswerExportQuestionsContactInfo(
            ExportQuestionsStateModel(
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
        )
      }

      "go to ExportQuestionsSummary when submitted required vessel details and answers are complete" in {
        val vesselDetails =
          VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))
        given(
          AnswerExportQuestionsOptionalVesselInfo(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              completeExportQuestionsAnswers
            )
          )
        ) when submittedExportQuestionsOptionalVesselDetails(eoriNumber)(vesselDetails) should thenGo(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              completeExportQuestionsAnswers.copy(vesselDetails = Some(vesselDetails))
            )
          )
        )
      }

      "go to AnswerExportQuestionsContactInfo when submitted empty vessel details" in {
        given(
          AnswerExportQuestionsOptionalVesselInfo(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route3),
                priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ExportFreightType.Air)
              )
            )
          )
        ) when submittedExportQuestionsOptionalVesselDetails(eoriNumber)(
          VesselDetails()
        ) should thenGo(
          AnswerExportQuestionsContactInfo(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route3),
                priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ExportFreightType.Air),
                vesselDetails = Some(VesselDetails())
              )
            )
          )
        )
      }

      "go to ExportQuestionsSummary when submitted empty vessel details and answers are complete" in {
        val vesselDetails = VesselDetails()
        given(
          AnswerExportQuestionsOptionalVesselInfo(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              completeExportQuestionsAnswers
            )
          )
        ) when submittedExportQuestionsOptionalVesselDetails(eoriNumber)(vesselDetails) should thenGo(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              completeExportQuestionsAnswers.copy(vesselDetails = Some(vesselDetails))
            )
          )
        )
      }
    }

    "at state AnswerExportQuestionsContactInfo" should {
      "go to ExportQuestionsSummary when submitted required contact details" in {
        given(
          AnswerExportQuestionsContactInfo(
            ExportQuestionsStateModel(
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
        ) when submittedExportQuestionsContactInfo(eoriNumber)(
          ExportContactInfo(contactName = "Full Name", contactEmail = "name@somewhere.com")
        ) should thenGo(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route3),
                priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ExportFreightType.Air),
                vesselDetails =
                  Some(VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))),
                contactInfo = Some(ExportContactInfo(contactName = "Full Name", contactEmail = "name@somewhere.com"))
              )
            )
          )
        )
      }
    }

    "at state AnswerImportQuestionsRequestType" should {
      for (requestType <- ImportRequestType.values) {
        s"go to AnswerImportQuestionsRequestType when submitted request type ${ImportRequestType.keyOf(requestType).get}" in {
          given(
            AnswerImportQuestionsRequestType(ImportQuestionsStateModel(importDeclarationDetails, ImportQuestions()))
          ) when submittedImportQuestionsAnswersRequestType(eoriNumber)(
            requestType
          ) should thenGo(
            AnswerImportQuestionsRouteType(
              ImportQuestionsStateModel(importDeclarationDetails, ImportQuestions(requestType = Some(requestType)))
            )
          )
        }

        s"go to ImportQuestionsSummary when submitted request type ${ImportRequestType.keyOf(requestType).get} and answers are complete" in {
          given(
            AnswerImportQuestionsRequestType(
              ImportQuestionsStateModel(importDeclarationDetails, completeImportQuestionsAnswers)
            )
          ) when submittedImportQuestionsAnswersRequestType(eoriNumber)(
            requestType
          ) should thenGo(
            ImportQuestionsSummary(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                completeImportQuestionsAnswers.copy(requestType = Some(requestType))
              )
            )
          )
        }

      }

    }

    "at state AnswerImportQuestionsRouteType" should {
      for (routeType <- ImportRouteType.values) {
        s"go to AnswerImportQuestionsHasPriorityGoods when submitted route ${ImportRouteType.keyOf(routeType).get}" in {
          given(
            AnswerImportQuestionsRouteType(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                ImportQuestions(requestType = Some(ImportRequestType.New))
              )
            )
          ) when submittedImportQuestionsAnswerRouteType(eoriNumber)(
            routeType
          ) should thenGo(
            AnswerImportQuestionsHasPriorityGoods(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(routeType))
              )
            )
          )
        }

        s"go to ImportQuestionsSummary when submitted route ${ImportRouteType.keyOf(routeType).get} and answers are complete" in {
          given(
            AnswerImportQuestionsRouteType(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                completeImportQuestionsAnswers
              )
            )
          ) when submittedImportQuestionsAnswerRouteType(eoriNumber)(
            routeType
          ) should thenGo(
            ImportQuestionsSummary(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                completeImportQuestionsAnswers.copy(routeType = Some(routeType))
              )
            )
          )
        }

      }
    }

    "at state AnswerImportQuestionsHasPriorityGoods" should {
      "go to AnswerImportQuestionsWhichPriorityGoods when selected YES" in {
        given(
          AnswerImportQuestionsHasPriorityGoods(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route1))
            )
          )
        ) when submittedImportQuestionsAnswerHasPriorityGoods(eoriNumber)(true) should thenGo(
          AnswerImportQuestionsWhichPriorityGoods(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route1),
                hasPriorityGoods = Some(true)
              )
            )
          )
        )
      }
      "go to AnswerImportQuestionsALVS when selected NO" in {
        given(
          AnswerImportQuestionsHasPriorityGoods(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route1))
            )
          )
        ) when submittedImportQuestionsAnswerHasPriorityGoods(eoriNumber)(false) should thenGo(
          AnswerImportQuestionsALVS(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route1),
                hasPriorityGoods = Some(false)
              )
            )
          )
        )
      }
      "go to AnswerImportQuestionsWhichPriorityGoods when selected YES and answer was YES before but no priority goods selected" in {
        val answers = completeImportQuestionsAnswers
          .copy(hasPriorityGoods = Some(true), priorityGoods = None)
        given(
          AnswerImportQuestionsHasPriorityGoods(
            ImportQuestionsStateModel(importDeclarationDetails, answers)
          )
        ) when submittedImportQuestionsAnswerHasPriorityGoods(eoriNumber)(true) should thenGo(
          AnswerImportQuestionsWhichPriorityGoods(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              answers.copy(hasPriorityGoods = Some(true))
            )
          )
        )
      }

      "go to AnswerImportQuestionsWhichPriorityGoods when selected YES and answer was NO before, and other answers are complete" in {
        val answers = completeImportQuestionsAnswers
          .copy(hasPriorityGoods = Some(false), priorityGoods = None)
        given(
          AnswerImportQuestionsHasPriorityGoods(
            ImportQuestionsStateModel(importDeclarationDetails, answers)
          )
        ) when submittedImportQuestionsAnswerHasPriorityGoods(eoriNumber)(true) should thenGo(
          AnswerImportQuestionsWhichPriorityGoods(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              answers.copy(hasPriorityGoods = Some(true))
            )
          )
        )
      }

      "go to ImportQuestionsSummary when selected YES and answers are complete" in {
        val answers = completeImportQuestionsAnswers
          .copy(hasPriorityGoods = Some(true), priorityGoods = Some(ImportPriorityGoods.HumanRemains))
        given(
          AnswerImportQuestionsHasPriorityGoods(
            ImportQuestionsStateModel(importDeclarationDetails, answers)
          )
        ) when submittedImportQuestionsAnswerHasPriorityGoods(eoriNumber)(true) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              answers
            )
          )
        )
      }
    }

    "at state AnswerImportQuestionsWhichPriorityGoods" should {
      for (priorityGoods <- ImportPriorityGoods.values) {
        s"go to AnswerImportQuestionsALVS when submitted priority good ${ImportPriorityGoods.keyOf(priorityGoods).get}" in {
          given(
            AnswerImportQuestionsWhichPriorityGoods(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route3))
              )
            )
          ) when submittedImportQuestionsAnswerWhichPriorityGoods(eoriNumber)(
            priorityGoods
          ) should thenGo(
            AnswerImportQuestionsALVS(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                ImportQuestions(
                  requestType = Some(ImportRequestType.New),
                  routeType = Some(ImportRouteType.Route3),
                  priorityGoods = Some(priorityGoods)
                )
              )
            )
          )
        }
        s"go to ImportQuestionsSummary when submitted priority good ${ImportPriorityGoods.keyOf(priorityGoods).get} and answers are complete" in {
          given(
            AnswerImportQuestionsWhichPriorityGoods(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                completeImportQuestionsAnswers
              )
            )
          ) when submittedImportQuestionsAnswerWhichPriorityGoods(eoriNumber)(
            priorityGoods
          ) should thenGo(
            ImportQuestionsSummary(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                completeImportQuestionsAnswers.copy(priorityGoods = Some(priorityGoods))
              )
            )
          )
        }
      }
    }

    "at state AnswerImportQuestionsALVS" should {
      "go to AnswerImportQuestionsFreightType when selected YES" in {
        given(
          AnswerImportQuestionsALVS(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route1))
            )
          )
        ) when submittedImportQuestionsAnswerHasALVS(eoriNumber)(true) should thenGo(
          AnswerImportQuestionsFreightType(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route1),
                hasALVS = Some(true)
              )
            )
          )
        )
      }

      "go to ImportQuestionsSummary when selected YES and answers are complete" in {
        given(
          AnswerImportQuestionsALVS(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              completeImportQuestionsAnswers
            )
          )
        ) when submittedImportQuestionsAnswerHasALVS(eoriNumber)(true) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              completeImportQuestionsAnswers.copy(hasALVS = Some(true))
            )
          )
        )
      }

      "go to AnswerImportQuestionsFreightType when selected NO" in {
        given(
          AnswerImportQuestionsALVS(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route1))
            )
          )
        ) when submittedImportQuestionsAnswerHasALVS(eoriNumber)(false) should thenGo(
          AnswerImportQuestionsFreightType(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route1),
                hasALVS = Some(false)
              )
            )
          )
        )
      }

      "go to ImportQuestionsSummary when selected NO and answers are complete" in {
        given(
          AnswerImportQuestionsALVS(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              completeImportQuestionsAnswers
            )
          )
        ) when submittedImportQuestionsAnswerHasALVS(eoriNumber)(false) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              completeImportQuestionsAnswers.copy(hasALVS = Some(false))
            )
          )
        )
      }
    }

    "at state AnswerImportQuestionsFreightType" should {
      for (
        freightType <- ImportFreightType.values;
        requestType <- ImportRequestType.values
      ) {
        s"go to AnswerImportQuestionsOptionalVesselInfo when submitted freight type ${ImportFreightType.keyOf(freightType).get} and request type is ${ImportRequestType
          .keyOf(requestType)
          .get}" in {
          given(
            AnswerImportQuestionsFreightType(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                ImportQuestions(
                  requestType = Some(requestType),
                  routeType = Some(ImportRouteType.Route3),
                  hasALVS = Some(false)
                )
              )
            )
          ) when submittedImportQuestionsAnswerFreightType(eoriNumber)(
            freightType
          ) should thenGo(
            AnswerImportQuestionsOptionalVesselInfo(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                ImportQuestions(
                  requestType = Some(requestType),
                  routeType = Some(ImportRouteType.Route3),
                  freightType = Some(freightType),
                  hasALVS = Some(false)
                )
              )
            )
          )
        }

        s"go to ImportQuestionsSummary when submitted freight type ${ImportFreightType.keyOf(freightType).get} and requestType is ${ImportRequestType
          .keyOf(requestType)
          .get}, and answers are complete" in {
          given(
            AnswerImportQuestionsFreightType(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                completeImportQuestionsAnswers.copy(requestType = Some(requestType))
              )
            )
          ) when submittedImportQuestionsAnswerFreightType(eoriNumber)(
            freightType
          ) should thenGo(
            ImportQuestionsSummary(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                completeImportQuestionsAnswers.copy(freightType = Some(freightType), requestType = Some(requestType))
              )
            )
          )
        }

      }

      for (
        freightType <- ImportFreightType.values;
        requestType <- ImportRequestType.values
      ) {
        s"go to AnswerImportQuestionsMandatoryVesselInfo when submitted freight type ${ImportFreightType.keyOf(freightType).get} regardless of request type ${ImportRequestType
          .keyOf(requestType)
          .get} when route is Hold" in {
          given(
            AnswerImportQuestionsFreightType(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                ImportQuestions(
                  requestType = Some(requestType),
                  routeType = Some(ImportRouteType.Hold),
                  hasALVS = Some(false)
                )
              )
            )
          ) when submittedImportQuestionsAnswerFreightType(eoriNumber)(
            freightType
          ) should thenGo(
            AnswerImportQuestionsMandatoryVesselInfo(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                ImportQuestions(
                  requestType = Some(requestType),
                  routeType = Some(ImportRouteType.Hold),
                  freightType = Some(freightType),
                  hasALVS = Some(false)
                )
              )
            )
          )
        }
        s"go to ImportQuestionsSummary when submitted freight type ${ImportFreightType.keyOf(freightType).get} regardless of request type ${ImportRequestType
          .keyOf(requestType)
          .get} when route is Hold and answers are complete" in {
          given(
            AnswerImportQuestionsFreightType(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                completeImportQuestionsAnswers
                  .copy(requestType = Some(requestType), routeType = Some(ImportRouteType.Hold))
              )
            )
          ) when submittedImportQuestionsAnswerFreightType(eoriNumber)(
            freightType
          ) should thenGo(
            ImportQuestionsSummary(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                completeImportQuestionsAnswers.copy(
                  freightType = Some(freightType),
                  requestType = Some(requestType),
                  routeType = Some(ImportRouteType.Hold)
                )
              )
            )
          )
        }

      }
    }

    "at state AnswerImportQuestionsOptionalVesselInfo" should {
      "go to AnswerImportQuestionsContactInfo when submitted required vessel details" in {
        given(
          AnswerImportQuestionsOptionalVesselInfo(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route3),
                priorityGoods = Some(ImportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ImportFreightType.Air)
              )
            )
          )
        ) when submittedImportQuestionsOptionalVesselDetails(eoriNumber)(
          VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))
        ) should thenGo(
          AnswerImportQuestionsContactInfo(
            ImportQuestionsStateModel(
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
        )
      }

      "go to ImportQuestionsSummary when submitted required vessel details and answers are complete" in {
        val vesselDetails =
          VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))
        given(
          AnswerImportQuestionsOptionalVesselInfo(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              completeImportQuestionsAnswers
            )
          )
        ) when submittedImportQuestionsOptionalVesselDetails(eoriNumber)(
          vesselDetails
        ) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              completeImportQuestionsAnswers.copy(vesselDetails = Some(vesselDetails))
            )
          )
        )
      }

      "go to AnswerImportQuestionsContactInfo when submitted empty vessel details" in {
        given(
          AnswerImportQuestionsOptionalVesselInfo(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route3),
                priorityGoods = Some(ImportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ImportFreightType.Air)
              )
            )
          )
        ) when submittedImportQuestionsOptionalVesselDetails(eoriNumber)(
          VesselDetails()
        ) should thenGo(
          AnswerImportQuestionsContactInfo(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route3),
                priorityGoods = Some(ImportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ImportFreightType.Air),
                vesselDetails = Some(VesselDetails())
              )
            )
          )
        )
      }

      "go to ImportQuestionsSummary when submitted empty vessel details and answers are complete" in {
        val vesselDetails = VesselDetails()
        given(
          AnswerImportQuestionsOptionalVesselInfo(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              completeImportQuestionsAnswers
            )
          )
        ) when submittedImportQuestionsOptionalVesselDetails(eoriNumber)(
          vesselDetails
        ) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              completeImportQuestionsAnswers.copy(vesselDetails = Some(vesselDetails))
            )
          )
        )
      }
    }

    "at state AnswerImportQuestionsContactInfo" should {
      "go to ImportQuestionsSummary when submitted required contact details" in {
        given(
          AnswerImportQuestionsContactInfo(
            ImportQuestionsStateModel(
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
        ) when submittedImportQuestionsContactInfo(eoriNumber)(
          ImportContactInfo(contactName = "Full Name", contactEmail = "name@somewhere.com")
        ) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route3),
                priorityGoods = Some(ImportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ImportFreightType.Air),
                contactInfo = Some(ImportContactInfo(contactName = "Full Name", contactEmail = "name@somewhere.com")),
                vesselDetails =
                  Some(VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00"))))
              )
            )
          )
        )
      }
    }

    "at state ExportQuestionsSummary" should {
      "go to UploadFile when initiateFileUpload" in {
        val mockUpscanInitiate: UpscanInitiateRequest => Future[UpscanInitiateResponse] = request =>
          Future.successful(
            UpscanInitiateResponse(
              reference = "foo-bar-ref",
              uploadRequest =
                UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> request.callbackUrl))
            )
          )
        val upscanRequest =
          UpscanInitiateRequest(
            "https://foo.bar/callback",
            Some("https://foo.bar/success"),
            Some("https://foo.bar/failure"),
            Some(10 * 1024 * 1024)
          )
        given(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(exportDeclarationDetails, completeExportQuestionsAnswers)
          )
        ) when initiateFileUpload(upscanRequest)(mockUpscanInitiate)(eoriNumber) should thenGo(
          UploadFile(
            FileUploadHostData(exportDeclarationDetails, completeExportQuestionsAnswers),
            "foo-bar-ref",
            UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            FileUploads(files = Seq(FileUpload.Initiated(1, "foo-bar-ref")))
          )
        )
      }
    }

    "at state ImportQuestionsSummary" should {
      "go to UploadFile when initiateFileUpload" in {
        val mockUpscanInitiate: UpscanInitiateApi = request =>
          Future.successful(
            UpscanInitiateResponse(
              reference = "foo-bar-ref",
              uploadRequest = UploadRequest(
                href = "https://s3.bucket",
                fields = Map(
                  "callbackUrl"         -> request.callbackUrl,
                  "successRedirect"     -> request.successRedirect.getOrElse(""),
                  "errorRedirect"       -> request.errorRedirect.getOrElse(""),
                  "minimumFileSize"     -> request.minimumFileSize.getOrElse(0).toString,
                  "maximumFileSize"     -> request.maximumFileSize.getOrElse(0).toString,
                  "expectedContentType" -> request.expectedContentType.getOrElse("")
                )
              )
            )
          )
        val upscanRequest =
          UpscanInitiateRequest(
            callbackUrl = "https://foo.bar/callback",
            successRedirect = Some("https://foo.bar/success"),
            errorRedirect = Some("https://foo.bar/failure"),
            minimumFileSize = Some(0),
            maximumFileSize = Some(10 * 1024 * 1024),
            expectedContentType = Some("image/jpeg,image/png")
          )
        given(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(importDeclarationDetails, completeImportQuestionsAnswers)
          )
        ) when initiateFileUpload(upscanRequest)(mockUpscanInitiate)(eoriNumber) should thenGo(
          UploadFile(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            "foo-bar-ref",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"         -> "https://foo.bar/callback",
                "successRedirect"     -> "https://foo.bar/success",
                "errorRedirect"       -> "https://foo.bar/failure",
                "minimumFileSize"     -> "0",
                "maximumFileSize"     -> "10485760",
                "expectedContentType" -> "image/jpeg,image/png"
              )
            ),
            FileUploads(files = Seq(FileUpload.Initiated(1, "foo-bar-ref")))
          )
        )
      }
    }

    "at state UploadFile" should {
      "go to WaitingForFileVerification when waitForFileVerification and not verified yet" in {
        given(
          UploadFile(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-2",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Initiated(2, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  3,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
                ),
                FileUpload.Failed(
                  4,
                  "foo-bar-ref-4",
                  UpscanNotification.FailureDetails(UpscanNotification.REJECTED, "some failure reason")
                )
              )
            )
          )
        ) when waitForFileVerification(
          eoriNumber
        ) should thenGo(
          WaitingForFileVerification(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-2",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(2, "foo-bar-ref-2"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Posted(2, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  3,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
                ),
                FileUpload.Failed(
                  4,
                  "foo-bar-ref-4",
                  UpscanNotification.FailureDetails(UpscanNotification.REJECTED, "some failure reason")
                )
              )
            )
          )
        )
      }

      "go to FileUploaded when waitForFileVerification and accepted already" in {
        given(
          UploadFile(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-3",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Initiated(2, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  3,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
                ),
                FileUpload.Failed(
                  4,
                  "foo-bar-ref-4",
                  UpscanNotification.FailureDetails(UpscanNotification.REJECTED, "some failure reason")
                )
              )
            )
          )
        ) when waitForFileVerification(
          eoriNumber
        ) should thenGo(
          FileUploaded(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Initiated(2, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  3,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
                ),
                FileUpload.Failed(
                  4,
                  "foo-bar-ref-4",
                  UpscanNotification.FailureDetails(UpscanNotification.REJECTED, "some failure reason")
                )
              )
            )
          )
        )
      }

      "go to UploadFile when waitForFileVerification and rejected already" in {
        given(
          UploadFile(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-4",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Initiated(2, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  3,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
                ),
                FileUpload.Failed(
                  4,
                  "foo-bar-ref-4",
                  UpscanNotification.FailureDetails(UpscanNotification.REJECTED, "some failure reason")
                )
              )
            )
          )
        ) when waitForFileVerification(
          eoriNumber
        ) should thenGo(
          WaitingForFileVerification(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-4",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(4, "foo-bar-ref-4"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Initiated(2, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  3,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
                ),
                FileUpload.Posted(4, "foo-bar-ref-4")
              )
            )
          )
        )
      }

      "go to FileUploaded when upscanCallbackArrived and accepted, and reference matches" in {
        given(
          UploadFile(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files = Seq(FileUpload.Initiated(1, "foo-bar-ref-1")))
          )
        ) when upscanCallbackArrived(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf"
            )
          )
        ) should thenGo(
          FileUploaded(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Accepted(
                  1,
                  "foo-bar-ref-1",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
                )
              )
            )
          )
        )
      }

      "go to UploadFile when upscanCallbackArrived and failed, and reference matches" in {
        given(
          UploadFile(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files = Seq(FileUpload.Initiated(1, "foo-bar-ref-1")))
          )
        ) when upscanCallbackArrived(
          UpscanFileFailed(
            reference = "foo-bar-ref-1",
            failureDetails = UpscanNotification.FailureDetails(
              failureReason = UpscanNotification.UNKNOWN,
              message = "e.g. This file has a virus"
            )
          )
        ) should thenGo(
          UploadFile(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files =
              Seq(
                FileUpload.Failed(
                  1,
                  "foo-bar-ref-1",
                  UpscanNotification.FailureDetails(UpscanNotification.UNKNOWN, "e.g. This file has a virus")
                )
              )
            ),
            Some(
              FileVerificationFailed(
                UpscanNotification.FailureDetails(UpscanNotification.UNKNOWN, "e.g. This file has a virus")
              )
            )
          )
        )
      }

      "go to UploadFile with error when fileUploadWasRejected" in {
        val state =
          UploadFile(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files = Seq(FileUpload.Initiated(1, "foo-bar-ref-1")))
          )

        given(state) when fileUploadWasRejected(eoriNumber)(
          S3UploadError(
            key = "foo-bar-ref-1",
            errorCode = "a",
            errorMessage = "b",
            errorResource = Some("c"),
            errorRequestId = Some("d")
          )
        ) should thenGo(
          state.copy(
            fileUploads = FileUploads(files =
              Seq(
                FileUpload.Rejected(
                  1,
                  "foo-bar-ref-1",
                  S3UploadError(
                    key = "foo-bar-ref-1",
                    errorCode = "a",
                    errorMessage = "b",
                    errorResource = Some("c"),
                    errorRequestId = Some("d")
                  )
                )
              )
            ),
            maybeUploadError = Some(
              FileTransmissionFailed(
                S3UploadError(
                  key = "foo-bar-ref-1",
                  errorCode = "a",
                  errorMessage = "b",
                  errorResource = Some("c"),
                  errorRequestId = Some("d")
                )
              )
            )
          )
        )
      }
    }

    "at state WaitingForFileVerification" should {
      "stay when waitForFileVerification and not verified yet" in {
        val state = WaitingForFileVerification(
          FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
          "foo-bar-ref-1",
          UploadRequest(
            href = "https://s3.bucket",
            fields = Map(
              "callbackUrl"     -> "https://foo.bar/callback",
              "successRedirect" -> "https://foo.bar/success",
              "errorRedirect"   -> "https://foo.bar/failure"
            )
          ),
          FileUpload.Posted(1, "foo-bar-ref-1"),
          FileUploads(files =
            Seq(
              FileUpload.Posted(1, "foo-bar-ref-1")
            )
          )
        )
        given(state) when waitForFileVerification(
          eoriNumber
        ) should thenGo(state)
      }

      "go to UploadFile when waitForFileVerification and reference unknown" in {
        given(
          WaitingForFileVerification(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-2",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(1, "foo-bar-ref-1"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1")
              )
            )
          )
        ) when waitForFileVerification(
          eoriNumber
        ) should thenGo(
          UploadFile(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-2",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1")
              )
            )
          )
        )
      }

      "go to FileUploaded when waitForFileVerification and file already accepted" in {
        given(
          WaitingForFileVerification(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Accepted(
              1,
              "foo-bar-ref-1",
              "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
              ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              "test.pdf",
              "application/pdf"
            ),
            FileUploads(files =
              Seq(
                FileUpload.Accepted(
                  1,
                  "foo-bar-ref-1",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
                )
              )
            )
          )
        ) when waitForFileVerification(
          eoriNumber
        ) should thenGo(
          FileUploaded(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Accepted(
                  1,
                  "foo-bar-ref-1",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
                )
              )
            )
          )
        )
      }

      "go to UploadFile when waitForFileVerification and file already failed" in {
        given(
          WaitingForFileVerification(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Failed(
              1,
              "foo-bar-ref-1",
              UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
            ),
            FileUploads(files =
              Seq(
                FileUpload.Failed(
                  1,
                  "foo-bar-ref-1",
                  UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
                )
              )
            )
          )
        ) when waitForFileVerification(
          eoriNumber
        ) should thenGo(
          UploadFile(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files =
              Seq(
                FileUpload.Failed(
                  1,
                  "foo-bar-ref-1",
                  UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
                )
              )
            ),
            Some(
              FileVerificationFailed(UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason"))
            )
          )
        )
      }

      "go to FileUploaded when upscanCallbackArrived and accepted, and reference matches" in {
        given(
          WaitingForFileVerification(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(1, "foo-bar-ref-1"),
            FileUploads(files = Seq(FileUpload.Posted(1, "foo-bar-ref-1")))
          )
        ) when upscanCallbackArrived(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf"
            )
          )
        ) should thenGo(
          FileUploaded(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Accepted(
                  1,
                  "foo-bar-ref-1",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
                )
              )
            )
          )
        )
      }

      "go to UploadFile when upscanCallbackArrived and failed, and reference matches" in {
        given(
          WaitingForFileVerification(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(1, "foo-bar-ref-1"),
            FileUploads(files = Seq(FileUpload.Posted(1, "foo-bar-ref-1")))
          )
        ) when upscanCallbackArrived(
          UpscanFileFailed(
            reference = "foo-bar-ref-1",
            failureDetails = UpscanNotification.FailureDetails(
              failureReason = UpscanNotification.QUARANTINE,
              message = "e.g. This file has a virus"
            )
          )
        ) should thenGo(
          UploadFile(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files =
              Seq(
                FileUpload.Failed(
                  1,
                  "foo-bar-ref-1",
                  UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "e.g. This file has a virus")
                )
              )
            ),
            Some(
              FileVerificationFailed(
                UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "e.g. This file has a virus")
              )
            )
          )
        )
      }

      "stay at WaitingForFileVerification when upscanCallbackArrived and reference doesn't match" in {
        given(
          WaitingForFileVerification(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(1, "foo-bar-ref-1"),
            FileUploads(files = Seq(FileUpload.Posted(1, "foo-bar-ref-1"), FileUpload.Posted(2, "foo-bar-ref-2")))
          )
        ) when upscanCallbackArrived(
          UpscanFileFailed(
            reference = "foo-bar-ref-2",
            failureDetails = UpscanNotification.FailureDetails(
              failureReason = UpscanNotification.QUARANTINE,
              message = "e.g. This file has a virus"
            )
          )
        ) should thenGo(
          WaitingForFileVerification(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(1, "foo-bar-ref-1"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Failed(
                  2,
                  "foo-bar-ref-2",
                  UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "e.g. This file has a virus")
                )
              )
            )
          )
        )
      }

      "retreat to FileUploaded when some files has been uploaded already" in {
        given(
          WaitingForFileVerification(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(1, "foo-bar-ref-1"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Accepted(
                  2,
                  "foo-bar-ref-2",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
                )
              )
            )
          )
        ) when backToFileUploaded(eoriNumber) should thenGo(
          FileUploaded(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "foo-bar-ref-1"),
                FileUpload.Accepted(
                  2,
                  "foo-bar-ref-2",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
                )
              )
            ),
            true
          )
        )
      }

      "retreat to ImportQuestionsSummary when none file has been uploaded yet" in {
        given(
          WaitingForFileVerification(
            FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(1, "foo-bar-ref-1"),
            FileUploads(files = Seq(FileUpload.Posted(1, "foo-bar-ref-1"), FileUpload.Posted(2, "foo-bar-ref-2")))
          )
        ) when backToFileUploaded(eoriNumber) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              completeImportQuestionsAnswers,
              Some(
                FileUploads(files =
                  Seq(
                    FileUpload.Posted(1, "foo-bar-ref-1"),
                    FileUpload.Posted(2, "foo-bar-ref-2")
                  )
                )
              )
            )
          )
        )
      }
    }

    "at state FileUploaded" should {
      "go to acknowledged FileUploaded when waitForFileVerification" in {
        val state = FileUploaded(
          FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
          FileUploads(files =
            Seq(
              FileUpload.Accepted(
                1,
                "foo-bar-ref-1",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf"
              )
            )
          ),
          acknowledged = false
        )

        given(state) when
          waitForFileVerification(eoriNumber) should
          thenGo(state.copy(acknowledged = true))
      }
    }

    "go to CreateCaseConfirmation when createCase" in {
      val mockCreateCaseApi: CreateCaseApi = { request =>
        Future.successful(TraderServicesCreateCaseResponse(correlationId = "", result = Some("A1234567890")))
      }
      given(
        FileUploaded(
          FileUploadHostData(importDeclarationDetails, completeImportQuestionsAnswers),
          FileUploads(files =
            Seq(
              FileUpload.Accepted(
                1,
                "foo-bar-ref-1",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf"
              )
            )
          ),
          acknowledged = false
        )
      ) when (createCase(mockCreateCaseApi)(eoriNumber)) should thenGo(
        CreateCaseConfirmation(
          importDeclarationDetails,
          completeImportQuestionsAnswers,
          Seq(
            UploadedFile(
              "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
              ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              "test.pdf",
              "application/pdf"
            )
          ),
          "A1234567890"
        )
      )
    }
  }

  case class given[S <: State: ClassTag](initialState: S)
      extends CreateCaseJourneyService[DummyContext] with InMemoryStore[(State, List[State]), DummyContext] {

    await(save((initialState, Nil)))

    def withBreadcrumbs(breadcrumbs: State*): this.type = {
      val (state, _) = await(fetch).getOrElse((Start, Nil))
      await(save((state, breadcrumbs.toList)))
      this
    }

    def when(transition: Transition): (State, List[State]) =
      await(super.apply(transition))

    def shouldFailWhen(transition: Transition) =
      Try(await(super.apply(transition))).isSuccess shouldBe false

    def when(merger: Merger[S], state: State): (State, List[State]) =
      await(super.modify { s: S => merger.apply((s, state)) })
  }
}

trait TestData {

  val eoriNumber = "foo"
  val correlationId = "123"

  val exportDeclarationDetails = DeclarationDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-09-23"))
  val importDeclarationDetails = DeclarationDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-09-23"))
  val invalidDeclarationDetails = DeclarationDetails(EPU(123), EntryNumber("0000000"), LocalDate.parse("2020-09-23"))

  val completeExportQuestionsAnswers = ExportQuestions(
    requestType = Some(ExportRequestType.New),
    routeType = Some(ExportRouteType.Route3),
    hasPriorityGoods = Some(true),
    priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
    freightType = Some(ExportFreightType.Air),
    vesselDetails =
      Some(VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))),
    contactInfo = Some(ExportContactInfo(contactName = "Bob", contactEmail = "name@somewhere.com"))
  )

  val completeImportQuestionsAnswers = ImportQuestions(
    requestType = Some(ImportRequestType.New),
    routeType = Some(ImportRouteType.Route3),
    hasPriorityGoods = Some(true),
    priorityGoods = Some(ImportPriorityGoods.ExplosivesOrFireworks),
    hasALVS = Some(true),
    freightType = Some(ImportFreightType.Air),
    vesselDetails =
      Some(VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))),
    contactInfo = Some(ImportContactInfo(contactName = "Bob", contactEmail = "name@somewhere.com"))
  )

  val nonEmptyFileUploads = FileUploads(files =
    Seq(
      FileUpload.Accepted(
        1,
        "foo-bar-ref-1",
        "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
        ZonedDateTime.parse("2018-04-24T09:30:00Z"),
        "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
        "test.pdf",
        "application/pdf"
      )
    )
  )

}
