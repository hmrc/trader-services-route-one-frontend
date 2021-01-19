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

package uk.gov.hmrc.traderservices.journey

import uk.gov.hmrc.traderservices.connectors.{ApiError, TraderServicesCaseResponse, TraderServicesResult, UpscanInitiateRequest, UpscanInitiateResponse}
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.FileUploadState._
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.FileUploadTransitions._
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.Rules._
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.State._
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.Transitions._
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.{start => _, _}
import uk.gov.hmrc.traderservices.models.ImportFreightType.Air
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.services.CreateCaseJourneyService
import uk.gov.hmrc.traderservices.support.{InMemoryStore, StateMatchers, UnitSpec}
import java.time.{LocalDate, LocalTime, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag
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

      "go to ExportQuestionsSummary when submitted declaration details for export and all answers are complete" in {
        given(
          EnterDeclarationDetails(
            declarationDetailsOpt = None,
            exportQuestionsAnswersOpt = Some(completeExportQuestionsAnswers),
            fileUploadsOpt = Some(nonEmptyFileUploads)
          )
        ) when submittedDeclarationDetails(eoriNumber)(
          exportDeclarationDetails
        ) should thenGo(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              completeExportQuestionsAnswers,
              Some(nonEmptyFileUploads)
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

      "go to ImportQuestionsSummary when submitted declaration details for import and all answers are complete" in {
        given(
          EnterDeclarationDetails(
            declarationDetailsOpt = None,
            importQuestionsAnswersOpt = Some(completeImportQuestionsAnswers),
            fileUploadsOpt = Some(nonEmptyFileUploads)
          )
        ) when submittedDeclarationDetails(eoriNumber)(
          importDeclarationDetails
        ) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              completeImportQuestionsAnswers,
              Some(nonEmptyFileUploads)
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

        s"go to ExportQuestionsSummary when submitted request type ${ExportRequestType.keyOf(requestType).get} and all answers are complete" in {
          given(
            AnswerExportQuestionsRequestType(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                completeExportQuestionsAnswers,
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedExportQuestionsAnswerRequestType(eoriNumber)(
            requestType
          ) should thenGo(
            ExportQuestionsSummary(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                completeExportQuestionsAnswers.copy(requestType = Some(requestType)),
                Some(nonEmptyFileUploads)
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

        s"go to ExportQuestionsSummary when submitted route ${ExportRouteType.keyOf(routeType).get} and all answers are complete" in {
          given(
            AnswerExportQuestionsRouteType(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                completeExportQuestionsAnswers,
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedExportQuestionsAnswerRouteType(eoriNumber)(
            routeType
          ) should thenGo(
            ExportQuestionsSummary(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                completeExportQuestionsAnswers.copy(routeType = Some(routeType)),
                Some(nonEmptyFileUploads)
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

      "go to ExportQuestionsSummary when selected YES and all answers are complete" in {
        val answers = completeExportQuestionsAnswers
          .copy(hasPriorityGoods = Some(true), priorityGoods = Some(ExportPriorityGoods.HumanRemains))
        given(
          AnswerExportQuestionsHasPriorityGoods(
            ExportQuestionsStateModel(exportDeclarationDetails, answers, Some(nonEmptyFileUploads))
          )
        ) when submittedExportQuestionsAnswerHasPriorityGoods(eoriNumber)(true) should thenGo(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              answers,
              Some(nonEmptyFileUploads)
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

        s"go to ExportQuestionsSummary when submitted priority good ${ExportPriorityGoods.keyOf(priorityGood).get} and all answers are complete" in {
          given(
            AnswerExportQuestionsWhichPriorityGoods(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                completeExportQuestionsAnswers,
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedExportQuestionsAnswerWhichPriorityGoods(eoriNumber)(
            priorityGood
          ) should thenGo(
            ExportQuestionsSummary(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                completeExportQuestionsAnswers.copy(priorityGoods = Some(priorityGood)),
                Some(nonEmptyFileUploads)
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
                  priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks)
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
                  priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
                  freightType = Some(freightType)
                )
              )
            )
          )
        }

        s"go to ExportQuestionsSummary when submitted freight type ${ExportFreightType.keyOf(freightType).get} and requestType is ${ExportRequestType
          .keyOf(requestType)
          .get}, and all answers are complete" in {
          given(
            AnswerExportQuestionsFreightType(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                completeExportQuestionsAnswers.copy(requestType = Some(requestType)),
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedExportQuestionsAnswerFreightType(eoriNumber)(
            freightType
          ) should thenGo(
            ExportQuestionsSummary(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                completeExportQuestionsAnswers.copy(freightType = Some(freightType), requestType = Some(requestType)),
                Some(nonEmptyFileUploads)
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
                  priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks)
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
                  priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
                  freightType = Some(freightType)
                )
              )
            )
          )
        }

        s"go to ExportQuestionsSummary when submitted freight type ${ExportFreightType.keyOf(freightType).get} and requestType is ${ExportRequestType
          .keyOf(requestType)
          .get}, and all answers are complete" in {
          given(
            AnswerExportQuestionsFreightType(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                completeExportQuestionsAnswers.copy(requestType = Some(requestType)),
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedExportQuestionsAnswerFreightType(eoriNumber)(
            freightType
          ) should thenGo(
            ExportQuestionsSummary(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                completeExportQuestionsAnswers.copy(freightType = Some(freightType), requestType = Some(requestType)),
                Some(nonEmptyFileUploads)
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
                  priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks)
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
                  priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
                  freightType = Some(freightType)
                )
              )
            )
          )
        }

        s"go to ExportQuestionsSummary when submitted freight type ${ExportFreightType.keyOf(freightType).get} regardless of request type ${ExportRequestType
          .keyOf(requestType)
          .get} when route is Hold and all answers are complete" in {
          given(
            AnswerExportQuestionsFreightType(
              ExportQuestionsStateModel(
                exportDeclarationDetails,
                completeExportQuestionsAnswers.copy(
                  requestType = Some(requestType),
                  routeType = Some(ExportRouteType.Hold)
                ),
                Some(nonEmptyFileUploads)
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
                ),
                Some(nonEmptyFileUploads)
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

      "go to ExportQuestionsSummary when submitted required vessel details and all answers are complete" in {
        val vesselDetails =
          VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))
        given(
          AnswerExportQuestionsMandatoryVesselInfo(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              completeExportQuestionsAnswers,
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedExportQuestionsMandatoryVesselDetails(eoriNumber)(vesselDetails) should thenGo(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              completeExportQuestionsAnswers.copy(vesselDetails = Some(vesselDetails)),
              Some(nonEmptyFileUploads)
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
      "go to AnswerExportQuestionsMandatoryVesselInfo when mandatory vessel details are submitted but user tries to redirect to optional vessel info" in {
        val answerMandatoryVesselInfo = AnswerExportQuestionsMandatoryVesselInfo(
          ExportQuestionsStateModel(
            exportDeclarationDetails,
            ExportQuestions(
              requestType = Some(ExportRequestType.C1601),
              routeType = Some(ExportRouteType.Hold),
              priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
              freightType = Some(ExportFreightType.Air)
            )
          )
        )
        given(answerMandatoryVesselInfo) when backToAnswerExportQuestionsOptionalVesselInfo(eoriNumber) should thenGo(
          answerMandatoryVesselInfo
        )
      }

      "go to ExportQuestionsSummary when submitted required vessel details and all answers are complete" in {
        val vesselDetails =
          VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))
        given(
          AnswerExportQuestionsOptionalVesselInfo(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              completeExportQuestionsAnswers,
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedExportQuestionsOptionalVesselDetails(eoriNumber)(vesselDetails) should thenGo(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              completeExportQuestionsAnswers.copy(vesselDetails = Some(vesselDetails)),
              Some(nonEmptyFileUploads)
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

      "go to ExportQuestionsSummary when submitted empty vessel details and all answers are complete" in {
        val vesselDetails = VesselDetails()
        given(
          AnswerExportQuestionsOptionalVesselInfo(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              completeExportQuestionsAnswers,
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedExportQuestionsOptionalVesselDetails(eoriNumber)(vesselDetails) should thenGo(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportDeclarationDetails,
              completeExportQuestionsAnswers.copy(vesselDetails = Some(vesselDetails)),
              Some(nonEmptyFileUploads)
            )
          )
        )
      }
    }

    "at state AnswerExportQuestionsContactInfo" should {
      "go to UploadFile when submitted required contact details and none file uploads exist yet" in {
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
        ) when submittedExportQuestionsContactInfo(upscanRequest)(mockUpscanInitiate)(eoriNumber)(
          ExportContactInfo(contactEmail = "name@somewhere.com")
        ) should thenGo(
          UploadFile(
            hostData = FileUploadHostData(
              exportDeclarationDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route3),
                priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ExportFreightType.Air),
                vesselDetails =
                  Some(VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))),
                contactInfo = Some(ExportContactInfo(contactEmail = "name@somewhere.com"))
              )
            ),
            reference = "foo-bar-ref",
            uploadRequest =
              UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            fileUploads = FileUploads(files = Seq(FileUpload.Initiated(1, "foo-bar-ref")))
          )
        )
      }

      "go to FileUploaded when submitted required contact details and holds already some file uploads" in {
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
              ),
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedExportQuestionsContactInfo(upscanRequest)(mockUpscanInitiate)(eoriNumber)(
          ExportContactInfo(contactEmail = "name@somewhere.com")
        ) should thenGo(
          FileUploaded(
            hostData = FileUploadHostData(
              exportDeclarationDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route3),
                priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ExportFreightType.Air),
                vesselDetails =
                  Some(VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))),
                contactInfo = Some(ExportContactInfo(contactEmail = "name@somewhere.com"))
              )
            ),
            fileUploads = nonEmptyFileUploads
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

        s"go to ImportQuestionsSummary when submitted request type ${ImportRequestType.keyOf(requestType).get} and all answers are complete" in {
          given(
            AnswerImportQuestionsRequestType(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                completeImportQuestionsAnswers,
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedImportQuestionsAnswersRequestType(eoriNumber)(
            requestType
          ) should thenGo(
            ImportQuestionsSummary(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                completeImportQuestionsAnswers.copy(requestType = Some(requestType)),
                Some(nonEmptyFileUploads)
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

        s"go to ImportQuestionsSummary when submitted route ${ImportRouteType.keyOf(routeType).get} and all answers are complete" in {
          given(
            AnswerImportQuestionsRouteType(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                completeImportQuestionsAnswers,
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedImportQuestionsAnswerRouteType(eoriNumber)(
            routeType
          ) should thenGo(
            ImportQuestionsSummary(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                completeImportQuestionsAnswers.copy(routeType = Some(routeType)),
                Some(nonEmptyFileUploads)
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

      "go to ImportQuestionsSummary when selected YES and all answers are complete" in {
        val answers = completeImportQuestionsAnswers
          .copy(hasPriorityGoods = Some(true), priorityGoods = Some(ImportPriorityGoods.HumanRemains))
        given(
          AnswerImportQuestionsHasPriorityGoods(
            ImportQuestionsStateModel(importDeclarationDetails, answers, Some(nonEmptyFileUploads))
          )
        ) when submittedImportQuestionsAnswerHasPriorityGoods(eoriNumber)(true) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              answers,
              Some(nonEmptyFileUploads)
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
        s"go to ImportQuestionsSummary when submitted priority good ${ImportPriorityGoods.keyOf(priorityGoods).get} and all answers are complete" in {
          given(
            AnswerImportQuestionsWhichPriorityGoods(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                completeImportQuestionsAnswers,
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedImportQuestionsAnswerWhichPriorityGoods(eoriNumber)(
            priorityGoods
          ) should thenGo(
            ImportQuestionsSummary(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                completeImportQuestionsAnswers.copy(priorityGoods = Some(priorityGoods)),
                Some(nonEmptyFileUploads)
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

      "go to ImportQuestionsSummary when selected YES and all answers are complete" in {
        given(
          AnswerImportQuestionsALVS(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              completeImportQuestionsAnswers,
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedImportQuestionsAnswerHasALVS(eoriNumber)(true) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              completeImportQuestionsAnswers.copy(hasALVS = Some(true)),
              Some(nonEmptyFileUploads)
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

      "go to ImportQuestionsSummary when selected NO and all answers are complete" in {
        given(
          AnswerImportQuestionsALVS(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              completeImportQuestionsAnswers,
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedImportQuestionsAnswerHasALVS(eoriNumber)(false) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              completeImportQuestionsAnswers.copy(hasALVS = Some(false)),
              Some(nonEmptyFileUploads)
            )
          )
        )
      }
    }
    "at state AnswerImportQuestionsMandatoryVesselInfo" should {
      "go to AnswerImportQuestionsMandatoryVesselInfo when mandatory vessel details are submitted but user tries to redirect to optional vessel info" in {
        val answerMandatoryVesselInfo = AnswerImportQuestionsMandatoryVesselInfo(
          ImportQuestionsStateModel(
            importDeclarationDetails,
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Hold),
              freightType = Some(Air),
              hasALVS = Some(false)
            )
          )
        )
        given(answerMandatoryVesselInfo) when backToAnswerImportQuestionsOptionalVesselInfo(eoriNumber) should thenGo(
          answerMandatoryVesselInfo
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
          .get}, and all answers are complete" in {
          given(
            AnswerImportQuestionsFreightType(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                completeImportQuestionsAnswers.copy(requestType = Some(requestType)),
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedImportQuestionsAnswerFreightType(eoriNumber)(
            freightType
          ) should thenGo(
            ImportQuestionsSummary(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                completeImportQuestionsAnswers.copy(freightType = Some(freightType), requestType = Some(requestType)),
                Some(nonEmptyFileUploads)
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
          .get} when route is Hold and all answers are complete" in {
          given(
            AnswerImportQuestionsFreightType(
              ImportQuestionsStateModel(
                importDeclarationDetails,
                completeImportQuestionsAnswers
                  .copy(requestType = Some(requestType), routeType = Some(ImportRouteType.Hold)),
                Some(nonEmptyFileUploads)
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
                ),
                Some(nonEmptyFileUploads)
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

      "go to ImportQuestionsSummary when submitted required vessel details and all answers are complete" in {
        val vesselDetails =
          VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))
        given(
          AnswerImportQuestionsOptionalVesselInfo(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              completeImportQuestionsAnswers,
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedImportQuestionsOptionalVesselDetails(eoriNumber)(
          vesselDetails
        ) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              completeImportQuestionsAnswers.copy(vesselDetails = Some(vesselDetails)),
              Some(nonEmptyFileUploads)
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

      "go to ImportQuestionsSummary when submitted empty vessel details and all answers are complete" in {
        val vesselDetails = VesselDetails()
        given(
          AnswerImportQuestionsOptionalVesselInfo(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              completeImportQuestionsAnswers,
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedImportQuestionsOptionalVesselDetails(eoriNumber)(
          vesselDetails
        ) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              completeImportQuestionsAnswers.copy(vesselDetails = Some(vesselDetails)),
              Some(nonEmptyFileUploads)
            )
          )
        )
      }
    }

    "at state AnswerImportQuestionsContactInfo" should {
      "go to UploadFile when submitted required contact details and none file uploads exist yet" in {
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
        ) when submittedImportQuestionsContactInfo(upscanRequest)(mockUpscanInitiate)(eoriNumber)(
          ImportContactInfo(contactEmail = "name@somewhere.com")
        ) should thenGo(
          UploadFile(
            hostData = FileUploadHostData(
              importDeclarationDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route3),
                priorityGoods = Some(ImportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ImportFreightType.Air),
                contactInfo = Some(ImportContactInfo(contactEmail = "name@somewhere.com")),
                vesselDetails =
                  Some(VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00"))))
              )
            ),
            reference = "foo-bar-ref",
            uploadRequest =
              UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            fileUploads = FileUploads(files = Seq(FileUpload.Initiated(1, "foo-bar-ref")))
          )
        )
      }

      "go to FileUploaded when submitted required contact details and some file uploads already exist" in {
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
              ),
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedImportQuestionsContactInfo(upscanRequest)(mockUpscanInitiate)(eoriNumber)(
          ImportContactInfo(contactEmail = "name@somewhere.com")
        ) should thenGo(
          FileUploaded(
            hostData = FileUploadHostData(
              importDeclarationDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route3),
                priorityGoods = Some(ImportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ImportFreightType.Air),
                contactInfo = Some(ImportContactInfo(contactEmail = "name@somewhere.com")),
                vesselDetails =
                  Some(VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00"))))
              )
            ),
            fileUploads = nonEmptyFileUploads
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

      "go to UploadFile when waitForFileVerification and file upload already rejected" in {
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
            ),
            Some(
              FileVerificationFailed(
                UpscanNotification.FailureDetails(UpscanNotification.REJECTED, "some failure reason")
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

      "go to UploadFile when upscanCallbackArrived and accepted, and reference matches but upload is a duplicate" in {
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
            FileUploads(files =
              Seq(
                FileUpload.Initiated(1, "foo-bar-ref-1"),
                FileUpload.Accepted(
                  2,
                  "foo-bar-ref-2",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2020-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test1.pdf",
                  "application/pdf"
                )
              )
            )
          )
        ) when upscanCallbackArrived(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2020-04-24T09:32:13Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test2.png",
              fileMimeType = "image/png"
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
                FileUpload.Duplicate(
                  1,
                  "foo-bar-ref-1",
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test1.pdf",
                  "test2.png"
                ),
                FileUpload.Accepted(
                  2,
                  "foo-bar-ref-2",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2020-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test1.pdf",
                  "application/pdf"
                )
              )
            ),
            Some(
              DuplicateFileUpload(
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test1.pdf",
                "test2.png"
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

      "retreat to AnswerImportQuestionsContactInfo when none file has been uploaded and accepted yet" in {
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
          AnswerImportQuestionsContactInfo(
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

      "go to CreateCaseConfirmation when createCase" in {
        val mockCreateCaseApi: CreateCaseApi = { request =>
          Future.successful(
            TraderServicesCaseResponse(
              correlationId = "",
              result = Some(TraderServicesResult("A1234567890", generatedAt))
            )
          )
        }
        given(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              completeImportQuestionsAnswers,
              Some(
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
          )
        ) when (createCase(mockCreateCaseApi)(eoriNumber)) should thenGo(
          CreateCaseConfirmation(
            importDeclarationDetails,
            completeImportQuestionsAnswers,
            Seq(
              UploadedFile(
                "foo-bar-ref-1",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf"
              )
            ),
            TraderServicesResult("A1234567890", generatedAt)
          )
        )
      }

      "go to CaseAlreadyExists when createCase but case already exists" in {
        val mockCreateCaseApi: CreateCaseApi = { request =>
          Future.successful(
            TraderServicesCaseResponse(
              correlationId = "",
              error = Some(ApiError(errorCode = "409", errorMessage = Some("A1234567890")))
            )
          )
        }
        given(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importDeclarationDetails,
              completeImportQuestionsAnswers,
              Some(
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
          )
        ) when (createCase(mockCreateCaseApi)(eoriNumber)) should thenGo(
          CaseAlreadyExists("A1234567890")
        )
      }
    }

    "at state CreateCaseConfirmation" should {
      "go to Start when start" in {
        given(
          CreateCaseConfirmation(
            importDeclarationDetails,
            completeImportQuestionsAnswers,
            Seq(
              UploadedFile(
                "foo",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf"
              )
            ),
            TraderServicesResult("A1234567890", generatedAt)
          )
        ) when start(eoriNumber) should thenGo(Start)
      }

      "go to clean EnterDeclarationDetails when going back" in {
        given(
          CreateCaseConfirmation(
            importDeclarationDetails,
            completeImportQuestionsAnswers,
            Seq(
              UploadedFile(
                "foo",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf"
              )
            ),
            TraderServicesResult("A1234567890", generatedAt)
          )
        ) when backToEnterDeclarationDetails(eoriNumber) should thenGo(EnterDeclarationDetails())
      }
    }

    "at state CaseAlreadyExists" should {
      "go to Start when start" in {
        given(
          CaseAlreadyExists("A1234567890")
        ) when start(eoriNumber) should thenGo(Start)
      }

      "go to clean EnterDeclarationDetails when going back" in {
        given(
          CaseAlreadyExists("A1234567890")
        ) when backToEnterDeclarationDetails(eoriNumber) should thenGo(EnterDeclarationDetails())
      }
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
  val generatedAt = java.time.LocalDateTime.of(2018, 12, 11, 10, 20, 0)

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
    contactInfo = Some(ExportContactInfo(contactEmail = "name@somewhere.com"))
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
    contactInfo = Some(ImportContactInfo(contactEmail = "name@somewhere.com"))
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
