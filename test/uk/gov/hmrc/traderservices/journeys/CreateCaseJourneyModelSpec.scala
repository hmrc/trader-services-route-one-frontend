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

import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.traderservices.connectors.{ApiError, TraderServicesCaseResponse, TraderServicesResult, UpscanInitiateRequest, UpscanInitiateResponse}
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.FileUploadState._
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.FileUploadTransitions._
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.Rules._
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.{start => _, _}
import uk.gov.hmrc.traderservices.models.ImportFreightType.Air
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.support.JourneyModelSpec

import java.time.{LocalDate, LocalTime, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CreateCaseJourneyModelSpec
    extends AnyWordSpec with Matchers with BeforeAndAfterAll with JourneyModelSpec with TestData {

  override val model = CreateCaseJourneyModel

  import model.State._
  import model.Transitions._

  "CreateCaseJourneyModel" when {
    "at state Start" should {
      "stay at Start when start" in {
        given(Start) when start should thenGo(Start)
      }

      "go to clean ChooseNewOrExistingCase" in {
        given(Start) when chooseNewOrExistingCase should thenGo(ChooseNewOrExistingCase())
      }

      "no change when enterEntryDetails" in {
        given(Start).when(backToEnterEntryDetails).thenNoChange
      }

      "fail if any other transition requested" in {
        given(Start) when submittedEntryDetails(
          exportEntryDetails
        )
      }
    }

    "at state ChooseNewOrExistingCase" should {
      "go to clean EnterEntryDetails when selected New and no former answers" in {
        given(ChooseNewOrExistingCase()) when submittedNewOrExistingCaseChoice(
          NewOrExistingCase.New
        ) should thenGo(EnterEntryDetails())
      }

      "go to EnterEntryDetails when selected New and keep former answers" in {
        given(
          ChooseNewOrExistingCase(
            entryDetailsOpt = Some(importEntryDetails),
            importQuestionsAnswersOpt = Some(completeImportQuestionsAnswers),
            fileUploadsOpt = Some(nonEmptyFileUploads)
          )
        ) when submittedNewOrExistingCaseChoice(
          NewOrExistingCase.New
        ) should thenGo(
          EnterEntryDetails(
            entryDetailsOpt = Some(importEntryDetails),
            importQuestionsAnswersOpt = Some(completeImportQuestionsAnswers),
            fileUploadsOpt = Some(nonEmptyFileUploads)
          )
        )
      }

      "go to TurnToAmendCaseJourney when selected Existing and shouldn't continue" in {
        given(ChooseNewOrExistingCase(continueAmendCaseJourney = false)) when submittedNewOrExistingCaseChoice(
          NewOrExistingCase.Existing
        ) should thenGo(TurnToAmendCaseJourney(continueAmendCaseJourney = false))
      }

      "go to TurnToAmendCaseJourney when selected Existing and must continue" in {
        given(ChooseNewOrExistingCase(continueAmendCaseJourney = true)) when submittedNewOrExistingCaseChoice(
          NewOrExistingCase.Existing
        ) should thenGo(TurnToAmendCaseJourney(continueAmendCaseJourney = true))
      }

      "go back to Start when start" in {
        given(
          ChooseNewOrExistingCase(
            entryDetailsOpt = Some(importEntryDetails),
            importQuestionsAnswersOpt = Some(completeImportQuestionsAnswers)
          )
        ) when start should thenGo(Start)
      }
    }

    "at state TurnToAmendCaseJourney" should {
      "go back to ChooseNewOrExistingCase and reset continue flag" in {
        given(TurnToAmendCaseJourney(continueAmendCaseJourney = false)) when chooseNewOrExistingCase should thenGo(
          ChooseNewOrExistingCase(Some(NewOrExistingCase.Existing))
        )
      }

      "go back to ChooseNewOrExistingCase" in {
        given(TurnToAmendCaseJourney(continueAmendCaseJourney = true)) when chooseNewOrExistingCase should thenGo(
          ChooseNewOrExistingCase(Some(NewOrExistingCase.Existing))
        )
      }
    }

    "at state EnterEntryDetails" should {
      "go to AnswerExportQuestionsRequestType when submitted declaration details for export" in {
        given(EnterEntryDetails(None)) when submittedEntryDetails(
          exportEntryDetails
        ) should thenGo(
          AnswerExportQuestionsRequestType(ExportQuestionsStateModel(exportEntryDetails, ExportQuestions()))
        )
      }

      "go to ExportQuestionsSummary when submitted declaration details for export and all answers are complete" in {
        given(
          EnterEntryDetails(
            entryDetailsOpt = None,
            exportQuestionsAnswersOpt = Some(completeExportQuestionsAnswers),
            fileUploadsOpt = Some(nonEmptyFileUploads)
          )
        ) when submittedEntryDetails(
          exportEntryDetails
        ) should thenGo(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportEntryDetails,
              completeExportQuestionsAnswers,
              Some(nonEmptyFileUploads)
            )
          )
        )
      }

      "go to ExportQuestionsSummary when going back and answers completed" in {
        val thisState = EnterEntryDetails(
          entryDetailsOpt = Some(exportEntryDetails),
          exportQuestionsAnswersOpt = Some(completeExportQuestionsAnswers),
          fileUploadsOpt = Some(nonEmptyFileUploads)
        )
        val summaryState = ExportQuestionsSummary(
          ExportQuestionsStateModel(
            exportEntryDetails,
            completeExportQuestionsAnswers,
            Some(nonEmptyFileUploads)
          )
        )
        given(thisState).withBreadcrumbs(summaryState) when toSummary should thenGo(summaryState)
      }

      "go to ExportQuestionsSummary when going back and answers incomplete" in {
        val thisState = EnterEntryDetails(
          entryDetailsOpt = Some(exportEntryDetails),
          exportQuestionsAnswersOpt = Some(completeExportQuestionsAnswers),
          fileUploadsOpt = None
        )
        val summaryState = ExportQuestionsSummary(
          ExportQuestionsStateModel(
            exportEntryDetails,
            completeExportQuestionsAnswers,
            None
          )
        )
        given(thisState).withBreadcrumbs(summaryState) when toSummary should thenGo(summaryState)
      }

      "go to ImportQuestionsSummary when going back and answers completed" in {
        val thisState = EnterEntryDetails(
          entryDetailsOpt = Some(importEntryDetails),
          importQuestionsAnswersOpt = Some(completeImportQuestionsAnswers),
          fileUploadsOpt = Some(nonEmptyFileUploads)
        )
        val summaryState = ImportQuestionsSummary(
          ImportQuestionsStateModel(
            importEntryDetails,
            completeImportQuestionsAnswers,
            Some(nonEmptyFileUploads)
          )
        )
        given(thisState).withBreadcrumbs(summaryState) when toSummary should thenGo(summaryState)
      }

      "go to ImportQuestionsSummary when going back and answers incomplete" in {
        val thisState = EnterEntryDetails(
          entryDetailsOpt = Some(importEntryDetails),
          importQuestionsAnswersOpt = Some(completeImportQuestionsAnswers),
          fileUploadsOpt = None
        )
        val summaryState = ImportQuestionsSummary(
          ImportQuestionsStateModel(
            importEntryDetails,
            completeImportQuestionsAnswers,
            None
          )
        )
        given(thisState).withBreadcrumbs(summaryState) when toSummary should thenGo(summaryState)
      }

      "go to AnswerImportQuestionsRequestType when submitted declaration details for import" in {
        given(EnterEntryDetails(None)) when submittedEntryDetails(
          importEntryDetails
        ) should thenGo(
          AnswerImportQuestionsRequestType(ImportQuestionsStateModel(importEntryDetails, ImportQuestions()))
        )
      }

      "go to ImportQuestionsSummary when submitted declaration details for import and all answers are complete" in {
        given(
          EnterEntryDetails(
            entryDetailsOpt = None,
            importQuestionsAnswersOpt = Some(completeImportQuestionsAnswers),
            fileUploadsOpt = Some(nonEmptyFileUploads)
          )
        ) when submittedEntryDetails(
          importEntryDetails
        ) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importEntryDetails,
              completeImportQuestionsAnswers,
              Some(nonEmptyFileUploads)
            )
          )
        )
      }

      "go back to ChooseNewOrExistingCase when chooseNewOrExistingCase and keep answers" in {
        given(
          EnterEntryDetails(
            entryDetailsOpt = Some(importEntryDetails),
            importQuestionsAnswersOpt = Some(completeImportQuestionsAnswers),
            fileUploadsOpt = Some(nonEmptyFileUploads)
          )
        ) when chooseNewOrExistingCase should thenGo(
          ChooseNewOrExistingCase(
            newOrExistingCaseOpt = Some(NewOrExistingCase.New),
            entryDetailsOpt = Some(importEntryDetails),
            importQuestionsAnswersOpt = Some(completeImportQuestionsAnswers),
            fileUploadsOpt = Some(nonEmptyFileUploads),
            continueAmendCaseJourney = false
          )
        )
      }

      "go back to ExportQuestionsMissingInformationError" in {
        val model = ExportQuestionsStateModel(exportEntryDetails, completeExportQuestionsAnswers.copy(routeType = None))
        given(
          EnterEntryDetails(Some(model.entryDetails), Some(model.exportQuestionsAnswers), None, model.fileUploadsOpt)
        )
          .when(backToExportQuestionsMissingInformationError)
          .thenGoes(ExportQuestionsMissingInformationError(model))
      }

      "go back to ImportQuestionsMissingInformationError" in {
        val model = ImportQuestionsStateModel(importEntryDetails, completeImportQuestionsAnswers.copy(routeType = None))
        given(
          EnterEntryDetails(Some(model.entryDetails), None, Some(model.importQuestionsAnswers), model.fileUploadsOpt)
        )
          .when(backToImportQuestionsMissingInformationError)
          .thenGoes(ImportQuestionsMissingInformationError(model))
      }
    }

    "at state AnswerExportQuestionsRequestType" should {
      for (requestType <- ExportRequestType.values.diff(mandatoryReasonExportRequestType)) {
        s"go to AnswerExportQuestionsRouteType when submitted request type ${ExportRequestType.keyOf(requestType).get}" in {
          given(
            AnswerExportQuestionsRequestType(ExportQuestionsStateModel(exportEntryDetails, ExportQuestions()))
          ) when submittedExportQuestionsAnswerRequestType(false)(
            requestType
          ) should thenGo(
            AnswerExportQuestionsRouteType(
              ExportQuestionsStateModel(exportEntryDetails, ExportQuestions(requestType = Some(requestType)))
            )
          )
        }

        s"go to ExportQuestionsSummary when submitted request type ${ExportRequestType.keyOf(requestType).get} and all answers are complete" in {
          given(
            AnswerExportQuestionsRequestType(
              ExportQuestionsStateModel(
                exportEntryDetails,
                completeExportQuestionsAnswers,
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedExportQuestionsAnswerRequestType(true)(
            requestType
          ) should thenGo(
            ExportQuestionsSummary(
              ExportQuestionsStateModel(
                exportEntryDetails,
                completeExportQuestionsAnswers.copy(requestType = Some(requestType)),
                Some(nonEmptyFileUploads)
              )
            )
          )
        }
      }
      for (requestType <- mandatoryReasonExportRequestType) {
        s"go to ExportQuestionsSummary if request type is changed from $requestType to non-mandatory reason request type and all answers are complete" in {
          given(
            AnswerExportQuestionsRequestType(
              ExportQuestionsStateModel(
                exportEntryDetails,
                completeExportQuestionsAnswers
                  .copy(requestType = Some(requestType), reason = Some(reasonText)),
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedExportQuestionsAnswerRequestType(true)(
            ExportRequestType.New
          ) should thenGo(
            ExportQuestionsSummary(
              ExportQuestionsStateModel(
                exportEntryDetails,
                completeExportQuestionsAnswers.copy(requestType = Some(ExportRequestType.New), reason = None),
                Some(nonEmptyFileUploads)
              )
            )
          )
        }

        s"go to ExportQuestionsSummary if request type is changed from non-mandatory to $requestType request type and all answers are complete" in {
          given(
            AnswerExportQuestionsRequestType(
              ExportQuestionsStateModel(
                exportEntryDetails,
                completeExportQuestionsAnswers
                  .copy(requestType = Some(ExportRequestType.New), reason = Some(reasonText)),
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedExportQuestionsAnswerRequestType(true)(
            requestType
          ) should thenGo(
            ExportQuestionsSummary(
              ExportQuestionsStateModel(
                exportEntryDetails,
                completeExportQuestionsAnswers
                  .copy(requestType = Some(requestType), reason = Some(reasonText)),
                Some(nonEmptyFileUploads)
              )
            )
          )
        }
      }

      "go to ExportQuestionsSummary when going back and answers completed" in {
        val thisState = AnswerExportQuestionsRequestType(
          ExportQuestionsStateModel(
            exportEntryDetails,
            completeExportQuestionsAnswers.copy(requestType = Some(ExportRequestType.New)),
            Some(nonEmptyFileUploads)
          )
        )
        val summaryState = ExportQuestionsSummary(
          ExportQuestionsStateModel(
            exportEntryDetails,
            completeExportQuestionsAnswers.copy(requestType = Some(ExportRequestType.New)),
            Some(nonEmptyFileUploads)
          )
        )
        given(thisState).withBreadcrumbs(summaryState) when toSummary should thenGo(summaryState)
      }

      "go to ExportQuestionsSummary when going back and answers incomplete" in {
        val thisState = AnswerExportQuestionsRequestType(
          ExportQuestionsStateModel(
            exportEntryDetails,
            completeExportQuestionsAnswers.copy(requestType = None),
            Some(nonEmptyFileUploads)
          )
        )
        val summaryState = ExportQuestionsSummary(
          ExportQuestionsStateModel(
            exportEntryDetails,
            completeExportQuestionsAnswers.copy(requestType = None),
            Some(nonEmptyFileUploads)
          )
        )
        given(thisState).withBreadcrumbs(summaryState) when toSummary should thenGo(summaryState)
      }

      "go to ExportQuestionsSummary when submitted request type C1601 with vessel details and all answers are complete" in {
        val vesselDetails =
          VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))

        given(
          AnswerExportQuestionsRequestType(
            ExportQuestionsStateModel(
              exportEntryDetails,
              completeExportQuestionsAnswers
                .copy(requestType = Some(ExportRequestType.C1601), vesselDetails = Some(vesselDetails)),
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedExportQuestionsAnswerRequestType(false)(
          ExportRequestType.C1602
        ) should thenGo(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportEntryDetails,
              completeExportQuestionsAnswers
                .copy(requestType = Some(ExportRequestType.C1602), vesselDetails = Some(vesselDetails)),
              Some(nonEmptyFileUploads)
            )
          )
        )
      }

      "go back to EnterEntryDetails" in {
        val model = ExportQuestionsStateModel(
          exportEntryDetails,
          completeExportQuestionsAnswers,
          None
        )
        given(AnswerExportQuestionsRequestType(model))
          .when(backToEnterEntryDetails)
          .thenGoes(EnterEntryDetails(Some(model.entryDetails), Some(model.exportQuestionsAnswers), None))
      }

    }

    "at state AnswerExportQuestionsRouteType" should {
      for (routeType <- ExportRouteType.values.filterNot(_ == mandatoryReasonExportRouteType)) {
        s"go to AnswerExportQuestionsHasPriorityGoods when submitted route ${ExportRouteType.keyOf(routeType).get}" in {
          given(
            AnswerExportQuestionsRouteType(
              ExportQuestionsStateModel(
                exportEntryDetails,
                ExportQuestions(requestType = Some(ExportRequestType.New))
              )
            )
          ) when submittedExportQuestionsAnswerRouteType(false)(
            routeType
          ) should thenGo(
            AnswerExportQuestionsHasPriorityGoods(
              ExportQuestionsStateModel(
                exportEntryDetails,
                ExportQuestions(requestType = Some(ExportRequestType.New), routeType = Some(routeType))
              )
            )
          )
        }

        s"go to ExportQuestionsSummary when submitted route ${ExportRouteType.keyOf(routeType).get} and all answers are complete and optional transport feature enabled" in {
          given(
            AnswerExportQuestionsRouteType(
              ExportQuestionsStateModel(
                exportEntryDetails,
                completeExportQuestionsAnswers,
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedExportQuestionsAnswerRouteType(true)(
            routeType
          ) should thenGo(
            ExportQuestionsSummary(
              ExportQuestionsStateModel(
                exportEntryDetails,
                completeExportQuestionsAnswers.copy(routeType = Some(routeType)),
                Some(nonEmptyFileUploads)
              )
            )
          )
        }

      }

      "go back to AnswerExportQuestionsRequestType" in {
        given(
          AnswerExportQuestionsRouteType(
            ExportQuestionsStateModel(
              exportEntryDetails,
              completeExportQuestionsAnswers,
              Some(nonEmptyFileUploads)
            )
          )
        ).when(backToAnswerExportQuestionsRequestType)
          .thenGoes(
            AnswerExportQuestionsRequestType(
              ExportQuestionsStateModel(
                exportEntryDetails,
                completeExportQuestionsAnswers,
                Some(nonEmptyFileUploads)
              )
            )
          )
      }

      s"go to ExportQuestionsSummary when submitted route type HOLD and all answers are complete" in {
        given(
          AnswerExportQuestionsRouteType(
            ExportQuestionsStateModel(
              exportEntryDetails,
              completeExportQuestionsAnswers.copy(routeType = Some(ExportRouteType.Hold)),
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedExportQuestionsAnswerRouteType(false)(
          ExportRouteType.Route1
        ) should thenGo(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportEntryDetails,
              completeExportQuestionsAnswers.copy(routeType = Some(ExportRouteType.Route1), vesselDetails = None),
              Some(nonEmptyFileUploads)
            )
          )
        )
      }
      s"go to ExportQuestionsSummary when submitted route type HOLD with vessel details and all answers are complete" in {
        val vesselDetails =
          VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))

        given(
          AnswerExportQuestionsRouteType(
            ExportQuestionsStateModel(
              exportEntryDetails,
              completeExportQuestionsAnswers
                .copy(routeType = Some(ExportRouteType.Hold), vesselDetails = Some(vesselDetails)),
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedExportQuestionsAnswerRouteType(false)(
          ExportRouteType.Hold
        ) should thenGo(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportEntryDetails,
              completeExportQuestionsAnswers
                .copy(routeType = Some(ExportRouteType.Hold), vesselDetails = Some(vesselDetails)),
              Some(nonEmptyFileUploads)
            )
          )
        )
      }
      s"go ExportQuestionsSummary if routeType is changed from mandatory reason route type to non-mandatory reason route type and all answers are complete" in {
        given(
          AnswerExportQuestionsRouteType(
            ExportQuestionsStateModel(
              exportEntryDetails,
              completeExportQuestionsAnswers
                .copy(routeType = Some(ExportRouteType.Route3), reason = Some(reasonText)),
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedExportQuestionsAnswerRouteType(false)(
          ExportRouteType.Route1
        ) should thenGo(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportEntryDetails,
              completeExportQuestionsAnswers
                .copy(routeType = Some(ExportRouteType.Route1), vesselDetails = None, reason = None),
              Some(nonEmptyFileUploads)
            )
          )
        )
      }

      s"go to AnswerExportQuestionsReason when submitted route type is ${ExportRouteType.Route3}" in {
        given(
          AnswerExportQuestionsRouteType(
            ExportQuestionsStateModel(
              exportEntryDetails,
              ExportQuestions(requestType = Some(ExportRequestType.New))
            )
          )
        ) when submittedExportQuestionsAnswerRouteType(false)(
          ExportRouteType.Route3
        ) should thenGo(
          AnswerExportQuestionsReason(
            ExportQuestionsStateModel(
              exportEntryDetails,
              ExportQuestions(requestType = Some(ExportRequestType.New), routeType = Some(ExportRouteType.Route3))
            )
          )
        )
      }

      for (
        requestType <- mandatoryReasonExportRequestType;
        routeType   <- ExportRouteType.values
      )
        s"go to AnswerExportQuestionsReason when submitted route type is $routeType and requestType is $requestType" in {
          given(
            AnswerExportQuestionsRouteType(
              ExportQuestionsStateModel(
                exportEntryDetails,
                ExportQuestions(requestType = Some(requestType))
              )
            )
          ) when submittedExportQuestionsAnswerRouteType(false)(
            routeType
          ) should thenGo(
            AnswerExportQuestionsReason(
              ExportQuestionsStateModel(
                exportEntryDetails,
                ExportQuestions(requestType = Some(requestType), routeType = Some(routeType))
              )
            )
          )
        }
    }

    "at state AnswerExportQuestionsReason" should {
      "go to AnswerExportQuestionsHasPriorityGoods" in given(
        AnswerExportQuestionsReason(
          ExportQuestionsStateModel(
            exportEntryDetails,
            ExportQuestions(
              requestType = Some(ExportRequestType.Cancellation),
              routeType = Some(ExportRouteType.Route1)
            )
          )
        )
      )
        .when(submittedExportQuestionsAnswerReason(reasonText))
        .thenGoes(
          AnswerExportQuestionsHasPriorityGoods(
            ExportQuestionsStateModel(
              exportEntryDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.Cancellation),
                routeType = Some(ExportRouteType.Route1),
                reason = Some(reasonText)
              )
            )
          )
        )

      "go to ExportQuestionsSummary when reason text is entered all answers are complete" in {
        val answers = completeExportQuestionsAnswers
          .copy(reason = Some(reasonText))
        given(
          AnswerExportQuestionsReason(
            ExportQuestionsStateModel(exportEntryDetails, answers, Some(nonEmptyFileUploads))
          )
        ) when submittedExportQuestionsAnswerReason(reasonText) should thenGo(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportEntryDetails,
              answers,
              Some(nonEmptyFileUploads)
            )
          )
        )
      }

      "go back to AnswerExportQuestionsRouteType" in {
        val answers = completeExportQuestionsAnswers
          .copy(reason = Some(reasonText))
        given(
          AnswerExportQuestionsReason(
            ExportQuestionsStateModel(exportEntryDetails, answers, Some(nonEmptyFileUploads))
          )
        ).when(backToAnswerExportQuestionsRouteType)
          .thenGoes(
            AnswerExportQuestionsRouteType(
              ExportQuestionsStateModel(exportEntryDetails, answers, Some(nonEmptyFileUploads))
            )
          )
      }
    }

    "at state AnswerExportQuestionsHasPriorityGoods" should {
      "go to AnswerExportQuestionsWhichPriorityGoods when selected YES" in {
        given(
          AnswerExportQuestionsHasPriorityGoods(
            ExportQuestionsStateModel(
              exportEntryDetails,
              ExportQuestions(requestType = Some(ExportRequestType.New), routeType = Some(ExportRouteType.Route1))
            )
          )
        ) when submittedExportQuestionsAnswerHasPriorityGoods(true) should thenGo(
          AnswerExportQuestionsWhichPriorityGoods(
            ExportQuestionsStateModel(
              exportEntryDetails,
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
              exportEntryDetails,
              ExportQuestions(requestType = Some(ExportRequestType.New), routeType = Some(ExportRouteType.Route1))
            )
          )
        ) when submittedExportQuestionsAnswerHasPriorityGoods(false) should thenGo(
          AnswerExportQuestionsFreightType(
            ExportQuestionsStateModel(
              exportEntryDetails,
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
            ExportQuestionsStateModel(exportEntryDetails, answers)
          )
        ) when submittedExportQuestionsAnswerHasPriorityGoods(true) should thenGo(
          AnswerExportQuestionsWhichPriorityGoods(
            ExportQuestionsStateModel(
              exportEntryDetails,
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
            ExportQuestionsStateModel(exportEntryDetails, answers)
          )
        ) when submittedExportQuestionsAnswerHasPriorityGoods(true) should thenGo(
          AnswerExportQuestionsWhichPriorityGoods(
            ExportQuestionsStateModel(
              exportEntryDetails,
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
            ExportQuestionsStateModel(exportEntryDetails, answers, Some(nonEmptyFileUploads))
          )
        ) when submittedExportQuestionsAnswerHasPriorityGoods(true) should thenGo(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportEntryDetails,
              answers,
              Some(nonEmptyFileUploads)
            )
          )
        )
      }

      "go back to AnswerExportQuestionsReason when mandatory" in {
        val model = ExportQuestionsStateModel(
          exportEntryDetails,
          completeExportQuestionsAnswers.copy(requestType = Some(ExportRequestType.Cancellation)),
          Some(nonEmptyFileUploads)
        )
        given(AnswerExportQuestionsHasPriorityGoods(model))
          .when(backToAnswerExportQuestionsReason)
          .thenGoes(AnswerExportQuestionsReason(model))
      }

      "stay when backToAnswerExportQuestionsReason and not mandatory" in {
        val model = ExportQuestionsStateModel(
          exportEntryDetails,
          completeExportQuestionsAnswers.copy(requestType = Some(ExportRequestType.New)),
          Some(nonEmptyFileUploads)
        )
        given(AnswerExportQuestionsHasPriorityGoods(model))
          .when(backToAnswerExportQuestionsReason)
          .thenNoChange
      }
    }

    "at state AnswerExportQuestionsWhichPriorityGoods" should {
      for (priorityGood <- ExportPriorityGoods.values) {
        s"go to AnswerExportQuestionsFreightType when submitted priority good ${ExportPriorityGoods.keyOf(priorityGood).get}" in {
          given(
            AnswerExportQuestionsWhichPriorityGoods(
              ExportQuestionsStateModel(
                exportEntryDetails,
                ExportQuestions(requestType = Some(ExportRequestType.C1601), routeType = Some(ExportRouteType.Route2))
              )
            )
          ) when submittedExportQuestionsAnswerWhichPriorityGoods(
            priorityGood
          ) should thenGo(
            AnswerExportQuestionsFreightType(
              ExportQuestionsStateModel(
                exportEntryDetails,
                ExportQuestions(
                  requestType = Some(ExportRequestType.C1601),
                  routeType = Some(ExportRouteType.Route2),
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
                exportEntryDetails,
                completeExportQuestionsAnswers,
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedExportQuestionsAnswerWhichPriorityGoods(
            priorityGood
          ) should thenGo(
            ExportQuestionsSummary(
              ExportQuestionsStateModel(
                exportEntryDetails,
                completeExportQuestionsAnswers.copy(priorityGoods = Some(priorityGood)),
                Some(nonEmptyFileUploads)
              )
            )
          )
        }

        s"go back to AnswerExportQuestionsHasPriorityGoods with ${ExportPriorityGoods.keyOf(priorityGood).get}" in {
          val model = ExportQuestionsStateModel(
            exportEntryDetails,
            completeExportQuestionsAnswers.copy(priorityGoods = Some(priorityGood)),
            Some(nonEmptyFileUploads)
          )
          given(AnswerExportQuestionsWhichPriorityGoods(model))
            .when(backToAnswerExportQuestionsHasPriorityGoods)
            .thenGoes(AnswerExportQuestionsHasPriorityGoods(model))
        }
      }
    }

    "at state AnswerExportQuestionsFreightType" should {
      for (
        freightType <- ExportFreightType.values;
        requestType <- ExportRequestType.values
                         .diff(mandatoryVesselDetailsRequestTypes)
                         .diff(mandatoryReasonExportRequestType)
      ) {
        s"go to AnswerExportQuestionsOptionalVesselInfo when submitted freight type ${ExportFreightType.keyOf(freightType).get} and requestType is ${ExportRequestType
            .keyOf(requestType)
            .get} and require optional transport feature is turned on" in {
          given(
            AnswerExportQuestionsFreightType(
              ExportQuestionsStateModel(
                exportEntryDetails,
                ExportQuestions(
                  requestType = Some(requestType),
                  routeType = Some(ExportRouteType.Route2),
                  priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks)
                )
              )
            )
          ) when submittedExportQuestionsAnswerFreightType(true)(
            freightType
          ) should thenGo(
            AnswerExportQuestionsOptionalVesselInfo(
              ExportQuestionsStateModel(
                exportEntryDetails,
                ExportQuestions(
                  requestType = Some(requestType),
                  routeType = Some(ExportRouteType.Route2),
                  priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
                  freightType = Some(freightType)
                )
              )
            )
          )
        }
        s"go to AnswerExportQuestionsContactInfo when submitted freight type ${ExportFreightType.keyOf(freightType).get} and requestType is ${ExportRequestType
            .keyOf(requestType)
            .get} and optional transport feature is turned off" in {
          given(
            AnswerExportQuestionsFreightType(
              ExportQuestionsStateModel(
                exportEntryDetails,
                ExportQuestions(
                  requestType = Some(requestType),
                  routeType = Some(ExportRouteType.Route2),
                  priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks)
                )
              )
            )
          ) when submittedExportQuestionsAnswerFreightType(false)(
            freightType
          ) should thenGo(
            AnswerExportQuestionsContactInfo(
              ExportQuestionsStateModel(
                exportEntryDetails,
                ExportQuestions(
                  requestType = Some(requestType),
                  routeType = Some(ExportRouteType.Route2),
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
                exportEntryDetails,
                completeExportQuestionsAnswers.copy(requestType = Some(requestType)),
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedExportQuestionsAnswerFreightType(false)(
            freightType
          ) should thenGo(
            ExportQuestionsSummary(
              ExportQuestionsStateModel(
                exportEntryDetails,
                completeExportQuestionsAnswers.copy(freightType = Some(freightType), requestType = Some(requestType)),
                Some(nonEmptyFileUploads)
              )
            )
          )
        }
        s"go back to AnswerExportQuestionsWhichPriorityGoods with ${ExportFreightType.keyOf(freightType).get} and ${ExportRequestType.keyOf(requestType).get}" in {
          val model = ExportQuestionsStateModel(
            exportEntryDetails,
            completeExportQuestionsAnswers.copy(requestType = Some(requestType), freightType = Some(freightType)),
            Some(nonEmptyFileUploads)
          )
          given(AnswerExportQuestionsFreightType(model))
            .when(backToAnswerExportQuestionsWhichPriorityGoods)
            .thenGoes(AnswerExportQuestionsWhichPriorityGoods(model))
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
                exportEntryDetails,
                ExportQuestions(
                  requestType = Some(requestType),
                  routeType = Some(ExportRouteType.Route2),
                  priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks)
                )
              )
            )
          ) when submittedExportQuestionsAnswerFreightType(false)(
            freightType
          ) should thenGo(
            AnswerExportQuestionsMandatoryVesselInfo(
              ExportQuestionsStateModel(
                exportEntryDetails,
                ExportQuestions(
                  requestType = Some(requestType),
                  routeType = Some(ExportRouteType.Route2),
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
                exportEntryDetails,
                completeExportQuestionsAnswers.copy(requestType = Some(requestType)),
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedExportQuestionsAnswerFreightType(false)(
            freightType
          ) should thenGo(
            ExportQuestionsSummary(
              ExportQuestionsStateModel(
                exportEntryDetails,
                completeExportQuestionsAnswers.copy(freightType = Some(freightType), requestType = Some(requestType)),
                Some(nonEmptyFileUploads)
              )
            )
          )
        }
      }

      for (
        freightType <- ExportFreightType.values;
        requestType <- ExportRequestType.values.diff(mandatoryReasonExportRequestType)
      ) {
        s"go to AnswerExportQuestionsMandatoryVesselInfo when submitted freight type ${ExportFreightType.keyOf(freightType).get} regardless of request type ${ExportRequestType
            .keyOf(requestType)
            .get} when route is Hold" in {
          given(
            AnswerExportQuestionsFreightType(
              ExportQuestionsStateModel(
                exportEntryDetails,
                ExportQuestions(
                  requestType = Some(requestType),
                  routeType = Some(ExportRouteType.Hold),
                  priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks)
                )
              )
            )
          ) when submittedExportQuestionsAnswerFreightType(false)(
            freightType
          ) should thenGo(
            AnswerExportQuestionsMandatoryVesselInfo(
              ExportQuestionsStateModel(
                exportEntryDetails,
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
                exportEntryDetails,
                completeExportQuestionsAnswers.copy(
                  requestType = Some(requestType),
                  routeType = Some(ExportRouteType.Hold)
                ),
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedExportQuestionsAnswerFreightType(false)(
            freightType
          ) should thenGo(
            ExportQuestionsSummary(
              ExportQuestionsStateModel(
                exportEntryDetails,
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
              exportEntryDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.C1601),
                routeType = Some(ExportRouteType.Route2),
                priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ExportFreightType.Air)
              )
            )
          )
        ) when submittedExportQuestionsMandatoryVesselDetails(
          VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))
        ) should thenGo(
          AnswerExportQuestionsContactInfo(
            ExportQuestionsStateModel(
              exportEntryDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.C1601),
                routeType = Some(ExportRouteType.Route2),
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
              exportEntryDetails,
              completeExportQuestionsAnswers,
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedExportQuestionsMandatoryVesselDetails(vesselDetails) should thenGo(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportEntryDetails,
              completeExportQuestionsAnswers.copy(vesselDetails = Some(vesselDetails)),
              Some(nonEmptyFileUploads)
            )
          )
        )
      }

      "stay when submitted partial vessel details" in {
        given(
          AnswerExportQuestionsMandatoryVesselInfo(
            ExportQuestionsStateModel(
              exportEntryDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.C1601),
                routeType = Some(ExportRouteType.Route2),
                priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ExportFreightType.Air)
              )
            )
          )
        )
          .when(
            submittedExportQuestionsMandatoryVesselDetails(
              VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), None)
            )
          )
          .thenNoChange
      }

      "go back to AnswerExportQuestionsFreightType" in {
        val model = ExportQuestionsStateModel(
          exportEntryDetails,
          ExportQuestions(
            requestType = Some(ExportRequestType.C1601),
            routeType = Some(ExportRouteType.Route2),
            priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
            freightType = Some(ExportFreightType.Air)
          )
        )
        given(AnswerExportQuestionsMandatoryVesselInfo(model))
          .when(backToAnswerExportQuestionsFreightType)
          .thenGoes(AnswerExportQuestionsFreightType(model))
      }
    }

    "at state AnswerExportQuestionsOptionalVesselInfo" should {
      "go to AnswerExportQuestionsContactInfo when submitted required vessel details" in {
        given(
          AnswerExportQuestionsOptionalVesselInfo(
            ExportQuestionsStateModel(
              exportEntryDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route2),
                priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ExportFreightType.Air)
              )
            )
          )
        ) when submittedExportQuestionsOptionalVesselDetails(
          VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))
        ) should thenGo(
          AnswerExportQuestionsContactInfo(
            ExportQuestionsStateModel(
              exportEntryDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route2),
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
            exportEntryDetails,
            ExportQuestions(
              requestType = Some(ExportRequestType.C1601),
              routeType = Some(ExportRouteType.Hold),
              priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
              freightType = Some(ExportFreightType.Air)
            )
          )
        )
        given(answerMandatoryVesselInfo) when backToAnswerExportQuestionsVesselInfo should thenGo(
          answerMandatoryVesselInfo
        )
      }

      "go to ExportQuestionsSummary when submitted required vessel details and all answers are complete" in {
        val vesselDetails =
          VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))
        given(
          AnswerExportQuestionsOptionalVesselInfo(
            ExportQuestionsStateModel(
              exportEntryDetails,
              completeExportQuestionsAnswers,
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedExportQuestionsOptionalVesselDetails(vesselDetails) should thenGo(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportEntryDetails,
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
              exportEntryDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route2),
                priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ExportFreightType.Air)
              )
            )
          )
        ) when submittedExportQuestionsOptionalVesselDetails(
          VesselDetails()
        ) should thenGo(
          AnswerExportQuestionsContactInfo(
            ExportQuestionsStateModel(
              exportEntryDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route2),
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
              exportEntryDetails,
              completeExportQuestionsAnswers,
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedExportQuestionsOptionalVesselDetails(vesselDetails) should thenGo(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportEntryDetails,
              completeExportQuestionsAnswers.copy(vesselDetails = Some(vesselDetails)),
              Some(nonEmptyFileUploads)
            )
          )
        )
      }

      "go back to AnswerExportQuestionsFreightType" in {
        val model = ExportQuestionsStateModel(
          exportEntryDetails,
          ExportQuestions(
            requestType = Some(ExportRequestType.C1601),
            routeType = Some(ExportRouteType.Route2),
            priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
            freightType = Some(ExportFreightType.Air)
          )
        )
        given(AnswerExportQuestionsOptionalVesselInfo(model))
          .when(backToAnswerExportQuestionsFreightType)
          .thenGoes(AnswerExportQuestionsFreightType(model))
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
        val upscanRequest = (nonce: String) =>
          UpscanInitiateRequest(
            "https://foo.bar/callback",
            Some("https://foo.bar/success"),
            Some("https://foo.bar/failure"),
            Some(10 * 1024 * 1024)
          )
        given(
          AnswerExportQuestionsContactInfo(
            ExportQuestionsStateModel(
              exportEntryDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route2),
                priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ExportFreightType.Air),
                vesselDetails =
                  Some(VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00"))))
              )
            )
          )
        ) when submittedExportQuestionsContactInfo(uploadMultipleFiles = false)(upscanRequest)(mockUpscanInitiate)(
          ExportContactInfo(contactEmail = "name@somewhere.com")
        ) should thenGo(
          UploadFile(
            hostData = FileUploadHostData(
              exportEntryDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route2),
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
            fileUploads = FileUploads(files = Seq(FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo-bar-ref")))
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
        val upscanRequest = (nonce: String) =>
          UpscanInitiateRequest(
            "https://foo.bar/callback",
            Some("https://foo.bar/success"),
            Some("https://foo.bar/failure"),
            Some(10 * 1024 * 1024)
          )
        given(
          AnswerExportQuestionsContactInfo(
            ExportQuestionsStateModel(
              exportEntryDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route2),
                priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ExportFreightType.Air),
                vesselDetails =
                  Some(VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00"))))
              ),
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedExportQuestionsContactInfo(uploadMultipleFiles = false)(upscanRequest)(mockUpscanInitiate)(
          ExportContactInfo(contactEmail = "name@somewhere.com")
        ) should thenGo(
          FileUploaded(
            hostData = FileUploadHostData(
              exportEntryDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route2),
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

      "go to UploadMultipleFiles when submitted required contact details and holds already some file uploads" in {
        val mockUpscanInitiate: UpscanInitiateRequest => Future[UpscanInitiateResponse] = request =>
          Future.successful(
            UpscanInitiateResponse(
              reference = "foo-bar-ref",
              uploadRequest =
                UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> request.callbackUrl))
            )
          )
        val upscanRequest = (nonce: String) =>
          UpscanInitiateRequest(
            "https://foo.bar/callback",
            Some("https://foo.bar/success"),
            Some("https://foo.bar/failure"),
            Some(10 * 1024 * 1024)
          )
        given(
          AnswerExportQuestionsContactInfo(
            ExportQuestionsStateModel(
              exportEntryDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route2),
                priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ExportFreightType.Air),
                vesselDetails =
                  Some(VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00"))))
              ),
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedExportQuestionsContactInfo(uploadMultipleFiles = true)(upscanRequest)(mockUpscanInitiate)(
          ExportContactInfo(contactEmail = "name@somewhere.com")
        ) should thenGo(
          UploadMultipleFiles(
            hostData = FileUploadHostData(
              exportEntryDetails,
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route2),
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

      "go back to AnswerExportQuestionsMandatoryVesselInfo" in {
        val model = ExportQuestionsStateModel(
          exportEntryDetails,
          ExportQuestions(
            requestType = Some(ExportRequestType.C1601),
            routeType = Some(ExportRouteType.Route2),
            priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
            freightType = Some(ExportFreightType.Air),
            vesselDetails =
              Some(VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00"))))
          )
        )
        given(AnswerExportQuestionsContactInfo(model))
          .when(backToAnswerExportQuestionsVesselInfo)
          .thenGoes(AnswerExportQuestionsMandatoryVesselInfo(model))
      }

      "go back to AnswerExportQuestionsOptionalVesselInfo" in {
        val model = ExportQuestionsStateModel(
          exportEntryDetails,
          ExportQuestions(
            requestType = Some(ExportRequestType.New),
            routeType = Some(ExportRouteType.Route2),
            priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
            freightType = Some(ExportFreightType.RORO)
          )
        )
        given(AnswerExportQuestionsContactInfo(model))
          .when(backToAnswerExportQuestionsVesselInfo)
          .thenGoes(AnswerExportQuestionsOptionalVesselInfo(model))
      }
    }

    "at state AnswerImportQuestionsRequestType" should {
      for (requestType <- ImportRequestType.values.filterNot(_ == mandatoryReasonImportRequestType)) {
        s"go to AnswerImportQuestionsRequestType when submitted request type ${ImportRequestType.keyOf(requestType).get}" in {
          given(
            AnswerImportQuestionsRequestType(ImportQuestionsStateModel(importEntryDetails, ImportQuestions()))
          ) when submittedImportQuestionsAnswersRequestType(
            requestType
          ) should thenGo(
            AnswerImportQuestionsRouteType(
              ImportQuestionsStateModel(importEntryDetails, ImportQuestions(requestType = Some(requestType)))
            )
          )
        }

        s"go to ImportQuestionsSummary when submitted request type ${ImportRequestType.keyOf(requestType).get} and all answers are complete" in {
          given(
            AnswerImportQuestionsRequestType(
              ImportQuestionsStateModel(
                importEntryDetails,
                completeImportQuestionsAnswers,
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedImportQuestionsAnswersRequestType(
            requestType
          ) should thenGo(
            ImportQuestionsSummary(
              ImportQuestionsStateModel(
                importEntryDetails,
                completeImportQuestionsAnswers.copy(requestType = Some(requestType)),
                Some(nonEmptyFileUploads)
              )
            )
          )
        }

        s"go to ImportQuestionsSummary if request type is changed from mandatory reason request type to non-mandatory reason request type and all answers are complete" in {
          given(
            AnswerImportQuestionsRequestType(
              ImportQuestionsStateModel(
                importEntryDetails,
                completeImportQuestionsAnswers
                  .copy(requestType = Some(ImportRequestType.Cancellation), reason = Some(reasonText)),
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedImportQuestionsAnswersRequestType(
            ImportRequestType.New
          ) should thenGo(
            ImportQuestionsSummary(
              ImportQuestionsStateModel(
                importEntryDetails,
                completeImportQuestionsAnswers.copy(requestType = Some(ImportRequestType.New), reason = None),
                Some(nonEmptyFileUploads)
              )
            )
          )
        }

        "go to ImportQuestionsSummary if request type is changed from non-mandatory reason request type to mandatory reason request type and all answers are complete" in {
          given(
            AnswerImportQuestionsRequestType(
              ImportQuestionsStateModel(
                importEntryDetails,
                completeImportQuestionsAnswers
                  .copy(requestType = Some(ImportRequestType.New), reason = Some(reasonText)),
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedImportQuestionsAnswersRequestType(
            ImportRequestType.Cancellation
          ) should thenGo(
            ImportQuestionsSummary(
              ImportQuestionsStateModel(
                importEntryDetails,
                completeImportQuestionsAnswers
                  .copy(requestType = Some(ImportRequestType.Cancellation), reason = Some(reasonText)),
                Some(nonEmptyFileUploads)
              )
            )
          )
        }
      }

      "go to ImportQuestionsSummary when going back and answers completed" in {
        val thisState = AnswerImportQuestionsRequestType(
          ImportQuestionsStateModel(
            importEntryDetails,
            completeImportQuestionsAnswers.copy(requestType = Some(ImportRequestType.New)),
            Some(nonEmptyFileUploads)
          )
        )
        val summaryState = ImportQuestionsSummary(
          ImportQuestionsStateModel(
            importEntryDetails,
            completeImportQuestionsAnswers.copy(requestType = Some(ImportRequestType.New)),
            Some(nonEmptyFileUploads)
          )
        )
        given(thisState).withBreadcrumbs(summaryState) when toSummary should thenGo(summaryState)
      }

      "go to ImportQuestionsSummary when going back and answers incomplete" in {
        val thisState = AnswerImportQuestionsRequestType(
          ImportQuestionsStateModel(
            importEntryDetails,
            completeImportQuestionsAnswers.copy(requestType = None),
            Some(nonEmptyFileUploads)
          )
        )
        val summaryState = ImportQuestionsSummary(
          ImportQuestionsStateModel(
            importEntryDetails,
            completeImportQuestionsAnswers.copy(requestType = None),
            Some(nonEmptyFileUploads)
          )
        )
        given(thisState).withBreadcrumbs(summaryState) when toSummary should thenGo(summaryState)
      }

      "go back to EnterEntryDetails" in {
        val model = ImportQuestionsStateModel(
          importEntryDetails,
          completeImportQuestionsAnswers,
          Some(nonEmptyFileUploads)
        )
        given(AnswerImportQuestionsRequestType(model))
          .when(backToEnterEntryDetails)
          .thenGoes(
            EnterEntryDetails(
              Some(model.entryDetails),
              None,
              Some(model.importQuestionsAnswers),
              Some(nonEmptyFileUploads)
            )
          )
      }

    }

    "at state AnswerImportQuestionsRouteType" should {
      for (routeType <- ImportRouteType.values.filterNot(_ == mandatoryReasonImportRouteType)) {
        s"go to AnswerImportQuestionsHasPriorityGoods when submitted route ${ImportRouteType.keyOf(routeType).get}" in {
          given(
            AnswerImportQuestionsRouteType(
              ImportQuestionsStateModel(
                importEntryDetails,
                ImportQuestions(requestType = Some(ImportRequestType.New))
              )
            )
          ) when submittedImportQuestionsAnswerRouteType(true)(
            routeType
          ) should thenGo(
            AnswerImportQuestionsHasPriorityGoods(
              ImportQuestionsStateModel(
                importEntryDetails,
                ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(routeType))
              )
            )
          )
        }

        s"go to ImportQuestionsSummary when submitted route ${ImportRouteType.keyOf(routeType).get} and all answers are complete and optional transport page is enabled" in {
          given(
            AnswerImportQuestionsRouteType(
              ImportQuestionsStateModel(
                importEntryDetails,
                completeImportQuestionsAnswers,
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedImportQuestionsAnswerRouteType(true)(
            routeType
          ) should thenGo(
            ImportQuestionsSummary(
              ImportQuestionsStateModel(
                importEntryDetails,
                completeImportQuestionsAnswers.copy(routeType = Some(routeType)),
                Some(nonEmptyFileUploads)
              )
            )
          )
        }

        s"go back to AnswerImportQuestionsRequestType with ${ImportRouteType.keyOf(routeType).get}" in {
          val model = ImportQuestionsStateModel(
            importEntryDetails,
            completeImportQuestionsAnswers.copy(routeType = Some(routeType)),
            Some(nonEmptyFileUploads)
          )
          given(AnswerImportQuestionsRouteType(model))
            .when(backToAnswerImportQuestionsRequestType)
            .thenGoes(AnswerImportQuestionsRequestType(model))
        }
      }
      "go to ImportQuestionsSummary when submitted route type is HOLD and all answers are complete" in {
        given(
          AnswerImportQuestionsRouteType(
            ImportQuestionsStateModel(
              importEntryDetails,
              completeImportQuestionsAnswers.copy(routeType = Some(ImportRouteType.Hold)),
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedImportQuestionsAnswerRouteType(false)(
          ImportRouteType.Route1
        ) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importEntryDetails,
              completeImportQuestionsAnswers.copy(routeType = Some(ImportRouteType.Route1), vesselDetails = None),
              Some(nonEmptyFileUploads)
            )
          )
        )
      }
      "go to ImportQuestionsSummary if routeType is changed from mandatory reason route type to non-mandatory reason route type and all answers are complete" in {
        given(
          AnswerImportQuestionsRouteType(
            ImportQuestionsStateModel(
              importEntryDetails,
              completeImportQuestionsAnswers
                .copy(routeType = Some(ImportRouteType.Route3), reason = Some(reasonText)),
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedImportQuestionsAnswerRouteType(false)(
          ImportRouteType.Route1
        ) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importEntryDetails,
              completeImportQuestionsAnswers
                .copy(routeType = Some(ImportRouteType.Route1), vesselDetails = None, reason = None),
              Some(nonEmptyFileUploads)
            )
          )
        )
      }

      s"go to AnswerImportQuestionsReason when submitted route type is ${ImportRouteType.Route3}" in {
        given(
          AnswerImportQuestionsRouteType(
            ImportQuestionsStateModel(
              importEntryDetails,
              ImportQuestions(requestType = Some(ImportRequestType.New))
            )
          )
        ) when submittedImportQuestionsAnswerRouteType(false)(
          ImportRouteType.Route3
        ) should thenGo(
          AnswerImportQuestionsReason(
            ImportQuestionsStateModel(
              importEntryDetails,
              ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route3))
            )
          )
        )
      }

      for (routeType <- ImportRouteType.values)
        s"go to AnswerImportQuestionsReason when submitted route type is $routeType and requestType is ${ImportRequestType.Cancellation}" in {
          given(
            AnswerImportQuestionsRouteType(
              ImportQuestionsStateModel(
                importEntryDetails,
                ImportQuestions(requestType = Some(ImportRequestType.Cancellation))
              )
            )
          ) when submittedImportQuestionsAnswerRouteType(false)(
            routeType
          ) should thenGo(
            AnswerImportQuestionsReason(
              ImportQuestionsStateModel(
                importEntryDetails,
                ImportQuestions(requestType = Some(ImportRequestType.Cancellation), routeType = Some(routeType))
              )
            )
          )
        }

    }

    "at state AnswerImportQuestionsReason" should {
      "go to AnswerImportQuestionsHasPriorityGoods" in {
        given(
          AnswerImportQuestionsReason(
            ImportQuestionsStateModel(
              importEntryDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.Cancellation),
                routeType = Some(ImportRouteType.Route1)
              )
            )
          )
        ) when submittedImportQuestionsAnswerReason(reasonText) should thenGo(
          AnswerImportQuestionsHasPriorityGoods(
            ImportQuestionsStateModel(
              importEntryDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.Cancellation),
                routeType = Some(ImportRouteType.Route1),
                reason = Some(reasonText)
              )
            )
          )
        )
      }

      "go to ImportQuestionsSummary when reason text is entered all answers are complete" in {
        val answers = completeImportQuestionsAnswers
          .copy(reason = Some(reasonText))
        given(
          AnswerImportQuestionsReason(
            ImportQuestionsStateModel(importEntryDetails, answers, Some(nonEmptyFileUploads))
          )
        ) when submittedImportQuestionsAnswerReason(reasonText) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importEntryDetails,
              answers,
              Some(nonEmptyFileUploads)
            )
          )
        )
      }

      "go to ImportQuestionsSummary when submitted route type HOLD with vessel details and all answers are complete" in {
        val vesselDetails =
          VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))

        given(
          AnswerImportQuestionsRouteType(
            ImportQuestionsStateModel(
              importEntryDetails,
              completeImportQuestionsAnswers
                .copy(routeType = Some(ImportRouteType.Hold), vesselDetails = Some(vesselDetails)),
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedImportQuestionsAnswerRouteType(false)(
          ImportRouteType.Hold
        ) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importEntryDetails,
              completeImportQuestionsAnswers
                .copy(routeType = Some(ImportRouteType.Hold), vesselDetails = Some(vesselDetails)),
              Some(nonEmptyFileUploads)
            )
          )
        )
      }

      "go back to AnswerImportQuestionsRouteType" in {
        val model = ImportQuestionsStateModel(
          importEntryDetails,
          ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route3))
        )
        given(AnswerImportQuestionsReason(model))
          .when(backToAnswerImportQuestionsRouteType)
          .thenGoes(AnswerImportQuestionsRouteType(model))
      }
    }

    "at state AnswerImportQuestionsHasPriorityGoods" should {
      "go to AnswerImportQuestionsWhichPriorityGoods when selected YES" in {
        given(
          AnswerImportQuestionsHasPriorityGoods(
            ImportQuestionsStateModel(
              importEntryDetails,
              ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route1))
            )
          )
        ) when submittedImportQuestionsAnswerHasPriorityGoods(true) should thenGo(
          AnswerImportQuestionsWhichPriorityGoods(
            ImportQuestionsStateModel(
              importEntryDetails,
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
              importEntryDetails,
              ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route1))
            )
          )
        ) when submittedImportQuestionsAnswerHasPriorityGoods(false) should thenGo(
          AnswerImportQuestionsALVS(
            ImportQuestionsStateModel(
              importEntryDetails,
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
            ImportQuestionsStateModel(importEntryDetails, answers)
          )
        ) when submittedImportQuestionsAnswerHasPriorityGoods(true) should thenGo(
          AnswerImportQuestionsWhichPriorityGoods(
            ImportQuestionsStateModel(
              importEntryDetails,
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
            ImportQuestionsStateModel(importEntryDetails, answers)
          )
        ) when submittedImportQuestionsAnswerHasPriorityGoods(true) should thenGo(
          AnswerImportQuestionsWhichPriorityGoods(
            ImportQuestionsStateModel(
              importEntryDetails,
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
            ImportQuestionsStateModel(importEntryDetails, answers, Some(nonEmptyFileUploads))
          )
        ) when submittedImportQuestionsAnswerHasPriorityGoods(true) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importEntryDetails,
              answers,
              Some(nonEmptyFileUploads)
            )
          )
        )
      }

      "go back to AnswerImportQuestionsReason if reason mandatory" in {
        val model = ImportQuestionsStateModel(
          importEntryDetails,
          ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route3))
        )
        given(AnswerImportQuestionsHasPriorityGoods(model))
          .when(backToAnswerImportQuestionsReason)
          .thenGoes(AnswerImportQuestionsReason(model))
      }

      "stay when backToAnswerImportQuestionsReason and reason not mandatory" in {
        val model = ImportQuestionsStateModel(
          importEntryDetails,
          ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route1))
        )
        given(AnswerImportQuestionsHasPriorityGoods(model))
          .when(backToAnswerImportQuestionsReason)
          .thenNoChange
      }
    }

    "at state AnswerImportQuestionsWhichPriorityGoods" should {
      for (priorityGoods <- ImportPriorityGoods.values) {
        s"go to AnswerImportQuestionsALVS when submitted priority good ${ImportPriorityGoods.keyOf(priorityGoods).get}" in {
          given(
            AnswerImportQuestionsWhichPriorityGoods(
              ImportQuestionsStateModel(
                importEntryDetails,
                ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route2))
              )
            )
          ) when submittedImportQuestionsAnswerWhichPriorityGoods(
            priorityGoods
          ) should thenGo(
            AnswerImportQuestionsALVS(
              ImportQuestionsStateModel(
                importEntryDetails,
                ImportQuestions(
                  requestType = Some(ImportRequestType.New),
                  routeType = Some(ImportRouteType.Route2),
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
                importEntryDetails,
                completeImportQuestionsAnswers,
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedImportQuestionsAnswerWhichPriorityGoods(
            priorityGoods
          ) should thenGo(
            ImportQuestionsSummary(
              ImportQuestionsStateModel(
                importEntryDetails,
                completeImportQuestionsAnswers.copy(priorityGoods = Some(priorityGoods)),
                Some(nonEmptyFileUploads)
              )
            )
          )
        }
      }

      "go back to AnswerImportQuestionsHasPriorityGoods" in {
        val model = ImportQuestionsStateModel(
          importEntryDetails,
          completeImportQuestionsAnswers
            .copy(hasPriorityGoods = Some(true), priorityGoods = None)
        )
        given(AnswerImportQuestionsWhichPriorityGoods(model))
          .when(backToAnswerImportQuestionsHasPriorityGoods)
          .thenGoes(AnswerImportQuestionsHasPriorityGoods(model))
      }
    }

    "at state AnswerImportQuestionsALVS" should {
      "go to AnswerImportQuestionsFreightType when selected YES" in {
        given(
          AnswerImportQuestionsALVS(
            ImportQuestionsStateModel(
              importEntryDetails,
              ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route1))
            )
          )
        ) when submittedImportQuestionsAnswerHasALVS(true) should thenGo(
          AnswerImportQuestionsFreightType(
            ImportQuestionsStateModel(
              importEntryDetails,
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
              importEntryDetails,
              completeImportQuestionsAnswers,
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedImportQuestionsAnswerHasALVS(true) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importEntryDetails,
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
              importEntryDetails,
              ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route1))
            )
          )
        ) when submittedImportQuestionsAnswerHasALVS(false) should thenGo(
          AnswerImportQuestionsFreightType(
            ImportQuestionsStateModel(
              importEntryDetails,
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
              importEntryDetails,
              completeImportQuestionsAnswers,
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedImportQuestionsAnswerHasALVS(false) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importEntryDetails,
              completeImportQuestionsAnswers.copy(hasALVS = Some(false)),
              Some(nonEmptyFileUploads)
            )
          )
        )
      }

      "go back to AnswerImportQuestionsWhichPriorityGoods" in {
        val model = ImportQuestionsStateModel(
          importEntryDetails,
          completeImportQuestionsAnswers
        )
        given(AnswerImportQuestionsALVS(model))
          .when(backToAnswerImportQuestionsWhichPriorityGoods)
          .thenGoes(AnswerImportQuestionsWhichPriorityGoods(model))
      }
    }

    "at state AnswerImportQuestionsMandatoryVesselInfo" should {
      "go to AnswerImportQuestionsContactInfo when submitted all vessel details" in {
        given(
          AnswerImportQuestionsMandatoryVesselInfo(
            ImportQuestionsStateModel(
              importEntryDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route2),
                priorityGoods = Some(ImportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ImportFreightType.Air)
              )
            )
          )
        ) when submittedImportQuestionsMandatoryVesselDetails(
          VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))
        ) should thenGo(
          AnswerImportQuestionsContactInfo(
            ImportQuestionsStateModel(
              importEntryDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route2),
                priorityGoods = Some(ImportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ImportFreightType.Air),
                vesselDetails =
                  Some(VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00"))))
              )
            )
          )
        )
      }

      "go to AnswerImportQuestionsMandatoryVesselInfo when mandatory vessel details are submitted but user tries to redirect to optional vessel info" in {
        val answerMandatoryVesselInfo = AnswerImportQuestionsMandatoryVesselInfo(
          ImportQuestionsStateModel(
            importEntryDetails,
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Hold),
              freightType = Some(Air),
              hasALVS = Some(false)
            )
          )
        )
        given(answerMandatoryVesselInfo) when backToAnswerImportQuestionsVesselInfo should thenGo(
          answerMandatoryVesselInfo
        )
      }

      "go back to AnswerImportQuestionsALVS" in {
        val model = ImportQuestionsStateModel(
          importEntryDetails,
          completeImportQuestionsAnswers
        )
        given(AnswerImportQuestionsMandatoryVesselInfo(model))
          .when(backToAnswerImportQuestionsALVS)
          .thenGoes(AnswerImportQuestionsALVS(model))
      }
    }

    "at state AnswerImportQuestionsOptionalVesselInfo" should {
      "go to AnswerImportQuestionsContactInfo when submitted required vessel details" in {
        given(
          AnswerImportQuestionsOptionalVesselInfo(
            ImportQuestionsStateModel(
              importEntryDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route2),
                priorityGoods = Some(ImportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ImportFreightType.Air)
              )
            )
          )
        ) when submittedImportQuestionsOptionalVesselDetails(
          VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))
        ) should thenGo(
          AnswerImportQuestionsContactInfo(
            ImportQuestionsStateModel(
              importEntryDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route2),
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
              importEntryDetails,
              completeImportQuestionsAnswers,
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedImportQuestionsOptionalVesselDetails(
          vesselDetails
        ) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importEntryDetails,
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
              importEntryDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route2),
                priorityGoods = Some(ImportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ImportFreightType.Air)
              )
            )
          )
        ) when submittedImportQuestionsOptionalVesselDetails(
          VesselDetails()
        ) should thenGo(
          AnswerImportQuestionsContactInfo(
            ImportQuestionsStateModel(
              importEntryDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route2),
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
              importEntryDetails,
              completeImportQuestionsAnswers,
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedImportQuestionsOptionalVesselDetails(
          vesselDetails
        ) should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importEntryDetails,
              completeImportQuestionsAnswers.copy(vesselDetails = Some(vesselDetails)),
              Some(nonEmptyFileUploads)
            )
          )
        )
      }

      "go back to AnswerImportQuestionsALVS" in {
        val model = ImportQuestionsStateModel(
          importEntryDetails,
          completeImportQuestionsAnswers.copy(routeType = Some(ImportRouteType.Route1))
        )
        given(AnswerImportQuestionsOptionalVesselInfo(model))
          .when(backToAnswerImportQuestionsALVS)
          .thenGoes(AnswerImportQuestionsALVS(model))
      }
    }

    "at state AnswerImportQuestionsFreightType" should {
      for (
        freightType <- ImportFreightType.values;
        requestType <- ImportRequestType.values.filterNot(_ == mandatoryReasonImportRequestType)
      ) {
        s"go to AnswerImportQuestionsOptionalVesselInfo when submitted freight type ${ImportFreightType.keyOf(freightType).get} and request type is ${ImportRequestType
            .keyOf(requestType)
            .get} and optional transport feature is turned on" in {
          given(
            AnswerImportQuestionsFreightType(
              ImportQuestionsStateModel(
                importEntryDetails,
                ImportQuestions(
                  requestType = Some(requestType),
                  routeType = Some(ImportRouteType.Route2),
                  hasALVS = Some(false)
                )
              )
            )
          ) when submittedImportQuestionsAnswerFreightType(true)(
            freightType
          ) should thenGo(
            AnswerImportQuestionsOptionalVesselInfo(
              ImportQuestionsStateModel(
                importEntryDetails,
                ImportQuestions(
                  requestType = Some(requestType),
                  routeType = Some(ImportRouteType.Route2),
                  freightType = Some(freightType),
                  hasALVS = Some(false)
                )
              )
            )
          )
        }

        s"go to AnswerImportQuestionsContactInfo when submitted freight type when freightType is ${ImportFreightType.keyOf(freightType).get} and request type is ${ImportRequestType
            .keyOf(requestType)
            .get} and optional transport feature is turned off" in {
          given(
            AnswerImportQuestionsFreightType(
              ImportQuestionsStateModel(
                importEntryDetails,
                ImportQuestions(
                  requestType = Some(requestType),
                  routeType = Some(ImportRouteType.Route2),
                  hasALVS = Some(false)
                )
              )
            )
          ) when submittedImportQuestionsAnswerFreightType(false)(
            freightType
          ) should thenGo(
            AnswerImportQuestionsContactInfo(
              ImportQuestionsStateModel(
                importEntryDetails,
                ImportQuestions(
                  requestType = Some(requestType),
                  routeType = Some(ImportRouteType.Route2),
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
                importEntryDetails,
                completeImportQuestionsAnswers.copy(requestType = Some(requestType)),
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedImportQuestionsAnswerFreightType(false)(
            freightType
          ) should thenGo(
            ImportQuestionsSummary(
              ImportQuestionsStateModel(
                importEntryDetails,
                completeImportQuestionsAnswers.copy(freightType = Some(freightType), requestType = Some(requestType)),
                Some(nonEmptyFileUploads)
              )
            )
          )
        }
      }

      for (
        freightType <- ImportFreightType.values;
        requestType <- ImportRequestType.values.filterNot(_ == mandatoryReasonImportRequestType)
      ) {
        s"go to AnswerImportQuestionsMandatoryVesselInfo when submitted freight type ${ImportFreightType.keyOf(freightType).get} regardless of request type ${ImportRequestType
            .keyOf(requestType)
            .get} when route is Hold" in {
          given(
            AnswerImportQuestionsFreightType(
              ImportQuestionsStateModel(
                importEntryDetails,
                ImportQuestions(
                  requestType = Some(requestType),
                  routeType = Some(ImportRouteType.Hold),
                  hasALVS = Some(false)
                )
              )
            )
          ) when submittedImportQuestionsAnswerFreightType(false)(
            freightType
          ) should thenGo(
            AnswerImportQuestionsMandatoryVesselInfo(
              ImportQuestionsStateModel(
                importEntryDetails,
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
                importEntryDetails,
                completeImportQuestionsAnswers
                  .copy(requestType = Some(requestType), routeType = Some(ImportRouteType.Hold)),
                Some(nonEmptyFileUploads)
              )
            )
          ) when submittedImportQuestionsAnswerFreightType(false)(
            freightType
          ) should thenGo(
            ImportQuestionsSummary(
              ImportQuestionsStateModel(
                importEntryDetails,
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

      "go back to AnswerImportQuestionsMandatoryVesselInfo" in {
        val model = ImportQuestionsStateModel(
          importEntryDetails,
          completeImportQuestionsAnswers.copy(routeType = Some(ImportRouteType.Hold))
        )
        given(AnswerImportQuestionsFreightType(model))
          .when(backToAnswerImportQuestionsVesselInfo)
          .thenGoes(AnswerImportQuestionsMandatoryVesselInfo(model))
      }

      "go back to AnswerImportQuestionsOptionalVesselInfo" in {
        val model = ImportQuestionsStateModel(
          importEntryDetails,
          completeImportQuestionsAnswers.copy(routeType = Some(ImportRouteType.Route1))
        )
        given(AnswerImportQuestionsFreightType(model))
          .when(backToAnswerImportQuestionsVesselInfo)
          .thenGoes(AnswerImportQuestionsOptionalVesselInfo(model))
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
        val upscanRequest = (nonce: String) =>
          UpscanInitiateRequest(
            "https://foo.bar/callback",
            Some("https://foo.bar/success"),
            Some("https://foo.bar/failure"),
            Some(10 * 1024 * 1024)
          )
        given(
          AnswerImportQuestionsContactInfo(
            ImportQuestionsStateModel(
              importEntryDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route2),
                priorityGoods = Some(ImportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ImportFreightType.Air),
                vesselDetails =
                  Some(VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00"))))
              )
            )
          )
        ) when submittedImportQuestionsContactInfo(uploadMultipleFiles = false)(upscanRequest)(mockUpscanInitiate)(
          ImportContactInfo(contactEmail = "name@somewhere.com")
        ) should thenGo(
          UploadFile(
            hostData = FileUploadHostData(
              importEntryDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route2),
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
            fileUploads = FileUploads(files = Seq(FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo-bar-ref")))
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
        val upscanRequest = (nonce: String) =>
          UpscanInitiateRequest(
            "https://foo.bar/callback",
            Some("https://foo.bar/success"),
            Some("https://foo.bar/failure"),
            Some(10 * 1024 * 1024)
          )
        given(
          AnswerImportQuestionsContactInfo(
            ImportQuestionsStateModel(
              importEntryDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route2),
                priorityGoods = Some(ImportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ImportFreightType.Air),
                vesselDetails =
                  Some(VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00"))))
              ),
              Some(nonEmptyFileUploads)
            )
          )
        ) when submittedImportQuestionsContactInfo(uploadMultipleFiles = false)(upscanRequest)(mockUpscanInitiate)(
          ImportContactInfo(contactEmail = "name@somewhere.com")
        ) should thenGo(
          FileUploaded(
            hostData = FileUploadHostData(
              importEntryDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route2),
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

      "go to UploadMultipleFiles when submitted required contact details and feature enabled" in {
        val mockUpscanInitiate: UpscanInitiateRequest => Future[UpscanInitiateResponse] = request =>
          Future.successful(
            UpscanInitiateResponse(
              reference = "foo-bar-ref",
              uploadRequest =
                UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> request.callbackUrl))
            )
          )
        val upscanRequest = (nonce: String) =>
          UpscanInitiateRequest(
            "https://foo.bar/callback",
            Some("https://foo.bar/success"),
            Some("https://foo.bar/failure"),
            Some(10 * 1024 * 1024)
          )
        given(
          AnswerImportQuestionsContactInfo(
            ImportQuestionsStateModel(
              importEntryDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route2),
                priorityGoods = Some(ImportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ImportFreightType.Air),
                vesselDetails =
                  Some(VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00"))))
              )
            )
          )
        ) when submittedImportQuestionsContactInfo(uploadMultipleFiles = true)(upscanRequest)(mockUpscanInitiate)(
          ImportContactInfo(contactEmail = "name@somewhere.com")
        ) should thenGo(
          UploadMultipleFiles(
            hostData = FileUploadHostData(
              importEntryDetails,
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route2),
                priorityGoods = Some(ImportPriorityGoods.ExplosivesOrFireworks),
                freightType = Some(ImportFreightType.Air),
                contactInfo = Some(ImportContactInfo(contactEmail = "name@somewhere.com")),
                vesselDetails =
                  Some(VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00"))))
              )
            ),
            fileUploads = FileUploads()
          )
        )
      }

      "go back to AnswerImportQuestionsFreightType" in {
        val model = ImportQuestionsStateModel(
          importEntryDetails,
          completeImportQuestionsAnswers.copy(routeType = Some(ImportRouteType.Route1))
        )
        given(AnswerImportQuestionsOptionalVesselInfo(model))
          .when(backToAnswerImportQuestionsFreightType)
          .thenGoes(AnswerImportQuestionsFreightType(model))
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
        val upscanRequest = (nonce: String) =>
          UpscanInitiateRequest(
            "https://foo.bar/callback",
            Some("https://foo.bar/success"),
            Some("https://foo.bar/failure"),
            Some(10 * 1024 * 1024)
          )
        given(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(exportEntryDetails, completeExportQuestionsAnswers)
          )
        ) when initiateFileUpload(upscanRequest)(mockUpscanInitiate) should thenGo(
          UploadFile(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            "foo-bar-ref",
            UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            FileUploads(files = Seq(FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo-bar-ref")))
          )
        )
      }

      "go to CreateCaseConfirmation when createCase and all answers completed" in {
        val mockCreateCaseApi: CreateCaseApi = { request =>
          Future.successful(
            TraderServicesCaseResponse(
              correlationId = "",
              result = Some(TraderServicesResult("A1234567890", generatedAt))
            )
          )
        }
        given(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportEntryDetails,
              completeExportQuestionsAnswers,
              Some(
                FileUploads(files =
                  Seq(
                    FileUpload.Accepted(
                      Nonce(1),
                      Timestamp.Any,
                      "foo-bar-ref-1",
                      "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                      ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                      "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                      "test.pdf",
                      "application/pdf",
                      Some(4567890)
                    )
                  )
                )
              )
            )
          )
        ) when (createCase(mockCreateCaseApi)(uidAndEori)) should thenGo(
          CreateCaseConfirmation(
            exportEntryDetails,
            completeExportQuestionsAnswers,
            Seq(
              UploadedFile(
                "foo-bar-ref-1",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              )
            ),
            TraderServicesResult("A1234567890", generatedAt),
            CaseSLA(Some(generatedAt.plusHours(2)))
          )
        )
      }

      "go to ExportQuestionsMissingInformationError when createCase and some answers missing" in {
        val mockCreateCaseApi: CreateCaseApi = { request =>
          Future.successful(
            TraderServicesCaseResponse(
              correlationId = "",
              result = Some(TraderServicesResult("A1234567890", generatedAt))
            )
          )
        }
        val model = ExportQuestionsStateModel(
          exportEntryDetails,
          completeExportQuestionsAnswers.copy(requestType = None),
          Some(
            FileUploads(files = Seq(acceptedFileUpload))
          )
        )
        given(
          ExportQuestionsSummary(model)
        ) when (createCase(mockCreateCaseApi)(uidAndEori)) should thenGo(
          ExportQuestionsMissingInformationError(model)
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
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportEntryDetails,
              completeExportQuestionsAnswers,
              Some(
                FileUploads(files =
                  Seq(
                    FileUpload.Accepted(
                      Nonce(1),
                      Timestamp.Any,
                      "foo-bar-ref-1",
                      "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                      ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                      "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                      "test.pdf",
                      "application/pdf",
                      Some(4567890)
                    )
                  )
                )
              )
            )
          )
        ) when (createCase(mockCreateCaseApi)(uidAndEori)) should thenGo(
          CaseAlreadyExists("A1234567890")
        )
      }

      "go back to AnswerExportQuestionsContactInfo" in {
        val model = ExportQuestionsStateModel(exportEntryDetails, completeExportQuestionsAnswers)
        given(ExportQuestionsSummary(model))
          .when(backToAnswerExportQuestionsContactInfo)
          .thenGoes(AnswerExportQuestionsContactInfo(model))
      }

      "stay when backToAnswerImportQuestionsContactInfo" in {
        val model = ExportQuestionsStateModel(exportEntryDetails, completeExportQuestionsAnswers)
        given(ExportQuestionsSummary(model))
          .when(backToAnswerImportQuestionsContactInfo)
          .thenGoes(ExportQuestionsSummary(model))
      }

      "go back to ExportQuestionsMissingInformationError" in {
        val model = ExportQuestionsStateModel(exportEntryDetails, completeExportQuestionsAnswers.copy(routeType = None))
        given(ExportQuestionsSummary(model))
          .when(backToExportQuestionsMissingInformationError)
          .thenGoes(ExportQuestionsMissingInformationError(model))
      }

      "go back to FileUploaded when backToFileUploaded and non-empty file uploads" in {
        val model =
          ExportQuestionsStateModel(exportEntryDetails, completeExportQuestionsAnswers, Some(nonEmptyFileUploads))
        given(ExportQuestionsSummary(model))
          .when(backToFileUploaded)
          .thenGoes(
            FileUploaded(
              FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
              nonEmptyFileUploads,
              acknowledged = true
            )
          )
      }

      "go back to AnswerExportQuestionsContactInfo when backToFileUploaded and empty file uploads" in {
        val model =
          ExportQuestionsStateModel(exportEntryDetails, completeExportQuestionsAnswers, None)
        given(ExportQuestionsSummary(model))
          .when(backToFileUploaded)
          .thenGoes(AnswerExportQuestionsContactInfo(model))
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
        given(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(importEntryDetails, completeImportQuestionsAnswers)
          )
        ) when initiateFileUpload(testUpscanRequest)(mockUpscanInitiate) should thenGo(
          UploadFile(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
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
            FileUploads(files = Seq(FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo-bar-ref")))
          )
        )
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
              importEntryDetails,
              completeImportQuestionsAnswers,
              Some(
                FileUploads(files =
                  Seq(
                    FileUpload.Accepted(
                      Nonce(1),
                      Timestamp.Any,
                      "foo-bar-ref-1",
                      "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                      ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                      "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                      "test.pdf",
                      "application/pdf",
                      Some(4567890)
                    )
                  )
                )
              )
            )
          )
        ) when (createCase(mockCreateCaseApi)(uidAndEori)) should thenGo(
          CreateCaseConfirmation(
            importEntryDetails,
            completeImportQuestionsAnswers,
            Seq(
              UploadedFile(
                "foo-bar-ref-1",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              )
            ),
            TraderServicesResult("A1234567890", generatedAt),
            CaseSLA(Some(generatedAt.plusHours(2)))
          )
        )
      }

      "go to ImportQuestionsMissingInformationError when createCase and some answers missing" in {
        val mockCreateCaseApi: CreateCaseApi = { request =>
          Future.successful(
            TraderServicesCaseResponse(
              correlationId = "",
              result = Some(TraderServicesResult("A1234567890", generatedAt))
            )
          )
        }
        val model = ImportQuestionsStateModel(
          importEntryDetails,
          completeImportQuestionsAnswers.copy(requestType = None),
          Some(
            FileUploads(files = Seq(acceptedFileUpload))
          )
        )
        given(
          ImportQuestionsSummary(model)
        ) when (createCase(mockCreateCaseApi)(uidAndEori)) should thenGo(
          ImportQuestionsMissingInformationError(model)
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
              importEntryDetails,
              completeImportQuestionsAnswers,
              Some(
                FileUploads(files =
                  Seq(
                    FileUpload.Accepted(
                      Nonce(1),
                      Timestamp.Any,
                      "foo-bar-ref-1",
                      "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                      ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                      "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                      "test.pdf",
                      "application/pdf",
                      Some(4567890)
                    )
                  )
                )
              )
            )
          )
        ) when (createCase(mockCreateCaseApi)(uidAndEori)) should thenGo(
          CaseAlreadyExists("A1234567890")
        )
      }

      "go back to AnswerImportQuestionsContactInfo" in {
        val model = ImportQuestionsStateModel(importEntryDetails, completeImportQuestionsAnswers)
        given(ImportQuestionsSummary(model))
          .when(backToAnswerImportQuestionsContactInfo)
          .thenGoes(AnswerImportQuestionsContactInfo(model))
      }

      "stay when backToAnswerExportQuestionsContactInfo" in {
        val model = ImportQuestionsStateModel(importEntryDetails, completeImportQuestionsAnswers)
        given(ImportQuestionsSummary(model))
          .when(backToAnswerExportQuestionsContactInfo)
          .thenGoes(ImportQuestionsSummary(model))
      }

      "go back to ImportQuestionsMissingInformationError" in {
        val model = ImportQuestionsStateModel(importEntryDetails, completeImportQuestionsAnswers.copy(routeType = None))
        given(ImportQuestionsSummary(model))
          .when(backToImportQuestionsMissingInformationError)
          .thenGoes(ImportQuestionsMissingInformationError(model))
      }

      "go back to FileUploaded when backToFileUploaded and non-empty file uploads" in {
        val model =
          ImportQuestionsStateModel(importEntryDetails, completeImportQuestionsAnswers, Some(nonEmptyFileUploads))
        given(ImportQuestionsSummary(model))
          .when(backToFileUploaded)
          .thenGoes(
            FileUploaded(
              FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
              nonEmptyFileUploads,
              acknowledged = true
            )
          )
      }

      "go back to AnswerImportQuestionsContactInfo when backToFileUploaded and empty file uploads" in {
        val model =
          ImportQuestionsStateModel(importEntryDetails, completeImportQuestionsAnswers, None)
        given(ImportQuestionsSummary(model))
          .when(backToFileUploaded)
          .thenGoes(AnswerImportQuestionsContactInfo(model))
      }
    }

    "at state UploadMultipleFiles" should {
      "go back to AnswerExportQuestionsContactInfo when backToAnswerExportQuestionsContactInfo" in {
        val model = ExportQuestionsStateModel(exportEntryDetails, completeExportQuestionsAnswers)
        given(
          UploadMultipleFiles(
            FileUploadHostData(model.entryDetails, model.exportQuestionsAnswers),
            nonEmptyFileUploads
          )
        )
          .when(backToAnswerExportQuestionsContactInfo)
          .thenGoes(AnswerExportQuestionsContactInfo(model.copy(fileUploadsOpt = Some(nonEmptyFileUploads))))
      }

      "go to Start when backToAnswerExportQuestionsContactInfo but has import answers" in {
        val model = ImportQuestionsStateModel(importEntryDetails, completeImportQuestionsAnswers)
        given(
          UploadMultipleFiles(
            FileUploadHostData(model.entryDetails, model.importQuestionsAnswers),
            nonEmptyFileUploads
          )
        )
          .when(backToAnswerExportQuestionsContactInfo)
          .thenGoes(Start)
      }

      "go back to AnswerImportQuestionsContactInfo when backToAnswerImportQuestionsContactInfo" in {
        val model = ImportQuestionsStateModel(importEntryDetails, completeImportQuestionsAnswers)
        given(
          UploadMultipleFiles(
            FileUploadHostData(model.entryDetails, model.importQuestionsAnswers),
            nonEmptyFileUploads
          )
        )
          .when(backToAnswerImportQuestionsContactInfo)
          .thenGoes(AnswerImportQuestionsContactInfo(model.copy(fileUploadsOpt = Some(nonEmptyFileUploads))))
      }

      "go to Start when backToAnswerImportQuestionsContactInfo but has export answers" in {
        val model = ExportQuestionsStateModel(exportEntryDetails, completeExportQuestionsAnswers)
        given(
          UploadMultipleFiles(
            FileUploadHostData(model.entryDetails, model.exportQuestionsAnswers),
            nonEmptyFileUploads
          )
        )
          .when(backToAnswerImportQuestionsContactInfo)
          .thenGoes(Start)
      }

      "go back to AnswerExportQuestionsContactInfo when backFromFileUpload" in {
        val model = ExportQuestionsStateModel(exportEntryDetails, completeExportQuestionsAnswers)
        given(
          UploadMultipleFiles(
            FileUploadHostData(model.entryDetails, model.exportQuestionsAnswers),
            nonEmptyFileUploads
          )
        )
          .when(backFromFileUpload)
          .thenGoes(AnswerExportQuestionsContactInfo(model.copy(fileUploadsOpt = Some(nonEmptyFileUploads))))
      }

      "go back to AnswerImportQuestionsContactInfo when backFromFileUpload" in {
        val model = ImportQuestionsStateModel(importEntryDetails, completeImportQuestionsAnswers)
        given(
          UploadMultipleFiles(
            FileUploadHostData(model.entryDetails, model.importQuestionsAnswers),
            nonEmptyFileUploads
          )
        )
          .when(backFromFileUpload)
          .thenGoes(AnswerImportQuestionsContactInfo(model.copy(fileUploadsOpt = Some(nonEmptyFileUploads))))
      }

      "go to ImportQuestionsSummary when non-empty file uploads and toSummary" in {
        given(
          UploadMultipleFiles(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
            nonEmptyFileUploads
          )
        ) when toSummary should thenGo(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              importEntryDetails,
              completeImportQuestionsAnswers,
              Some(nonEmptyFileUploads)
            )
          )
        )
      }

      "go to ExportQuestionsSummary when non-empty file uploads and toSummary" in {
        given(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            nonEmptyFileUploads
          )
        ) when toSummary should thenGo(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              exportEntryDetails,
              completeExportQuestionsAnswers,
              Some(nonEmptyFileUploads)
            )
          )
        )
      }

      "stay and filter accepted uploads when export and toUploadMultipleFiles transition" in {
        given(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            nonEmptyFileUploads + FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo-2") + FileUpload
              .Posted(Nonce.Any, Timestamp.Any, "foo-3") + FileUpload.Accepted(
              Nonce(4),
              Timestamp.Any,
              "foo-bar-ref-4",
              "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
              ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              "test.pdf",
              "application/pdf",
              Some(4567890)
            )
          )
        ) when toUploadMultipleFiles should thenGo(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            nonEmptyFileUploads + FileUpload.Accepted(
              Nonce(4),
              Timestamp.Any,
              "foo-bar-ref-4",
              "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
              ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              "test.pdf",
              "application/pdf",
              Some(4567890)
            )
          )
        )
      }

      "stay and filter accepted uploads when import and toUploadMultipleFiles transition" in {
        given(
          UploadMultipleFiles(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
            nonEmptyFileUploads + FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo-2") + FileUpload
              .Posted(Nonce.Any, Timestamp.Any, "foo-3") + FileUpload.Accepted(
              Nonce(4),
              Timestamp.Any,
              "foo-bar-ref-4",
              "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
              ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              "test.pdf",
              "application/pdf",
              Some(4567890)
            )
          )
        ) when toUploadMultipleFiles should thenGo(
          UploadMultipleFiles(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
            nonEmptyFileUploads + FileUpload.Accepted(
              Nonce(4),
              Timestamp.Any,
              "foo-bar-ref-4",
              "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
              ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              "test.pdf",
              "application/pdf",
              Some(4567890)
            )
          )
        )
      }

      "initiate new file upload when initiateNextFileUpload transition and empty uploads" in {
        given(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads()
          )
        ) when initiateNextFileUpload("001")(testUpscanRequest)(mockUpscanInitiate) should thenGo(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads() +
              FileUpload.Initiated(
                Nonce.Any,
                Timestamp.Any,
                "foo-bar-ref",
                uploadId = Some("001"),
                uploadRequest = Some(someUploadRequest(testUpscanRequest("")))
              )
          )
        )
      }

      "initiate new file upload when initiateNextFileUpload transition and some uploads exist already" in {
        val fileUploads = FileUploads(files =
          (0 until (maxFileUploadsNumber - 1))
            .map(i => FileUpload.Initiated(Nonce(i), Timestamp.Any, s"foo-bar-ref-$i", uploadId = Some(s"0$i")))
        ) + FileUpload.Rejected(Nonce(9), Timestamp.Any, "foo-bar-ref-9", S3UploadError("a", "b", "c"))
        given(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            fileUploads
          )
        ) when initiateNextFileUpload("001")(testUpscanRequest)(mockUpscanInitiate) should thenGo(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            fileUploads +
              FileUpload.Initiated(
                Nonce.Any,
                Timestamp.Any,
                "foo-bar-ref",
                uploadId = Some("001"),
                uploadRequest = Some(someUploadRequest(testUpscanRequest("")))
              )
          )
        )
      }

      "do nothing when initiateNextFileUpload with existing uploadId" in {
        given(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            nonEmptyFileUploads +
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo-bar-ref", uploadId = Some("101"))
          )
        ) when initiateNextFileUpload("101")(testUpscanRequest)(mockUpscanInitiate) should thenGo(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            nonEmptyFileUploads +
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo-bar-ref", uploadId = Some("101"))
          )
        )
      }

      "do nothing when initiateNextFileUpload and maximum number of uploads already reached" in {
        val fileUploads = FileUploads(files =
          (0 until maxFileUploadsNumber)
            .map(i => FileUpload.Initiated(Nonce(i), Timestamp.Any, s"foo-bar-ref-$i", uploadId = Some(s"0$i")))
        )
        given(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            fileUploads
          )
        ) when initiateNextFileUpload("101")(testUpscanRequest)(mockUpscanInitiate) should thenGo(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            fileUploads
          )
        )
      }

      "mark file upload as POSTED when markUploadAsPosted transition" in {
        given(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when markUploadAsPosted(S3UploadSuccess("foo-bar-ref-2", Some("bucket-123"))) should thenGo(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Posted(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        )
      }

      "do nothing when markUploadAsPosted transition and already in POSTED state" in {
        val state = UploadMultipleFiles(
          FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
          FileUploads(files =
            Seq(
              FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
              FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
              FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when markUploadAsPosted(S3UploadSuccess("foo-bar-ref-1", Some("bucket-123"))) should thenGo(state)
      }

      "overwrite upload status when markUploadAsPosted transition and already in ACCEPTED state" in {
        given(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c")),
                FileUpload.Accepted(
                  Nonce(4),
                  Timestamp.Any,
                  "foo-bar-ref-4",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                )
              )
            )
          )
        ) when markUploadAsPosted(S3UploadSuccess("foo-bar-ref-4", Some("bucket-123"))) should thenGo(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c")),
                FileUpload.Posted(Nonce(4), Timestamp.Any, "foo-bar-ref-4")
              )
            )
          )
        )
      }

      "do not overwrite upload status when markUploadAsPosted transition and already in ACCEPTED state but timestamp gap is too small" in {
        val state = UploadMultipleFiles(
          FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
          FileUploads(files =
            Seq(
              FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
              FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
              FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c")),
              FileUpload.Accepted(
                Nonce(4),
                Timestamp.now,
                "foo-bar-ref-4",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              )
            )
          )
        )
        given(state) when markUploadAsPosted(S3UploadSuccess("foo-bar-ref-4", Some("bucket-123"))) should thenGo(state)
      }

      "do nothing when markUploadAsPosted transition and none matching upload exist" in {
        val state = UploadMultipleFiles(
          FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
          FileUploads(files =
            Seq(
              FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
              FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
              FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when markUploadAsPosted(S3UploadSuccess("foo-bar-ref-4", Some("bucket-123"))) should thenGo(state)
      }

      "mark file upload as REJECTED when markUploadAsRejected transition" in {
        given(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when markUploadAsRejected(
          S3UploadError("foo-bar-ref-2", "errorCode1", "errorMessage2")
        ) should thenGo(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Rejected(
                  Nonce(2),
                  Timestamp.Any,
                  "foo-bar-ref-2",
                  S3UploadError("foo-bar-ref-2", "errorCode1", "errorMessage2")
                ),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        )
      }

      "overwrite upload status when markUploadAsRejected transition and already in REJECTED state" in {
        given(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when markUploadAsRejected(
          S3UploadError("foo-bar-ref-3", "errorCode1", "errorMessage2")
        ) should thenGo(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(
                  Nonce(3),
                  Timestamp.Any,
                  "foo-bar-ref-3",
                  S3UploadError("foo-bar-ref-3", "errorCode1", "errorMessage2")
                )
              )
            )
          )
        )
      }

      "overwrite upload status when markUploadAsRejected transition and already in ACCEPTED state" in {
        given(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c")),
                FileUpload.Accepted(
                  Nonce(4),
                  Timestamp.Any,
                  "foo-bar-ref-4",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                )
              )
            )
          )
        ) when markUploadAsRejected(
          S3UploadError("foo-bar-ref-4", "errorCode1", "errorMessage2")
        ) should thenGo(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c")),
                FileUpload.Rejected(
                  Nonce(4),
                  Timestamp.Any,
                  "foo-bar-ref-4",
                  S3UploadError("foo-bar-ref-4", "errorCode1", "errorMessage2")
                )
              )
            )
          )
        )
      }

      "do nothing when markUploadAsRejected transition and none matching file upload found" in {
        val state = UploadMultipleFiles(
          FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
          FileUploads(files =
            Seq(
              FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
              FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
              FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when markUploadAsRejected(
          S3UploadError("foo-bar-ref-4", "errorCode1", "errorMessage2")
        ) should thenGo(state)
      }

      "update file upload status to ACCEPTED when positive upscanCallbackArrived transition" in {
        given(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf",
              size = Some(4567890)
            )
          )
        ) should thenGo(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                acceptedFileUpload,
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        )
      }

      "update file upload status to ACCEPTED with sanitized name of the file" in {
        given(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = """F:\My Documents\my invoices\invoice00001_1234.pdf""",
              fileMimeType = "application/pdf",
              size = Some(4567890)
            )
          )
        ) should thenGo(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Accepted(
                  Nonce(1),
                  Timestamp.Any,
                  "foo-bar-ref-1",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "invoice00001_1234.pdf",
                  "application/pdf",
                  Some(4567890)
                ),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        )
      }

      "do nothing when positive upscanCallbackArrived transition and none matching file upload found" in {
        val state = UploadMultipleFiles(
          FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
          FileUploads(files =
            Seq(
              FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
              FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
              FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when upscanCallbackArrived(Nonce(4))(
          UpscanFileReady(
            reference = "foo-bar-ref-4",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf",
              size = Some(4567890)
            )
          )
        ) should thenGo(state)
      }

      "overwrite status when positive upscanCallbackArrived transition and file upload already in ACCEPTED state" in {
        given(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Accepted(
                  Nonce(1),
                  Timestamp.Any,
                  "foo-bar-ref-1",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?0035699",
                  ZonedDateTime.parse("2018-04-24T09:28:00Z"),
                  "786f101dd52e8b2ace0dcf5ed09b1d1ba30e608938510ce46e7a5c7a4e775189",
                  "test.png",
                  "image/png",
                  Some(4567890)
                ),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf",
              size = Some(4567890)
            )
          )
        ) should thenGo(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                acceptedFileUpload,
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        )
      }

      "do not overwrite status when positive upscanCallbackArrived transition and file upload already in ACCEPTED state if timestamp gap is to small" in {
        val now = Timestamp.now
        val state = UploadMultipleFiles(
          FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
          FileUploads(files =
            Seq(
              FileUpload.Accepted(
                Nonce(1),
                now,
                "foo-bar-ref-1",
                "https://bucketName.s3.eu-west-2.amazonaws.com?0035699",
                ZonedDateTime.parse("2018-04-24T09:28:00Z"),
                "786f101dd52e8b2ace0dcf5ed09b1d1ba30e608938510ce46e7a5c7a4e775189",
                "test.png",
                "image/png",
                Some(4567890)
              ),
              FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
              FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when upscanCallbackArrived(Nonce(1))(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf",
              size = Some(4567890)
            )
          )
        ) should thenGo(state)
      }

      "overwrite upload status when positive upscanCallbackArrived transition and file upload already in REJECTED state" in {
        given(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Rejected(Nonce(1), Timestamp.Any, "foo-bar-ref-1", S3UploadError("a", "b", "c")),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf",
              size = Some(4567890)
            )
          )
        ) should thenGo(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                acceptedFileUpload,
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        )
      }

      "overwrite upload status when positive upscanCallbackArrived transition and file upload already in FAILED state" in {
        given(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Failed(
                  Nonce(1),
                  Timestamp.Any,
                  "foo-bar-ref-1",
                  UpscanNotification.FailureDetails(
                    failureReason = UpscanNotification.QUARANTINE,
                    message = "e.g. This file has a virus"
                  )
                ),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf",
              size = Some(4567890)
            )
          )
        ) should thenGo(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                acceptedFileUpload,
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        )
      }

      "update file upload status to FAILED when negative upscanCallbackArrived transition" in {
        given(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileFailed(
            reference = "foo-bar-ref-1",
            failureDetails = UpscanNotification.FailureDetails(
              failureReason = UpscanNotification.QUARANTINE,
              message = "e.g. This file has a virus"
            )
          )
        ) should thenGo(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Failed(
                  Nonce(1),
                  Timestamp.Any,
                  "foo-bar-ref-1",
                  UpscanNotification.FailureDetails(
                    failureReason = UpscanNotification.QUARANTINE,
                    message = "e.g. This file has a virus"
                  )
                ),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        )
      }

      "do nothing when negative upscanCallbackArrived transition and none matching file upload found" in {
        val state = UploadMultipleFiles(
          FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
          FileUploads(files =
            Seq(
              FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
              FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
              FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when upscanCallbackArrived(Nonce(4))(
          UpscanFileFailed(
            reference = "foo-bar-ref-4",
            failureDetails = UpscanNotification.FailureDetails(
              failureReason = UpscanNotification.QUARANTINE,
              message = "e.g. This file has a virus"
            )
          )
        ) should thenGo(state)
      }

      "overwrite upload status when negative upscanCallbackArrived transition and upload already in FAILED state" in {
        given(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Failed(
                  Nonce(1),
                  Timestamp.Any,
                  "foo-bar-ref-1",
                  UpscanNotification.FailureDetails(
                    failureReason = UpscanNotification.REJECTED,
                    message = "e.g. This file has wrong type"
                  )
                ),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileFailed(
            reference = "foo-bar-ref-1",
            failureDetails = UpscanNotification.FailureDetails(
              failureReason = UpscanNotification.QUARANTINE,
              message = "e.g. This file has a virus"
            )
          )
        ) should thenGo(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Failed(
                  Nonce(1),
                  Timestamp.Any,
                  "foo-bar-ref-1",
                  UpscanNotification.FailureDetails(
                    failureReason = UpscanNotification.QUARANTINE,
                    message = "e.g. This file has a virus"
                  )
                ),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        )
      }

      "overwrite upload status when negative upscanCallbackArrived transition and upload already in ACCEPTED state" in {
        given(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Accepted(
                  Nonce(1),
                  Timestamp.Any,
                  "foo-bar-ref-1",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?0035699",
                  ZonedDateTime.parse("2018-04-24T09:28:00Z"),
                  "786f101dd52e8b2ace0dcf5ed09b1d1ba30e608938510ce46e7a5c7a4e775189",
                  "test.png",
                  "image/png",
                  Some(4567890)
                ),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileFailed(
            reference = "foo-bar-ref-1",
            failureDetails = UpscanNotification.FailureDetails(
              failureReason = UpscanNotification.QUARANTINE,
              message = "e.g. This file has a virus"
            )
          )
        ) should thenGo(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Failed(
                  Nonce(1),
                  Timestamp.Any,
                  "foo-bar-ref-1",
                  UpscanNotification.FailureDetails(
                    failureReason = UpscanNotification.QUARANTINE,
                    message = "e.g. This file has a virus"
                  )
                ),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(3), Timestamp.Any, "foo-bar-ref-3", S3UploadError("a", "b", "c"))
              )
            )
          )
        )
      }

      "remove file upload when removeFileUploadByReference transition and reference exists" in {
        given(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  Nonce(3),
                  Timestamp.Any,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                ),
                FileUpload.Rejected(Nonce(4), Timestamp.Any, "foo-bar-ref-4", S3UploadError("a", "b", "c"))
              )
            )
          )
        ) when removeFileUploadByReference("foo-bar-ref-3")(testUpscanRequest)(mockUpscanInitiate) should thenGo(
          UploadMultipleFiles(
            FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Rejected(Nonce(4), Timestamp.Any, "foo-bar-ref-4", S3UploadError("a", "b", "c"))
              )
            )
          )
        )
      }

      "do nothing when removeFileUploadByReference transition and none file upload matches" in {
        val state = UploadMultipleFiles(
          FileUploadHostData(exportEntryDetails, completeExportQuestionsAnswers),
          FileUploads(files =
            Seq(
              FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
              FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
              FileUpload.Accepted(
                Nonce(3),
                Timestamp.Any,
                "foo-bar-ref-3",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              ),
              FileUpload.Rejected(Nonce(4), Timestamp.Any, "foo-bar-ref-4", S3UploadError("a", "b", "c"))
            )
          )
        )
        given(state) when removeFileUploadByReference("foo-bar-ref-5")(testUpscanRequest)(
          mockUpscanInitiate
        ) should thenGo(state)
      }
    }

    "at state UploadFile" should {
      "go to WaitingForFileVerification when waitForFileVerification and not verified yet" in {
        given(
          UploadFile(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
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
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  Nonce(3),
                  Timestamp.Any,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                ),
                FileUpload.Failed(
                  Nonce(4),
                  Timestamp.Any,
                  "foo-bar-ref-4",
                  UpscanNotification.FailureDetails(UpscanNotification.REJECTED, "some failure reason")
                )
              )
            )
          )
        ) when waitForFileVerification should thenGo(
          WaitingForFileVerification(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-2",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Posted(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  Nonce(3),
                  Timestamp.Any,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                ),
                FileUpload.Failed(
                  Nonce(4),
                  Timestamp.Any,
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
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
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
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  Nonce(3),
                  Timestamp.Any,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                ),
                FileUpload.Failed(
                  Nonce(4),
                  Timestamp.Any,
                  "foo-bar-ref-4",
                  UpscanNotification.FailureDetails(UpscanNotification.REJECTED, "some failure reason")
                )
              )
            )
          )
        ) when waitForFileVerification should thenGo(
          FileUploaded(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  Nonce(3),
                  Timestamp.Any,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                ),
                FileUpload.Failed(
                  Nonce(4),
                  Timestamp.Any,
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
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
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
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  Nonce(3),
                  Timestamp.Any,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                ),
                FileUpload.Failed(
                  Nonce(4),
                  Timestamp.Any,
                  "foo-bar-ref-4",
                  UpscanNotification.FailureDetails(UpscanNotification.REJECTED, "some failure reason")
                )
              )
            )
          )
        ) when waitForFileVerification should thenGo(
          UploadFile(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
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
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Initiated(Nonce(2), Timestamp.Any, "foo-bar-ref-2"),
                FileUpload.Accepted(
                  Nonce(3),
                  Timestamp.Any,
                  "foo-bar-ref-3",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                ),
                FileUpload.Failed(
                  Nonce(4),
                  Timestamp.Any,
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
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files = Seq(FileUpload.Initiated(Nonce(1), Timestamp.Any, "foo-bar-ref-1")))
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf",
              size = Some(4567890)
            )
          )
        ) should thenGo(
          FileUploaded(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
            FileUploads(files = Seq(acceptedFileUpload))
          )
        )
      }

      "go to UploadFile when upscanCallbackArrived and accepted, and reference matches but upload is a duplicate" in {
        given(
          UploadFile(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
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
                FileUpload.Initiated(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Accepted(
                  Nonce(2),
                  Timestamp.Any,
                  "foo-bar-ref-2",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2020-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                )
              )
            )
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2020-04-24T09:32:13Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test2.png",
              fileMimeType = "image/png",
              size = Some(4567890)
            )
          )
        ) should thenGo(
          UploadFile(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
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
                  Nonce(1),
                  Timestamp.Any,
                  "foo-bar-ref-1",
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "test2.png"
                ),
                FileUpload.Accepted(
                  Nonce(2),
                  Timestamp.Any,
                  "foo-bar-ref-2",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2020-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                )
              )
            ),
            Some(
              DuplicateFileUpload(
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "test2.png"
              )
            )
          )
        )
      }

      "go to UploadFile when upscanCallbackArrived and failed, and reference matches" in {
        given(
          UploadFile(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files = Seq(FileUpload.Initiated(Nonce(1), Timestamp.Any, "foo-bar-ref-1")))
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileFailed(
            reference = "foo-bar-ref-1",
            failureDetails = UpscanNotification.FailureDetails(
              failureReason = UpscanNotification.UNKNOWN,
              message = "e.g. This file has a virus"
            )
          )
        ) should thenGo(
          UploadFile(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
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
                  Nonce(1),
                  Timestamp.Any,
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
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUploads(files = Seq(FileUpload.Initiated(Nonce(1), Timestamp.Any, "foo-bar-ref-1")))
          )

        given(state) when markUploadAsRejected(
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
                  Nonce(1),
                  Timestamp.Any,
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

      "switch over to UploadMultipleFiles when toUploadMultipleFiles" in {
        given(
          UploadFile(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-4",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            nonEmptyFileUploads,
            None
          )
        )
          .when(toUploadMultipleFiles)
          .thenGoes(
            UploadMultipleFiles(
              FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
              nonEmptyFileUploads
            )
          )
      }

      "go to UploadFile when initiateFileUpload and number of uploaded files below the limit" in {
        val hostData = FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers)
        val fileUploads = FileUploads(files =
          for (i <- 0 until (maxFileUploadsNumber - 1))
            yield FileUpload.Accepted(
              Nonce(i),
              Timestamp.Any,
              s"foo-bar-ref-$i",
              "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
              ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              "test.pdf",
              "application/pdf",
              Some(4567890)
            )
        )
        given(UploadMultipleFiles(hostData, fileUploads))
          .when(initiateFileUpload(testUpscanRequest)(mockUpscanInitiate))
          .thenGoes(
            UploadFile(
              hostData,
              "foo-bar-ref",
              someUploadRequest(testUpscanRequest("foo")),
              fileUploads + FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo-bar-ref")
            )
          )
      }

      "go to FileUploaded when initiateFileUpload and number of uploaded files above the limit" in {
        val hostData = FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers)
        val fileUploads = FileUploads(files =
          for (i <- 0 until maxFileUploadsNumber)
            yield FileUpload.Accepted(
              Nonce(i),
              Timestamp.Any,
              s"foo-bar-ref-$i",
              "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
              ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              "test.pdf",
              "application/pdf",
              Some(4567890)
            )
        )
        given(UploadMultipleFiles(hostData, fileUploads))
          .when(initiateFileUpload(testUpscanRequest)(mockUpscanInitiate))
          .thenGoes(
            FileUploaded(
              hostData,
              fileUploads
            )
          )
      }
    }

    "at state WaitingForFileVerification" should {
      "stay when waitForFileVerification and not verified yet" in {
        val state = WaitingForFileVerification(
          FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
          "foo-bar-ref-1",
          UploadRequest(
            href = "https://s3.bucket",
            fields = Map(
              "callbackUrl"     -> "https://foo.bar/callback",
              "successRedirect" -> "https://foo.bar/success",
              "errorRedirect"   -> "https://foo.bar/failure"
            )
          ),
          FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
          FileUploads(files =
            Seq(
              FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1")
            )
          )
        )
        given(state).when(waitForFileVerification).thenNoChange
      }

      "go to UploadFile when waitForFileVerification and reference unknown" in {
        given(
          WaitingForFileVerification(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-2",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1")
              )
            )
          )
        ) when waitForFileVerification should thenGo(
          UploadFile(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
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
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1")
              )
            )
          )
        )
      }

      "go to FileUploaded when waitForFileVerification and file already accepted" in {
        given(
          WaitingForFileVerification(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
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
              Nonce(1),
              Timestamp.Any,
              "foo-bar-ref-1",
              "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
              ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              "test.pdf",
              "application/pdf",
              Some(4567890)
            ),
            FileUploads(files = Seq(acceptedFileUpload))
          )
        ) when waitForFileVerification should thenGo(
          FileUploaded(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
            FileUploads(files = Seq(acceptedFileUpload))
          )
        )
      }

      "go to UploadFile when waitForFileVerification and file already failed" in {
        given(
          WaitingForFileVerification(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
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
              Nonce(1),
              Timestamp.Any,
              "foo-bar-ref-1",
              UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
            ),
            FileUploads(files =
              Seq(
                FileUpload.Failed(
                  Nonce(1),
                  Timestamp.Any,
                  "foo-bar-ref-1",
                  UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
                )
              )
            )
          )
        ) when waitForFileVerification should thenGo(
          UploadFile(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
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
                  Nonce(1),
                  Timestamp.Any,
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
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
            FileUploads(files = Seq(FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1")))
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileReady(
            reference = "foo-bar-ref-1",
            downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
            uploadDetails = UpscanNotification.UploadDetails(
              uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              fileName = "test.pdf",
              fileMimeType = "application/pdf",
              size = Some(4567890)
            )
          )
        ) should thenGo(
          FileUploaded(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
            FileUploads(files = Seq(acceptedFileUpload))
          )
        )
      }

      "go to UploadFile when upscanCallbackArrived and failed, and reference matches" in {
        given(
          WaitingForFileVerification(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
            FileUploads(files = Seq(FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1")))
          )
        ) when upscanCallbackArrived(Nonce(1))(
          UpscanFileFailed(
            reference = "foo-bar-ref-1",
            failureDetails = UpscanNotification.FailureDetails(
              failureReason = UpscanNotification.QUARANTINE,
              message = "e.g. This file has a virus"
            )
          )
        ) should thenGo(
          UploadFile(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
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
                  Nonce(1),
                  Timestamp.Any,
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
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Posted(Nonce(2), Timestamp.Any, "foo-bar-ref-2")
              )
            )
          )
        ) when upscanCallbackArrived(Nonce(2))(
          UpscanFileFailed(
            reference = "foo-bar-ref-2",
            failureDetails = UpscanNotification.FailureDetails(
              failureReason = UpscanNotification.QUARANTINE,
              message = "e.g. This file has a virus"
            )
          )
        ) should thenGo(
          WaitingForFileVerification(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Failed(
                  Nonce(2),
                  Timestamp.Any,
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
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Accepted(
                  Nonce(2),
                  Timestamp.Any,
                  "foo-bar-ref-2",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                )
              )
            )
          )
        ) when backToFileUploaded should thenGo(
          FileUploaded(
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Accepted(
                  Nonce(2),
                  Timestamp.Any,
                  "foo-bar-ref-2",
                  "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
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
            FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
            "foo-bar-ref-1",
            UploadRequest(
              href = "https://s3.bucket",
              fields = Map(
                "callbackUrl"     -> "https://foo.bar/callback",
                "successRedirect" -> "https://foo.bar/success",
                "errorRedirect"   -> "https://foo.bar/failure"
              )
            ),
            FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                FileUpload.Posted(Nonce(2), Timestamp.Any, "foo-bar-ref-2")
              )
            )
          )
        ) when backToFileUploaded should thenGo(
          AnswerImportQuestionsContactInfo(
            ImportQuestionsStateModel(
              importEntryDetails,
              completeImportQuestionsAnswers,
              Some(
                FileUploads(files =
                  Seq(
                    FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
                    FileUpload.Posted(Nonce(2), Timestamp.Any, "foo-bar-ref-2")
                  )
                )
              )
            )
          )
        )
      }

      "go to UploadFile when initiateFileUpload" in {
        val hostData = FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers)
        val uploadRequest = UploadRequest(
          href = "https://s3.bucket",
          fields = Map(
            "callbackUrl"     -> "https://foo.bar/callback",
            "successRedirect" -> "https://foo.bar/success",
            "errorRedirect"   -> "https://foo.bar/failure"
          )
        )
        val fileUploads = FileUploads(files =
          Seq(
            FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1")
          )
        )
        val state = WaitingForFileVerification(
          hostData,
          "foo-bar-ref-1",
          uploadRequest,
          FileUpload.Posted(Nonce(1), Timestamp.Any, "foo-bar-ref-1"),
          fileUploads
        )
        given(state)
          .when(initiateFileUpload(testUpscanRequest)(mockUpscanInitiate))
          .thenGoes(UploadFile(hostData, "foo-bar-ref-1", uploadRequest, fileUploads))
      }
    }

    "at state FileUploaded" should {
      "go to acknowledged FileUploaded when waitForFileVerification" in {
        val state = FileUploaded(
          FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers),
          FileUploads(files =
            Seq(
              FileUpload.Accepted(
                Nonce(1),
                Timestamp.Any,
                "foo-bar-ref-1",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              )
            )
          ),
          acknowledged = false
        )
        given(state)
          .when(waitForFileVerification)
          .thenGoes(state.copy(acknowledged = true))
      }

      "go to UploadFile when initiateFileUpload and number of uploads below the limit" in {
        val hostData = FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers)
        val fileUploads = FileUploads(files =
          for (i <- 0 until (maxFileUploadsNumber - 1))
            yield FileUpload.Accepted(
              Nonce(i),
              Timestamp.Any,
              s"foo-bar-ref-$i",
              "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
              ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              "test.pdf",
              "application/pdf",
              Some(4567890)
            )
        )
        given(
          FileUploaded(
            hostData,
            fileUploads,
            acknowledged = false
          )
        )
          .when(initiateFileUpload(testUpscanRequest)(mockUpscanInitiate))
          .thenGoes(
            UploadFile(
              hostData,
              "foo-bar-ref",
              someUploadRequest(testUpscanRequest("foo")),
              fileUploads + FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo-bar-ref")
            )
          )
      }

      "stay when initiateFileUpload and number of uploads above the limit" in {
        val hostData = FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers)
        val fileUploads = FileUploads(files =
          for (i <- 0 until maxFileUploadsNumber)
            yield FileUpload.Accepted(
              Nonce(i),
              Timestamp.Any,
              s"foo-bar-ref-$i",
              "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
              ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              "test.pdf",
              "application/pdf",
              Some(4567890)
            )
        )
        given(
          FileUploaded(
            hostData,
            fileUploads,
            acknowledged = false
          )
        )
          .when(initiateFileUpload(testUpscanRequest)(mockUpscanInitiate))
          .thenNoChange
      }

      "go to UploadFile when submitedUploadAnotherFileChoice with yes and number of uploads below the limit" in {
        val hostData = FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers)
        val fileUploads = FileUploads(files =
          for (i <- 0 until (maxFileUploadsNumber - 1))
            yield fileUploadAccepted.copy(reference = s"file-$i")
        )
        given(
          FileUploaded(hostData, fileUploads)
        )
          .when(submitedUploadAnotherFileChoice(testUpscanRequest)(mockUpscanInitiate)(toSummary)(true))
          .thenGoes(
            UploadFile(
              hostData,
              "foo-bar-ref",
              someUploadRequest(testUpscanRequest("foo")),
              fileUploads + FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo-bar-ref")
            )
          )
      }

      "apply follow-up transition when submitedUploadAnotherFileChoice with yes and number of uploads above the limit" in {
        val hostData = FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers)
        val fileUploads = FileUploads(files =
          for (i <- 0 until maxFileUploadsNumber)
            yield fileUploadAccepted.copy(reference = s"file-$i")
        )
        given(
          FileUploaded(hostData, fileUploads)
        )
          .when(submitedUploadAnotherFileChoice(testUpscanRequest)(mockUpscanInitiate)(toSummary)(true))
          .thenGoes(
            ImportQuestionsSummary(
              ImportQuestionsStateModel(importEntryDetails, completeImportQuestionsAnswers, Some(fileUploads))
            )
          )
      }

      "apply follow-up transition when submitedUploadAnotherFileChoice with no" in {
        val hostData = FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers)
        val fileUploads = FileUploads(files =
          for (i <- 0 until (maxFileUploadsNumber - 1))
            yield fileUploadAccepted.copy(reference = s"file-$i")
        )
        given(
          FileUploaded(hostData, fileUploads)
        )
          .when(submitedUploadAnotherFileChoice(testUpscanRequest)(mockUpscanInitiate)(toSummary)(false))
          .thenGoes(
            ImportQuestionsSummary(
              ImportQuestionsStateModel(importEntryDetails, completeImportQuestionsAnswers, Some(fileUploads))
            )
          )
      }

      "go to UploadFile when removeFileUploadByReference leaving no files" in {
        val hostData = FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers)
        val fileUploads = FileUploads(Seq(fileUploadAccepted))
        given(
          FileUploaded(hostData, fileUploads)
        )
          .when(removeFileUploadByReference(fileUploadAccepted.reference)(testUpscanRequest)(mockUpscanInitiate))
          .thenGoes(
            UploadFile(
              hostData,
              "foo-bar-ref",
              someUploadRequest(testUpscanRequest("foo")),
              FileUploads(Seq(FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo-bar-ref")))
            )
          )
      }
    }

    "at state CreateCaseConfirmation" should {
      "go to Start when start" in {
        given(
          CreateCaseConfirmation(
            importEntryDetails,
            completeImportQuestionsAnswers,
            Seq(
              UploadedFile(
                "foo",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              )
            ),
            TraderServicesResult("A1234567890", generatedAt),
            CaseSLA(Some(generatedAt.plusHours(2)))
          )
        ) when start should thenGo(Start)
      }

      "go to clean CaseAlreadySubmitted when browser back" in {
        given(
          CreateCaseConfirmation(
            importEntryDetails,
            completeImportQuestionsAnswers,
            Seq(
              UploadedFile(
                "foo",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              )
            ),
            TraderServicesResult("A1234567890", generatedAt),
            CaseSLA(Some(generatedAt.plusHours(2)))
          )
        ) when toSummary should thenGo(CaseAlreadySubmitted)
      }

      "go to the clean EnterEntryDetails when backToEnterEntryDetails from an export end state" in {
        given(
          CreateCaseConfirmation(
            importEntryDetails,
            completeExportQuestionsAnswers,
            Seq(
              UploadedFile(
                "foo",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              )
            ),
            TraderServicesResult("A1234567890", generatedAt),
            CaseSLA(Some(generatedAt.plusHours(2)))
          )
        ) when backToEnterEntryDetails should thenGo(EnterEntryDetails())
      }

      "go to the clean EnterEntryDetails when backToEnterEntryDetails from an import end state" in {
        given(
          CreateCaseConfirmation(
            importEntryDetails,
            completeImportQuestionsAnswers,
            Seq(
              UploadedFile(
                "foo",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              )
            ),
            TraderServicesResult("A1234567890", generatedAt),
            CaseSLA(Some(generatedAt.plusHours(2)))
          )
        ) when backToEnterEntryDetails should thenGo(EnterEntryDetails())
      }
    }

    "at state CaseAlreadyExists" should {
      "go to Start when start" in {
        given(
          CaseAlreadyExists("A1234567890")
        ) when start should thenGo(Start)
      }

      "go to clean EnterEntryDetails when going back" in {
        given(
          CaseAlreadyExists("A1234567890")
        ) when backToEnterEntryDetails should thenGo(EnterEntryDetails())
      }
    }

    "at any state" should {
      "check model completeness in gotoSummaryIfCompleteOr" in {
        await(model.gotoSummaryIfCompleteOr(Start)) shouldBe Start
        await(model.gotoSummaryIfCompleteOr(EnterEntryDetails())) shouldBe EnterEntryDetails()
        await(
          model.gotoSummaryIfCompleteOr(
            AnswerImportQuestionsWhichPriorityGoods(
              ImportQuestionsStateModel(importEntryDetails, completeImportQuestionsAnswers, Some(nonEmptyFileUploads))
            )
          )
        ) shouldBe ImportQuestionsSummary(
          ImportQuestionsStateModel(importEntryDetails, completeImportQuestionsAnswers, Some(nonEmptyFileUploads))
        )
        await(
          model.gotoSummaryIfCompleteOr(
            AnswerImportQuestionsWhichPriorityGoods(
              ImportQuestionsStateModel(
                importEntryDetails,
                completeImportQuestionsAnswers.copy(contactInfo = None),
                Some(nonEmptyFileUploads)
              )
            )
          )
        ) shouldBe AnswerImportQuestionsWhichPriorityGoods(
          ImportQuestionsStateModel(
            importEntryDetails,
            completeImportQuestionsAnswers.copy(contactInfo = None),
            Some(nonEmptyFileUploads)
          )
        )
        await(
          model.gotoSummaryIfCompleteOr(
            AnswerExportQuestionsHasPriorityGoods(
              ExportQuestionsStateModel(exportEntryDetails, completeExportQuestionsAnswers, Some(nonEmptyFileUploads))
            )
          )
        ) shouldBe ExportQuestionsSummary(
          ExportQuestionsStateModel(exportEntryDetails, completeExportQuestionsAnswers, Some(nonEmptyFileUploads))
        )
        await(
          model.gotoSummaryIfCompleteOr(
            AnswerExportQuestionsHasPriorityGoods(
              ExportQuestionsStateModel(
                exportEntryDetails,
                completeExportQuestionsAnswers.copy(contactInfo = None),
                Some(nonEmptyFileUploads)
              )
            )
          )
        ) shouldBe AnswerExportQuestionsHasPriorityGoods(
          ExportQuestionsStateModel(
            exportEntryDetails,
            completeExportQuestionsAnswers.copy(contactInfo = None),
            Some(nonEmptyFileUploads)
          )
        )
      }

      "check model completeness in gotoSummaryIfCompleteOrApplyTransition" in {
        await(model.gotoSummaryIfCompleteOrApplyTransition(Start)(start)) shouldBe Start
        await(model.gotoSummaryIfCompleteOrApplyTransition(EnterEntryDetails())(start)) shouldBe EnterEntryDetails()
        await(
          model.gotoSummaryIfCompleteOrApplyTransition(
            AnswerImportQuestionsWhichPriorityGoods(
              ImportQuestionsStateModel(importEntryDetails, completeImportQuestionsAnswers, Some(nonEmptyFileUploads))
            )
          )(start)
        ) shouldBe ImportQuestionsSummary(
          ImportQuestionsStateModel(importEntryDetails, completeImportQuestionsAnswers, Some(nonEmptyFileUploads))
        )
        await(
          model.gotoSummaryIfCompleteOrApplyTransition(
            AnswerImportQuestionsWhichPriorityGoods(
              ImportQuestionsStateModel(
                importEntryDetails,
                completeImportQuestionsAnswers.copy(contactInfo = None),
                Some(nonEmptyFileUploads)
              )
            )
          )(start)
        ) shouldBe Start
        await(
          model.gotoSummaryIfCompleteOrApplyTransition(
            AnswerExportQuestionsHasPriorityGoods(
              ExportQuestionsStateModel(exportEntryDetails, completeExportQuestionsAnswers, Some(nonEmptyFileUploads))
            )
          )(start)
        ) shouldBe ExportQuestionsSummary(
          ExportQuestionsStateModel(exportEntryDetails, completeExportQuestionsAnswers, Some(nonEmptyFileUploads))
        )
        await(
          model.gotoSummaryIfCompleteOrApplyTransition(
            AnswerExportQuestionsHasPriorityGoods(
              ExportQuestionsStateModel(
                exportEntryDetails,
                completeExportQuestionsAnswers.copy(contactInfo = None),
                Some(nonEmptyFileUploads)
              )
            )
          )(start)
        ) shouldBe Start
      }

      "match commonFileUploadStatusHandler" in {
        val hostData = FileUploadHostData(importEntryDetails, completeImportQuestionsAnswers)
        val fileUploads = nonEmptyFileUploads
        val uploadRequest = UploadRequest(
          href = "https://s3.bucket",
          fields = Map(
            "callbackUrl"     -> "https://foo.bar/callback",
            "successRedirect" -> "https://foo.bar/success",
            "errorRedirect"   -> "https://foo.bar/failure"
          )
        )

        await(
          model.FileUploadTransitions
            .commonFileUploadStatusHandler(hostData, fileUploads, "foo-ref", uploadRequest, Start)
            .apply(None)
        )
          .shouldBe(Start)

        await(
          model.FileUploadTransitions
            .commonFileUploadStatusHandler(hostData, fileUploads, "foo-ref", uploadRequest, Start)
            .apply(Some(fileUploadAccepted))
        )
          .shouldBe(FileUploaded(hostData, fileUploads))

        await(
          model.FileUploadTransitions
            .commonFileUploadStatusHandler(hostData, fileUploads, "foo-ref", uploadRequest, Start)
            .apply(Some(fileUploadPosted))
        )
          .shouldBe(WaitingForFileVerification(hostData, "foo-ref", uploadRequest, fileUploadPosted, fileUploads))

        await(
          model.FileUploadTransitions
            .commonFileUploadStatusHandler(hostData, fileUploads, "foo-ref", uploadRequest, Start)
            .apply(Some(fileUploadInitiated))
        )
          .shouldBe(UploadFile(hostData, "foo-ref", uploadRequest, fileUploads))

        await(
          model.FileUploadTransitions
            .commonFileUploadStatusHandler(hostData, fileUploads, "foo-ref", uploadRequest, Start)
            .apply(Some(fileUploadRejected))
        )
          .shouldBe(
            UploadFile(
              hostData,
              "foo-ref",
              uploadRequest,
              fileUploads,
              Some(FileTransmissionFailed(fileUploadRejected.details))
            )
          )

        await(
          model.FileUploadTransitions
            .commonFileUploadStatusHandler(hostData, fileUploads, "foo-ref", uploadRequest, Start)
            .apply(Some(fileUploadFailed))
        )
          .shouldBe(
            UploadFile(
              hostData,
              "foo-ref",
              uploadRequest,
              fileUploads,
              Some(FileVerificationFailed(fileUploadFailed.details))
            )
          )

        await(
          model.FileUploadTransitions
            .commonFileUploadStatusHandler(hostData, fileUploads, "foo-ref", uploadRequest, Start)
            .apply(Some(fileUploadDuplicate))
        )
          .shouldBe(
            UploadFile(
              hostData,
              "foo-ref",
              uploadRequest,
              fileUploads,
              Some(
                DuplicateFileUpload(
                  fileUploadDuplicate.checksum,
                  fileUploadDuplicate.existingFileName,
                  fileUploadDuplicate.duplicateFileName
                )
              )
            )
          )
      }
    }
  }
}
