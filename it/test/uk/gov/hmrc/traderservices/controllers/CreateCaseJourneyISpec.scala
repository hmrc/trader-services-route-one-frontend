/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.traderservices.controllers

import org.apache.pekko.actor.ActorSystem
import com.github.tomakehurst.wiremock.client.WireMock
import com.typesafe.config.Config
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json._
import play.api.libs.ws.{DefaultWSCookie, StandaloneWSRequest}
import play.api.mvc._
import play.api.test.FakeRequest
import uk.gov.hmrc.traderservices.stubs.{TraderServicesApiStubs, UpscanInitiateStubs}
import uk.gov.hmrc.traderservices.support
import uk.gov.hmrc.traderservices.support.{ServerISpec, StateMatchers, TestData, TestJourneyService}
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, SessionId, SessionKeys}
import uk.gov.hmrc.traderservices.connectors.{FileTransferResult, TraderServicesResult}
import uk.gov.hmrc.traderservices.controllers.{CreateCaseJourneyController, routes}
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.CreateCaseJourneyState._
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.FileUploadHostData
import uk.gov.hmrc.traderservices.journeys.{CreateCaseJourneyStateFormats, State}
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.repository.CacheRepository
import uk.gov.hmrc.traderservices.services.{CreateCaseJourneyService, EncryptedSessionCache, KeyProvider}
import uk.gov.hmrc.traderservices.utils.SHA256
import uk.gov.hmrc.traderservices.views.CommonUtilsHelper.DateTimeUtilities

import java.time.temporal.{ChronoField, ChronoUnit}
import java.time.{LocalDate, LocalDateTime, ZonedDateTime}
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

class CreateCaseJourneyISpec
    extends CreateCaseJourneyISpecSetup with TraderServicesApiStubs with UpscanInitiateStubs {

  import journey.model.FileUploadState._

  override def uploadMultipleFilesFeature: Boolean = false
  override def requireEnrolmentFeature: Boolean = true
  override def requireOptionalTransportFeature: Boolean = false

  "CreateCaseJourneyController" when {

    "user not enrolled for HMRC-XYZ" should {
      "be redirected to the subscription journey" in {
        journey.setState(Start)
        givenAuthorisedWithoutEnrolments()
        givenDummySubscriptionUrl
        val result = await(request("/").get())
        result.status shouldBe 200
        verifyAuthoriseAttempt()
        verifySubscriptionAttempt()
      }
    }

    "preferUploadMultipleFiles" should {
      "return false when jsenabled cookie NOT set" in {
        controller.preferUploadMultipleFiles(FakeRequest()) shouldBe false
      }

      "return false when jsenabled cookie set but uploadMultipleFilesFeature flag NOT set" in {
        controller.preferUploadMultipleFiles(
          FakeRequest().withCookies(Cookie(controller.COOKIE_JSENABLED, "true"))
        ) shouldBe false
      }
    }

    "successRedirect" should {
      "return /file-verification when jsenabled cookie NOT set" in {
        controller.successRedirect(journeyId.value)(FakeRequest()) should endWith(
          "/send-documents-for-customs-check/new/file-verification"
        )
      }

      "return /journey/:journeyId/file-verification when jsenabled cookie set" in {
        controller.successRedirect(journeyId.value)(
          fakeRequest(Cookie(controller.COOKIE_JSENABLED, "true"))
        ) should endWith(
          s"/send-documents-for-customs-check/new/journey/${journeyId.value}/file-verification"
        )
      }
    }

    "errorRedirect" should {
      "return /file-rejected when jsenabled cookie NOT set" in {
        controller.errorRedirect(journeyId.value)(FakeRequest()) should endWith(
          "/send-documents-for-customs-check/new/file-rejected"
        )
      }

      "return /journey/:journeyId/file-rejected when jsenabled cookie set" in {
        controller.errorRedirect(journeyId.value)(
          fakeRequest(Cookie(controller.COOKIE_JSENABLED, "true"))
        ) should endWith(
          s"/send-documents-for-customs-check/new/journey/${journeyId.value}/file-rejected"
        )
      }
    }

    "GET /send-documents-for-customs-check/" should {
      "show the start page" in {
        journey.setState(Start)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/").withHttpHeaders((journey.journeyKey, journeyId.value)).get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.new-or-existing-case.title"))
        result.body should include(htmlEscapedMessage("view.new-or-existing-case.heading"))
        journey.getState shouldBe ChooseNewOrExistingCase()
      }
    }

    "GET /send-documents-for-customs-check/new-or-existing" should {
      "show the choice between new and existing case" in {
        journey.setState(ChooseNewOrExistingCase())
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new-or-existing").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.new-or-existing-case.title"))
        result.body should include(htmlEscapedMessage("view.new-or-existing-case.heading"))
        journey.getState shouldBe ChooseNewOrExistingCase()
      }
    }

    "POST /new-or-existing" should {
      "submit the choice of New and ask next for declaration details" in {
        journey.setState(ChooseNewOrExistingCase())
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "newOrExistingCase" -> "New"
        )

        val result = await(request("/new-or-existing").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.entry-details.title"))
        result.body should include(htmlEscapedMessage("view.entry-details.heading"))
        journey.getState shouldBe EnterEntryDetails()
      }

      "submit the choice of Existing and ask next for case reference number (when continue is true)" in {
        journey.setState(ChooseNewOrExistingCase(continueAmendCaseJourney = true))
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "newOrExistingCase" -> "Existing"
        )

        val result = await(request("/new-or-existing").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.case-reference-number.title"))
        result.body should include(htmlEscapedMessage("view.case-reference-number.heading"))
        journey.getState shouldBe TurnToAmendCaseJourney(true)
      }

      "submit the choice of Existing and ask next for case reference number (when continue is false)" in {
        journey.setState(ChooseNewOrExistingCase(continueAmendCaseJourney = false))
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "newOrExistingCase" -> "Existing"
        )

        val result = await(request("/new-or-existing").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.case-reference-number.title"))
        result.body should include(htmlEscapedMessage("view.case-reference-number.heading"))
        journey.getState shouldBe TurnToAmendCaseJourney(false)
      }

      "submit invalid choice and re-display the form page with error" in {
        journey.setState(ChooseNewOrExistingCase())
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "newOrExistingCase" -> "Foo"
        )

        val result = await(request("/new-or-existing").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitleWithError("view.new-or-existing-case.title"))
        result.body should include(htmlEscapedMessage("view.new-or-existing-case.heading"))
        journey.getState shouldBe ChooseNewOrExistingCase()
      }
    }

    "GET /send-documents-for-customs-check/new/entry-details" should {
      "show declaration details page if at EnterEntryDetails" in {
        journey.setState(EnterEntryDetails())
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/entry-details").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.entry-details.title"))
        result.body should include(htmlEscapedMessage("view.entry-details.heading"))
        journey.getState shouldBe EnterEntryDetails()
      }

      "redisplay pre-filled enter declaration details page" in {
        journey.setState(
          AnswerExportQuestionsRequestType(
            ExportQuestionsStateModel(
              EntryDetails(EPU(235), EntryNumber("A11111X"), today),
              ExportQuestions()
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/entry-details").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.entry-details.title"))
        result.body should include(htmlEscapedMessage("view.entry-details.heading"))
        result.body should (include("235") and include("A11111X"))
        result.body should (include(s"$y") and include(f"$m%02d") and include(f"$d%02d"))
        journey.getState shouldBe EnterEntryDetails(
          Some(EntryDetails(EPU(235), EntryNumber("A11111X"), today)),
          Some(ExportQuestions())
        )
      }

      "show declaration details page if at CreateCaseConfirmation" in {
        journey.setState(
          CreateCaseConfirmation(
            TestData.exportEntryDetails,
            TestData.fullExportQuestions(dateTimeOfArrival),
            Seq(
              UploadedFile(
                "foo-123",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              )
            ),
            TraderServicesResult("TBC", generatedAt),
            CaseSLA(Some(generatedAt.plusHours(2)))
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/entry-details").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.entry-details.title"))
        result.body should include(htmlEscapedMessage("view.entry-details.heading"))
        journey.getState shouldBe EnterEntryDetails()
      }
    }

    "POST /new/entry-details" should {
      "submit the form and ask next for requestType when entryNumber is for export" in {
        journey.setState(EnterEntryDetails(None))
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "entryDate.year"  -> s"$y",
          "entryDate.month" -> f"$m%02d",
          "entryDate.day"   -> f"$d%02d",
          "epu"             -> "235",
          "entryNumber"     -> "A11111X"
        )

        val result = await(request("/new/entry-details").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.requestType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.requestType.heading"))
        journey.getState shouldBe AnswerExportQuestionsRequestType(
          ExportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("A11111X"), today),
            ExportQuestions()
          )
        )
      }

      "submit the form and go next page when entryNumber is for import" in {
        journey.setState(EnterEntryDetails(None))
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "entryDate.year"  -> s"$y",
          "entryDate.month" -> f"$m%02d",
          "entryDate.day"   -> f"$d%02d",
          "epu"             -> "235",
          "entryNumber"     -> "111111X"
        )

        val result = await(request("/new/entry-details").post(payload))

        result.status shouldBe 200
        journey.getState shouldBe AnswerImportQuestionsRequestType(
          ImportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("111111X"), today),
            ImportQuestions()
          )
        )
      }

      "submit invalid form and re-display the form page with errors" in {
        journey.setState(EnterEntryDetails())
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "entryDate.year"  -> "202a",
          "entryDate.month" -> "00",
          "entryDate.day"   -> "44",
          "epu"             -> "AAA",
          "entryNumber"     -> "A11X"
        )

        val result = await(request("/new/entry-details").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitleWithError("view.entry-details.title"))
        result.body should include(htmlEscapedMessage("view.entry-details.heading"))
        journey.getState shouldBe EnterEntryDetails()
      }
    }

    "GET /new/export/request-type" should {
      "show the export request type question page" in {
        val state = AnswerExportQuestionsRequestType(
          ExportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("A11111X"), today),
            ExportQuestions()
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/export/request-type").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.requestType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.requestType.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /new/export/request-type" should {
      "submit the form and ask next for routeType" in {
        journey.setState(
          AnswerExportQuestionsRequestType(
            ExportQuestionsStateModel(
              EntryDetails(EPU(235), EntryNumber("A11111X"), today),
              ExportQuestions()
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("requestType" -> "New")

        val result = await(request("/new/export/request-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.routeType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.routeType.heading"))
        journey.getState shouldBe AnswerExportQuestionsRouteType(
          ExportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("A11111X"), today),
            ExportQuestions(requestType = Some(ExportRequestType.New))
          )
        )
      }

      "submit invalid form and re-display the form page with errors" in {
        val state = AnswerExportQuestionsRequestType(
          ExportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("A11111X"), today),
            ExportQuestions()
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("requestType" -> "Foo")

        val result = await(request("/new/export/request-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitleWithError("view.export-questions.requestType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.requestType.heading"))
        journey.getState shouldBe state
      }
    }

    "GET /new/export/route-type" should {
      "show the export route type question page" in {
        val state = AnswerExportQuestionsRouteType(
          ExportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("A11111X"), today),
            ExportQuestions(requestType = Some(ExportRequestType.C1601))
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/export/route-type").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.routeType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.routeType.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /new/export/route-type" should {
      "submit the form and ask next for hasPriorityGoods" in {
        journey.setState(
          AnswerExportQuestionsRouteType(
            ExportQuestionsStateModel(
              EntryDetails(EPU(235), EntryNumber("A11111X"), today),
              ExportQuestions(requestType = Some(ExportRequestType.C1602))
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("routeType" -> "Route2")

        val result = await(request("/new/export/route-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.hasPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.hasPriorityGoods.heading"))
        journey.getState shouldBe AnswerExportQuestionsHasPriorityGoods(
          ExportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("A11111X"), today),
            ExportQuestions(requestType = Some(ExportRequestType.C1602), routeType = Some(ExportRouteType.Route2))
          )
        )
      }

      "submit the form and ask next for reason when route type requires mandatory reason for export" in {
        journey.setState(
          AnswerExportQuestionsRouteType(
            ExportQuestionsStateModel(
              EntryDetails(EPU(235), EntryNumber("A11111X"), today),
              ExportQuestions(requestType = Some(ExportRequestType.New))
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("routeType" -> "Route3")

        val result = await(request("/new/export/route-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("form.export-questions.reason-text.title"))
        result.body should include(htmlEscapedMessage("form.export-questions.reason-text.heading"))
        journey.getState shouldBe AnswerExportQuestionsReason(
          ExportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("A11111X"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route3)
            )
          )
        )
      }
      "submit the form and ask next for reason when request type requires mandatory reason for export" in {
        journey.setState(
          AnswerExportQuestionsRouteType(
            ExportQuestionsStateModel(
              EntryDetails(EPU(235), EntryNumber("A11111X"), today),
              ExportQuestions(requestType = Some(ExportRequestType.Cancellation))
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("routeType" -> "Route2")

        val result = await(request("/new/export/route-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("form.export-questions.reason-text.title"))
        result.body should include(htmlEscapedMessage("form.export-questions.reason-text.heading"))
        journey.getState shouldBe AnswerExportQuestionsReason(
          ExportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("A11111X"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.Cancellation),
              routeType = Some(ExportRouteType.Route2)
            )
          )
        )
      }

      "submit invalid form and re-display the form page with errors" in {
        val state = AnswerExportQuestionsRouteType(
          ExportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("A11111X"), today),
            ExportQuestions(requestType = Some(ExportRequestType.C1602))
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("routeType" -> "Foo")

        val result = await(request("/new/export/route-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitleWithError("view.export-questions.routeType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.routeType.heading"))
        journey.getState shouldBe state
      }
    }
    "GET /new/export/has-priority-goods" should {
      "show the export has priority goods page" in {
        val state = AnswerExportQuestionsHasPriorityGoods(
          ExportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("A11111X"), today),
            ExportQuestions(requestType = Some(ExportRequestType.C1603), routeType = Some(ExportRouteType.Route6))
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/export/has-priority-goods").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.hasPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.hasPriorityGoods.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /new/export/has-priority-goods" should {
      "submit YES choice and ask next for which priority goods" in {
        journey.setState(
          AnswerExportQuestionsHasPriorityGoods(
            ExportQuestionsStateModel(
              EntryDetails(EPU(235), EntryNumber("A11111X"), today),
              ExportQuestions(requestType = Some(ExportRequestType.New), routeType = Some(ExportRouteType.Route2))
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("hasPriorityGoods" -> "yes")

        val result = await(request("/new/export/has-priority-goods").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.whichPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.whichPriorityGoods.heading"))
        journey.getState shouldBe AnswerExportQuestionsWhichPriorityGoods(
          ExportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("A11111X"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route2),
              hasPriorityGoods = Some(true)
            )
          )
        )
      }

      "submit NO choice and ask next for transport type" in {
        journey.setState(
          AnswerExportQuestionsHasPriorityGoods(
            ExportQuestionsStateModel(
              EntryDetails(EPU(235), EntryNumber("A11111X"), today),
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route2)
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("hasPriorityGoods" -> "no")

        val result = await(request("/new/export/has-priority-goods").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.freightType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.freightType.heading"))
        journey.getState shouldBe AnswerExportQuestionsFreightType(
          ExportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("A11111X"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route2),
              hasPriorityGoods = Some(false)
            )
          )
        )
      }

      "submit empty choice and re-display the form with error" in {
        val state = AnswerExportQuestionsHasPriorityGoods(
          ExportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("A11111X"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route2)
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("hasPriorityGoods" -> "")

        val result = await(request("/new/export/has-priority-goods").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitleWithError("view.export-questions.hasPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.hasPriorityGoods.heading"))
        journey.getState shouldBe state
      }
    }

    "GET /new/export/which-priority-goods" should {
      "show the export which priority goods page" in {
        val state = AnswerExportQuestionsWhichPriorityGoods(
          ExportQuestionsStateModel(
            EntryDetails(EPU(230), EntryNumber("A11111Z"), today),
            ExportQuestions(requestType = Some(ExportRequestType.C1603), routeType = Some(ExportRouteType.Route6))
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/export/which-priority-goods").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.whichPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.whichPriorityGoods.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /new/export/which-priority-goods" should {
      "submit selected priority goods and ask next for transport type" in {
        journey.setState(
          AnswerExportQuestionsWhichPriorityGoods(
            ExportQuestionsStateModel(
              EntryDetails(EPU(236), EntryNumber("X11111X"), today),
              ExportQuestions(requestType = Some(ExportRequestType.C1603), routeType = Some(ExportRouteType.Route3))
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("priorityGoods" -> "LiveAnimals")

        val result = await(request("/new/export/which-priority-goods").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.freightType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.freightType.heading"))
        journey.getState shouldBe AnswerExportQuestionsFreightType(
          ExportQuestionsStateModel(
            EntryDetails(EPU(236), EntryNumber("X11111X"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1603),
              routeType = Some(ExportRouteType.Route3),
              priorityGoods = Some(ExportPriorityGoods.LiveAnimals)
            )
          )
        )
      }

      "submit empty priority goods and re-display the form with error" in {
        val state = AnswerExportQuestionsWhichPriorityGoods(
          ExportQuestionsStateModel(
            EntryDetails(EPU(236), EntryNumber("X11111X"), today),
            ExportQuestions(requestType = Some(ExportRequestType.C1603), routeType = Some(ExportRouteType.Route3))
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("priorityGoods" -> "")

        val result = await(request("/new/export/which-priority-goods").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitleWithError("view.export-questions.whichPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.whichPriorityGoods.heading"))
        journey.getState shouldBe state
      }
    }

    "GET /new/export/transport-type" should {
      "show the export transport type page" in {
        val state = AnswerExportQuestionsFreightType(
          ExportQuestionsStateModel(
            EntryDetails(EPU(230), EntryNumber("A11111Z"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1603),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HumanRemains)
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/export/transport-type").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.freightType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.freightType.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /new/export/transport-type" should {
      "submit selected RORO transport type without C1601 and ask next for contact info" in {
        journey.setState(
          AnswerExportQuestionsFreightType(
            ExportQuestionsStateModel(
              EntryDetails(EPU(236), EntryNumber("X11111X"), today),
              ExportQuestions(
                requestType = Some(ExportRequestType.C1603),
                routeType = Some(ExportRouteType.Route3),
                priorityGoods = Some(ExportPriorityGoods.LiveAnimals)
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("freightType" -> "RORO")

        val result = await(request("/new/export/transport-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.contactInfo.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.contactInfo.heading"))

        journey.getState shouldBe AnswerExportQuestionsContactInfo(
          ExportQuestionsStateModel(
            EntryDetails(EPU(236), EntryNumber("X11111X"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1603),
              routeType = Some(ExportRouteType.Route3),
              priorityGoods = Some(ExportPriorityGoods.LiveAnimals),
              freightType = Some(ExportFreightType.RORO)
            )
          )
        )
      }

      "submit selected Air transport type with C1601 and ask next for mandatory vessel details" in {
        journey.setState(
          AnswerExportQuestionsFreightType(
            ExportQuestionsStateModel(
              EntryDetails(EPU(236), EntryNumber("X11111X"), today),
              ExportQuestions(
                requestType = Some(ExportRequestType.C1601),
                routeType = Some(ExportRouteType.Route3),
                priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks)
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("freightType" -> "Air")

        val result = await(request("/new/export/transport-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.vessel-details.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.vessel-details.heading"))

        journey.getState shouldBe AnswerExportQuestionsMandatoryVesselInfo(
          ExportQuestionsStateModel(
            EntryDetails(EPU(236), EntryNumber("X11111X"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1601),
              routeType = Some(ExportRouteType.Route3),
              priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
              freightType = Some(ExportFreightType.Air)
            )
          )
        )
      }

      "submit empty transport type and re-display the form with error" in {
        val state = AnswerExportQuestionsFreightType(
          ExportQuestionsStateModel(
            EntryDetails(EPU(236), EntryNumber("X11111X"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1601),
              routeType = Some(ExportRouteType.Route3),
              priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks)
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("freightType" -> "")

        val result = await(request("/new/export/transport-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitleWithError("view.export-questions.freightType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.freightType.heading"))

        journey.getState shouldBe state
      }
    }

    "GET /new/export/transport-information-required" should {
      "show the export vessel details page" in {
        val state = AnswerExportQuestionsMandatoryVesselInfo(
          ExportQuestionsStateModel(
            EntryDetails(EPU(230), EntryNumber("A11111Z"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1601),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HumanRemains),
              freightType = Some(ExportFreightType.Air)
            )
          )
        )

        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/export/transport-information-required").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.vessel-details.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.vessel-details.heading"))
        journey.getState shouldBe state
      }
    }

    "GET /new/export/transport-information-required" should {
      "show the export vessel details page when routeType=Hold" in {
        val state = AnswerExportQuestionsMandatoryVesselInfo(
          ExportQuestionsStateModel(
            EntryDetails(EPU(230), EntryNumber("A11111Z"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1603),
              routeType = Some(ExportRouteType.Hold),
              priorityGoods = Some(ExportPriorityGoods.HumanRemains),
              freightType = Some(ExportFreightType.Air)
            )
          )
        )

        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/export/transport-information-required").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.vessel-details.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.vessel-details.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /new/export/transport-information-required" should {
      "submit mandatory vessel details and ask next for contact details" in {
        journey.setState(
          AnswerExportQuestionsMandatoryVesselInfo(
            ExportQuestionsStateModel(
              EntryDetails(EPU(230), EntryNumber("A11111Z"), today),
              ExportQuestions(
                requestType = Some(ExportRequestType.C1602),
                routeType = Some(ExportRouteType.Route6),
                priorityGoods = Some(ExportPriorityGoods.HumanRemains),
                freightType = Some(ExportFreightType.Air)
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val dateTimeOfArrival = dateTime.plusDays(1).truncatedTo(ChronoUnit.MINUTES)

        val payload = Map(
          "vesselName"              -> "Foo Bar",
          "dateOfDeparture.year"    -> f"${dateTimeOfArrival.get(ChronoField.YEAR)}",
          "dateOfDeparture.month"   -> f"${dateTimeOfArrival.get(ChronoField.MONTH_OF_YEAR)}%02d",
          "dateOfDeparture.day"     -> f"${dateTimeOfArrival.get(ChronoField.DAY_OF_MONTH)}%02d",
          "timeOfDeparture.hour"    -> f"${dateTimeOfArrival.get(ChronoField.HOUR_OF_DAY)}%02d",
          "timeOfDeparture.minutes" -> f"${dateTimeOfArrival.get(ChronoField.MINUTE_OF_HOUR)}%02d"
        )

        val result = await(request("/new/export/transport-information-required").post(payload))

        result.status shouldBe 200

        journey.getState shouldBe AnswerExportQuestionsContactInfo(
          ExportQuestionsStateModel(
            EntryDetails(EPU(230), EntryNumber("A11111Z"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1602),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HumanRemains),
              freightType = Some(ExportFreightType.Air),
              vesselDetails = Some(
                VesselDetails(
                  vesselName = Some("Foo Bar"),
                  dateOfArrival = Some(dateTimeOfArrival.toLocalDate),
                  timeOfArrival = Some(dateTimeOfArrival.toLocalTime)
                )
              )
            )
          )
        )
      }

      "submit incomplete vessel details and re-display the form page with error" in {
        val state = AnswerExportQuestionsMandatoryVesselInfo(
          ExportQuestionsStateModel(
            EntryDetails(EPU(230), EntryNumber("A11111Z"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1602),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HumanRemains),
              freightType = Some(ExportFreightType.Air)
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "vesselName" -> "Foo Bar"
        )

        val result = await(request("/new/export/transport-information-required").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitleWithError("view.export-questions.vessel-details.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.vessel-details.heading"))

        journey.getState shouldBe state
      }
    }

    "GET /new/export/transport-information" should {
      "show the export vessel details page" in {
        val state = AnswerExportQuestionsOptionalVesselInfo(
          ExportQuestionsStateModel(
            EntryDetails(EPU(230), EntryNumber("A11111Z"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HumanRemains),
              freightType = Some(ExportFreightType.Air)
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/export/transport-information").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.vessel-details.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.vessel-details.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /new/export/transport-information" should {
      "submit optional vessel details and ask next for contact details" in {
        journey.setState(
          AnswerExportQuestionsOptionalVesselInfo(
            ExportQuestionsStateModel(
              EntryDetails(EPU(230), EntryNumber("A11111Z"), today),
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route6),
                priorityGoods = Some(ExportPriorityGoods.HumanRemains),
                freightType = Some(ExportFreightType.Air)
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val dateTimeOfArrival = dateTime.plusDays(1).truncatedTo(ChronoUnit.MINUTES)

        val payload = Map(
          "vesselName"              -> "Foo Bar",
          "dateOfDeparture.year"    -> f"${dateTimeOfArrival.get(ChronoField.YEAR)}",
          "dateOfDeparture.month"   -> f"${dateTimeOfArrival.get(ChronoField.MONTH_OF_YEAR)}%02d",
          "dateOfDeparture.day"     -> f"${dateTimeOfArrival.get(ChronoField.DAY_OF_MONTH)}%02d",
          "timeOfDeparture.hour"    -> f"${dateTimeOfArrival.get(ChronoField.HOUR_OF_DAY)}%02d",
          "timeOfDeparture.minutes" -> f"${dateTimeOfArrival.get(ChronoField.MINUTE_OF_HOUR)}%02d"
        )

        val result = await(request("/new/export/transport-information").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.contactInfo.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.contactInfo.heading"))

        journey.getState shouldBe AnswerExportQuestionsContactInfo(
          ExportQuestionsStateModel(
            EntryDetails(EPU(230), EntryNumber("A11111Z"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HumanRemains),
              freightType = Some(ExportFreightType.Air),
              vesselDetails = Some(
                VesselDetails(
                  vesselName = Some("Foo Bar"),
                  dateOfArrival = Some(dateTimeOfArrival.toLocalDate),
                  timeOfArrival = Some(dateTimeOfArrival.toLocalTime)
                )
              )
            )
          )
        )
      }

      "submit none vessel details and ask next for contact details" in {
        journey.setState(
          AnswerExportQuestionsOptionalVesselInfo(
            ExportQuestionsStateModel(
              EntryDetails(EPU(230), EntryNumber("A11111Z"), today),
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route6),
                priorityGoods = Some(ExportPriorityGoods.HumanRemains),
                freightType = Some(ExportFreightType.Air)
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map[String, String]()

        val result = await(request("/new/export/transport-information").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.contactInfo.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.contactInfo.heading"))

        journey.getState shouldBe AnswerExportQuestionsContactInfo(
          ExportQuestionsStateModel(
            EntryDetails(EPU(230), EntryNumber("A11111Z"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HumanRemains),
              freightType = Some(ExportFreightType.Air),
              vesselDetails = Some(VesselDetails())
            )
          )
        )
      }

      "submit invalid vessel details and re-display the form page" in {
        val state = AnswerExportQuestionsOptionalVesselInfo(
          ExportQuestionsStateModel(
            EntryDetails(EPU(230), EntryNumber("A11111Z"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HumanRemains),
              freightType = Some(ExportFreightType.Air)
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("dateOfDeparture.year" -> "202A")

        val result = await(request("/new/export/transport-information").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitleWithError("view.export-questions.vessel-details.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.vessel-details.heading"))

        journey.getState shouldBe state
      }
    }

    "GET /new/export/contact-information" should {
      "show the export contact information question page" in {
        journey.setState(
          AnswerExportQuestionsContactInfo(
            ExportQuestionsStateModel(
              EntryDetails(EPU(235), EntryNumber("111111X"), today),
              ExportQuestions()
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/export/contact-information").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.contactInfo.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.contactInfo.heading"))
        journey.getState shouldBe AnswerExportQuestionsContactInfo(
          ExportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("111111X"), today),
            ExportQuestions()
          )
        )
      }
    }

    "POST /new/export/contact-information" should {
      "go to upload file page when an email submitted" in {
        journey.setState(
          AnswerExportQuestionsContactInfo(
            ExportQuestionsStateModel(
              EntryDetails(EPU(235), EntryNumber("111111X"), today),
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route6),
                priorityGoods = Some(ExportPriorityGoods.HumanRemains),
                freightType = Some(ExportFreightType.Air)
              )
            )
          )
        )
        val callbackUrl =
          appConfig.baseInternalCallbackUrl + s"/internal/callback-from-upscan/new/journey/${SHA256
              .compute(journeyId.value)}"
        givenUpscanInitiateSucceeds(callbackUrl)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "contactEmail" -> "someone@email.com"
        )
        val result = await(request("/new/export/contact-information").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-file.first.title"))
        result.body should include(htmlEscapedMessage("view.upload-file.first.heading"))

        journey.getState shouldBe UploadFile(
          hostData = FileUploadHostData(
            EntryDetails(EPU(235), EntryNumber("111111X"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HumanRemains),
              freightType = Some(ExportFreightType.Air),
              contactInfo = Some(ExportContactInfo(contactEmail = "someone@email.com"))
            )
          ),
          reference = "11370e18-6e24-453e-b45a-76d3e32ea33d",
          uploadRequest = UploadRequest(
            href = "https://bucketName.s3.eu-west-2.amazonaws.com",
            fields = Map(
              "Content-Type"            -> "application/xml",
              "acl"                     -> "private",
              "key"                     -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
              "policy"                  -> "xxxxxxxx==",
              "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
              "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
              "x-amz-date"              -> "yyyyMMddThhmmssZ",
              "x-amz-meta-callback-url" -> callbackUrl,
              "x-amz-signature"         -> "xxxx",
              "success_action_redirect" -> "https://myservice.com/nextPage",
              "error_action_redirect"   -> "https://myservice.com/errorPage"
            )
          ),
          fileUploads = FileUploads(files =
            Seq(FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"))
          )
        )
      }

      "submit invalid contact info and re-display the form page with error" in {
        val state = AnswerExportQuestionsContactInfo(
          ExportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("111111X"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HumanRemains),
              freightType = Some(ExportFreightType.Air)
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "contactName"   -> "Full Name",
          "contactEmail"  -> "someone@email.com",
          "contactNumber" -> "000000"
        )
        val result = await(request("/new/export/contact-information").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitleWithError("view.export-questions.contactInfo.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.contactInfo.heading"))

        journey.getState shouldBe state
      }
    }

    "GET /new/export/check-your-answers" should {
      "show the export questions summary page" in {

        val state = ExportQuestionsSummary(
          ExportQuestionsStateModel(TestData.exportEntryDetails, TestData.fullExportQuestions(dateTimeOfArrival))
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/export/check-your-answers").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.summary.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.summary.heading"))
        result.body should include(routes.CreateCaseJourneyController.showFileUpload.url)
        journey.getState shouldBe state
      }
    }

    "GET /new/import/request-type" should {
      "show the import request type question page" in {
        val state = AnswerImportQuestionsRequestType(
          ImportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("111111X"), today),
            ImportQuestions()
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/import/request-type").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.requestType.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.requestType.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /new/import/request-type" should {
      "submit the form and ask next for route type" in {
        journey.setState(
          AnswerImportQuestionsRequestType(
            ImportQuestionsStateModel(
              EntryDetails(EPU(444), EntryNumber("011111X"), today),
              ImportQuestions(
                requestType = Some(ImportRequestType.Cancellation),
                routeType = Some(ImportRouteType.Route6)
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("requestType" -> "New")

        val result = await(request("/new/import/request-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.routeType.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.routeType.heading"))
        journey.getState shouldBe AnswerImportQuestionsRouteType(
          ImportQuestionsStateModel(
            EntryDetails(EPU(444), EntryNumber("011111X"), today),
            ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route6))
          )
        )
      }
    }

    "GET /new/import/route-type" should {
      "show the import route type question page" in {
        val state = AnswerImportQuestionsRouteType(
          ImportQuestionsStateModel(
            EntryDetails(EPU(532), EntryNumber("111111X"), today),
            ImportQuestions(requestType = Some(ImportRequestType.New))
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/import/route-type").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.routeType.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.routeType.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /new/import/route-type" should {
      "submit the form and ask next for hasPriorityGoods" in {
        journey.setState(
          AnswerImportQuestionsRouteType(
            ImportQuestionsStateModel(
              EntryDetails(EPU(444), EntryNumber("011111X"), today),
              ImportQuestions(requestType = Some(ImportRequestType.New))
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("routeType" -> "Route6")

        val result = await(request("/new/import/route-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.hasPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.hasPriorityGoods.heading"))
        journey.getState shouldBe AnswerImportQuestionsHasPriorityGoods(
          ImportQuestionsStateModel(
            EntryDetails(EPU(444), EntryNumber("011111X"), today),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route6)
            )
          )
        )
      }
      "submit the form and ask next for reason when route type requires mandatory reason for import" in {
        journey.setState(
          AnswerImportQuestionsRouteType(
            ImportQuestionsStateModel(
              EntryDetails(EPU(444), EntryNumber("011111X"), today),
              ImportQuestions(requestType = Some(ImportRequestType.New))
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("routeType" -> "Route3")

        val result = await(request("/new/import/route-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("form.import-questions.reason-text.title"))
        result.body should include(htmlEscapedMessage("form.import-questions.reason-text.heading"))
        journey.getState shouldBe AnswerImportQuestionsReason(
          ImportQuestionsStateModel(
            EntryDetails(EPU(444), EntryNumber("011111X"), today),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route3)
            )
          )
        )
      }
      "submit the form and ask next for reason when request type requires mandatory reason for import" in {
        journey.setState(
          AnswerImportQuestionsRouteType(
            ImportQuestionsStateModel(
              EntryDetails(EPU(444), EntryNumber("011111X"), today),
              ImportQuestions(requestType = Some(ImportRequestType.Cancellation))
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("routeType" -> "Route6")

        val result = await(request("/new/import/route-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("form.import-questions.reason-text.title"))
        result.body should include(htmlEscapedMessage("form.import-questions.reason-text.heading"))
        journey.getState shouldBe AnswerImportQuestionsReason(
          ImportQuestionsStateModel(
            EntryDetails(EPU(444), EntryNumber("011111X"), today),
            ImportQuestions(
              requestType = Some(ImportRequestType.Cancellation),
              routeType = Some(ImportRouteType.Route6)
            )
          )
        )
      }
    }

    "GET /new/export/reason" should {
      "show the reason page in the export journey" in {
        val state = AnswerExportQuestionsReason(
          ExportQuestionsStateModel(
            EntryDetails(EPU(110), EntryNumber("911111X"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.Cancellation),
              routeType = Some(ExportRouteType.Route6)
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/export/reason").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("form.export-questions.reason-text.title"))
        result.body should include(htmlEscapedMessage("form.export-questions.reason-text.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /new/export/reason" should {

      "submit no reason and re-display the form page with error" in {
        val state = AnswerExportQuestionsReason(
          ExportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("111111X"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.Cancellation),
              routeType = Some(ExportRouteType.Route6)
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("reasonText" -> "")
        val result = await(request("/new/export/reason").post(payload))
        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitleWithError("form.export-questions.reason-text.title"))
        result.body should include(htmlEscapedMessage("form.export-questions.reason-text.heading"))
        journey.getState shouldBe state
      }

      "submit invalid text length reason and re-display the form page with error for export" in {
        val reasonText = Random.alphanumeric.take(1025).mkString
        val state = AnswerExportQuestionsReason(
          ExportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("111111X"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.Cancellation),
              routeType = Some(ExportRouteType.Route6),
              reason = Some(reasonText)
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val payload = Map("reasonText" -> reasonText)
        val result = await(request("/new/export/reason").post(payload))

        result.status shouldBe 200

        result.body should include(htmlEscapedPageTitleWithError("form.export-questions.reason-text.title"))
        result.body should include(htmlEscapedMessage("form.export-questions.reason-text.heading"))
        journey.getState shouldBe state
      }

      "ask for export has priority goods question" in {
        val state = AnswerExportQuestionsHasPriorityGoods(
          ExportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("111111X"), today),
            ExportQuestions(
              requestType = Some(ExportRequestType.Cancellation),
              routeType = Some(ExportRouteType.Route6),
              reason = Some("bankrupt")
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("reasonText" -> "bankrupt")
        val result = await(request("/new/export/reason").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.export-questions.hasPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.hasPriorityGoods.heading"))
        journey.getState shouldBe state
      }
    }

    "GET /new/import/reason" should {
      "show the reason page in import journey" in {
        val state = AnswerImportQuestionsReason(
          ImportQuestionsStateModel(
            EntryDetails(EPU(110), EntryNumber("911111X"), today),
            ImportQuestions(
              requestType = Some(ImportRequestType.Cancellation),
              routeType = Some(ImportRouteType.Route6)
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/import/reason").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("form.import-questions.reason-text.title"))
        result.body should include(htmlEscapedMessage("form.import-questions.reason-text.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /new/import/reason" should {

      "submit no reason and re-display the form page with error" in {
        val state = AnswerImportQuestionsReason(
          ImportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("111111X"), today),
            ImportQuestions(
              requestType = Some(ImportRequestType.Cancellation),
              routeType = Some(ImportRouteType.Route6)
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("reasonText" -> "")
        val result = await(request("/new/import/reason").post(payload))
        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitleWithError("form.import-questions.reason-text.title"))
        result.body should include(htmlEscapedMessage("form.import-questions.reason-text.heading"))
        journey.getState shouldBe state
      }

      "submit invalid text length reason and re-display the form page with error for import" in {
        val reasonText = Random.alphanumeric.take(1025).mkString
        val state = AnswerImportQuestionsReason(
          ImportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("111111X"), today),
            ImportQuestions(
              requestType = Some(ImportRequestType.Cancellation),
              routeType = Some(ImportRouteType.Route6),
              reason = Some(reasonText)
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val payload = Map("reasonText" -> reasonText)
        val result = await(request("/new/import/reason").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitleWithError("form.import-questions.reason-text.title"))
        result.body should include(htmlEscapedMessage("form.import-questions.reason-text.heading"))
        journey.getState shouldBe state
      }

      "ask for import has priority goods question" in {
        val state = AnswerImportQuestionsHasPriorityGoods(
          ImportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("111111X"), today),
            ImportQuestions(
              requestType = Some(ImportRequestType.Cancellation),
              routeType = Some(ImportRouteType.Route6),
              reason = Some("bankrupt")
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("reasonText" -> "bankrupt")
        val result = await(request("/new/import/reason").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.import-questions.hasPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.hasPriorityGoods.heading"))
        journey.getState shouldBe state
      }
    }

    "GET /new/import/has-priority-goods" should {
      "show the import has priority goods page" in {
        val state = AnswerImportQuestionsHasPriorityGoods(
          ImportQuestionsStateModel(
            EntryDetails(EPU(110), EntryNumber("911111X"), today),
            ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route6))
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/import/has-priority-goods").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.hasPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.hasPriorityGoods.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /new/import/has-priority-goods" should {
      "submit YES choice and ask next for which priority goods" in {
        journey.setState(
          AnswerImportQuestionsHasPriorityGoods(
            ImportQuestionsStateModel(
              EntryDetails(EPU(101), EntryNumber("811111X"), today),
              ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route2))
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("hasPriorityGoods" -> "yes")

        val result = await(request("/new/import/has-priority-goods").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.whichPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.whichPriorityGoods.heading"))
        journey.getState shouldBe AnswerImportQuestionsWhichPriorityGoods(
          ImportQuestionsStateModel(
            EntryDetails(EPU(101), EntryNumber("811111X"), today),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route2),
              hasPriorityGoods = Some(true)
            )
          )
        )
      }

      "submit NO choice and ask next for transport type" in {
        journey.setState(
          AnswerImportQuestionsHasPriorityGoods(
            ImportQuestionsStateModel(
              EntryDetails(EPU(100), EntryNumber("711111X"), today),
              ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route2))
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("hasPriorityGoods" -> "no")

        val result = await(request("/new/import/has-priority-goods").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.hasALVS.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.hasALVS.heading"))
        journey.getState shouldBe AnswerImportQuestionsALVS(
          ImportQuestionsStateModel(
            EntryDetails(EPU(100), EntryNumber("711111X"), today),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route2),
              hasPriorityGoods = Some(false)
            )
          )
        )
      }
    }

    "GET /new/import/which-priority-goods" should {
      "show the import which priority goods page" in {
        val state = AnswerImportQuestionsWhichPriorityGoods(
          ImportQuestionsStateModel(
            EntryDetails(EPU(230), EntryNumber("111111Z"), today),
            ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route6))
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/import/which-priority-goods").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.whichPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.whichPriorityGoods.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /new/import/which-priority-goods" should {
      "submit selected priority goods and ask next for automatic licence verification" in {
        journey.setState(
          AnswerImportQuestionsWhichPriorityGoods(
            ImportQuestionsStateModel(
              EntryDetails(EPU(236), EntryNumber("011111X"), today),
              ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route3))
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("priorityGoods" -> "LiveAnimals")

        val result = await(request("/new/import/which-priority-goods").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.hasALVS.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.hasALVS.heading"))
        journey.getState shouldBe AnswerImportQuestionsALVS(
          ImportQuestionsStateModel(
            EntryDetails(EPU(236), EntryNumber("011111X"), today),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route3),
              priorityGoods = Some(ImportPriorityGoods.LiveAnimals)
            )
          )
        )
      }
    }

    "GET /new/import/automatic-licence-verification" should {
      "show the import has ALVS page" in {
        val state = AnswerImportQuestionsALVS(
          ImportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("711111X"), today),
            ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route2))
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/import/automatic-licence-verification").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.hasALVS.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.hasALVS.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /new/import/automatic-licence-verification" should {
      for (hasALVS <- Seq(true, false))
        s"submit ${if (hasALVS) "YES" else "NO"} choice and ask next for transport type" in {
          journey.setState(
            AnswerImportQuestionsALVS(
              ImportQuestionsStateModel(
                EntryDetails(EPU(235), EntryNumber("011111X"), today),
                ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route2))
              )
            )
          )
          givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

          val payload = Map("hasALVS" -> { if (hasALVS) "yes" else "no" })

          val result = await(request("/new/import/automatic-licence-verification").post(payload))

          result.status shouldBe 200
          result.body should include(
            htmlEscapedMessage("view.import-questions.freightType.title") + " - " + htmlEscapedMessage(
              "site.serviceName"
            ) + " - " + htmlEscapedMessage("site.govuk")
          )
          result.body should include(htmlEscapedMessage("view.import-questions.freightType.heading"))
          journey.getState shouldBe AnswerImportQuestionsFreightType(
            ImportQuestionsStateModel(
              EntryDetails(EPU(235), EntryNumber("011111X"), today),
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route2),
                hasALVS = Some(hasALVS)
              )
            )
          )
        }
    }

    "GET /new/import/transport-type" should {
      "show the import transport type page" in {
        val state = AnswerImportQuestionsFreightType(
          ImportQuestionsStateModel(
            EntryDetails(EPU(230), EntryNumber("311111Z"), today),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route6)
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/import/transport-type").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.freightType.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.freightType.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /new/import/transport-type" should {
      "submit selected transport type and ask next for contact info" in {
        journey.setState(
          AnswerImportQuestionsFreightType(
            ImportQuestionsStateModel(
              EntryDetails(EPU(236), EntryNumber("211111X"), today),
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route3)
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("freightType" -> "RORO")

        val result = await(request("/new/import/transport-type").post(payload))

        result.status shouldBe 200

        journey.getState shouldBe AnswerImportQuestionsContactInfo(
          ImportQuestionsStateModel(
            EntryDetails(EPU(236), EntryNumber("211111X"), today),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route3),
              freightType = Some(ImportFreightType.RORO)
            )
          )
        )
      }

      "submit selected transport type and ask next for mandatory vessel details" in {
        journey.setState(
          AnswerImportQuestionsFreightType(
            ImportQuestionsStateModel(
              EntryDetails(EPU(100), EntryNumber("011111X"), today),
              ImportQuestions(
                routeType = Some(ImportRouteType.Hold),
                requestType = Some(ImportRequestType.New)
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("freightType" -> "Maritime")

        val result = await(request("/new/import/transport-type").post(payload))

        result.status shouldBe 200

        journey.getState shouldBe AnswerImportQuestionsMandatoryVesselInfo(
          ImportQuestionsStateModel(
            EntryDetails(EPU(100), EntryNumber("011111X"), today),
            ImportQuestions(
              routeType = Some(ImportRouteType.Hold),
              requestType = Some(ImportRequestType.New),
              freightType = Some(ImportFreightType.Maritime)
            )
          )
        )
      }
    }

    "GET /new/import/transport-information" should {
      "show the import vessel details page" in {
        val state = AnswerImportQuestionsOptionalVesselInfo(
          ImportQuestionsStateModel(
            EntryDetails(EPU(230), EntryNumber("111111Z"), today),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route6),
              priorityGoods = Some(ImportPriorityGoods.HumanRemains),
              freightType = Some(ImportFreightType.Air)
            )
          )
        )

        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/import/transport-information").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.vessel-details.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.vessel-details.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /new/import/transport-information" should {
      "submit optional vessel details and ask next for contact details" in {
        journey.setState(
          AnswerImportQuestionsOptionalVesselInfo(
            ImportQuestionsStateModel(
              EntryDetails(EPU(230), EntryNumber("111111Z"), today),
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route6),
                priorityGoods = Some(ImportPriorityGoods.HumanRemains),
                freightType = Some(ImportFreightType.Air)
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val dateTimeOfArrival = dateTime.plusDays(1).truncatedTo(ChronoUnit.MINUTES)

        val payload = Map(
          "vesselName"            -> "Foo Bar",
          "dateOfArrival.year"    -> f"${dateTimeOfArrival.get(ChronoField.YEAR)}",
          "dateOfArrival.month"   -> f"${dateTimeOfArrival.get(ChronoField.MONTH_OF_YEAR)}%02d",
          "dateOfArrival.day"     -> f"${dateTimeOfArrival.get(ChronoField.DAY_OF_MONTH)}%02d",
          "timeOfArrival.hour"    -> f"${dateTimeOfArrival.get(ChronoField.HOUR_OF_DAY)}%02d",
          "timeOfArrival.minutes" -> f"${dateTimeOfArrival.get(ChronoField.MINUTE_OF_HOUR)}%02d"
        )

        val result = await(request("/new/import/transport-information").post(payload))

        result.status shouldBe 200

        journey.getState shouldBe AnswerImportQuestionsContactInfo(
          ImportQuestionsStateModel(
            EntryDetails(EPU(230), EntryNumber("111111Z"), today),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route6),
              priorityGoods = Some(ImportPriorityGoods.HumanRemains),
              freightType = Some(ImportFreightType.Air),
              vesselDetails = Some(
                VesselDetails(
                  vesselName = Some("Foo Bar"),
                  dateOfArrival = Some(dateTimeOfArrival.toLocalDate),
                  timeOfArrival = Some(dateTimeOfArrival.toLocalTime)
                )
              )
            )
          )
        )
      }

      "submit none vessel details and ask next for contact details" in {
        journey.setState(
          AnswerImportQuestionsOptionalVesselInfo(
            ImportQuestionsStateModel(
              EntryDetails(EPU(230), EntryNumber("A11111Z"), today),
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route6),
                priorityGoods = Some(ImportPriorityGoods.HumanRemains),
                freightType = Some(ImportFreightType.Air)
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map[String, String]()

        val result = await(request("/new/import/transport-information").post(payload))

        result.status shouldBe 200

        journey.getState shouldBe AnswerImportQuestionsContactInfo(
          ImportQuestionsStateModel(
            EntryDetails(EPU(230), EntryNumber("A11111Z"), today),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route6),
              priorityGoods = Some(ImportPriorityGoods.HumanRemains),
              freightType = Some(ImportFreightType.Air),
              vesselDetails = Some(VesselDetails())
            )
          )
        )
      }
    }

    "POST /new/import/transport-information-required" should {
      "submit mandatory vessel details and ask next for contact details" in {
        journey.setState(
          AnswerImportQuestionsMandatoryVesselInfo(
            ImportQuestionsStateModel(
              EntryDetails(EPU(230), EntryNumber("111111Z"), today),
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Hold),
                priorityGoods = Some(ImportPriorityGoods.HumanRemains),
                freightType = Some(ImportFreightType.Air)
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val dateTimeOfArrival = dateTime.plusDays(1).truncatedTo(ChronoUnit.MINUTES)

        val payload = Map(
          "vesselName"            -> "Foo Bar",
          "dateOfArrival.year"    -> f"${dateTimeOfArrival.get(ChronoField.YEAR)}",
          "dateOfArrival.month"   -> f"${dateTimeOfArrival.get(ChronoField.MONTH_OF_YEAR)}%02d",
          "dateOfArrival.day"     -> f"${dateTimeOfArrival.get(ChronoField.DAY_OF_MONTH)}%02d",
          "timeOfArrival.hour"    -> f"${dateTimeOfArrival.get(ChronoField.HOUR_OF_DAY)}%02d",
          "timeOfArrival.minutes" -> f"${dateTimeOfArrival.get(ChronoField.MINUTE_OF_HOUR)}%02d"
        )

        val result = await(request("/new/import/transport-information-required").post(payload))

        result.status shouldBe 200

        journey.getState shouldBe AnswerImportQuestionsContactInfo(
          ImportQuestionsStateModel(
            EntryDetails(EPU(230), EntryNumber("111111Z"), today),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Hold),
              priorityGoods = Some(ImportPriorityGoods.HumanRemains),
              freightType = Some(ImportFreightType.Air),
              vesselDetails = Some(
                VesselDetails(
                  vesselName = Some("Foo Bar"),
                  dateOfArrival = Some(dateTimeOfArrival.toLocalDate),
                  timeOfArrival = Some(dateTimeOfArrival.toLocalTime)
                )
              )
            )
          )
        )
      }

      "submit none vessel details and ask again for mandatory vessel details" in {
        val state = AnswerImportQuestionsMandatoryVesselInfo(
          ImportQuestionsStateModel(
            EntryDetails(EPU(230), EntryNumber("A11111Z"), today),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Hold),
              priorityGoods = Some(ImportPriorityGoods.HumanRemains),
              freightType = Some(ImportFreightType.Air)
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map[String, String]()
        val result = await(request("/new/import/transport-information-required").post(payload))

        result.status shouldBe 200
        journey.getState shouldBe state
      }
    }

    "GET /new/import/contact-information" should {
      "show the import contact information question page" in {
        val state = AnswerImportQuestionsContactInfo(
          ImportQuestionsStateModel(
            EntryDetails(EPU(235), EntryNumber("111111X"), today),
            ImportQuestions()
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/import/contact-information").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.contactInfo.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.contactInfo.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /new/import/contact-information" should {
      "go to upload file page when an email submitted" in {
        journey.setState(
          AnswerImportQuestionsContactInfo(
            ImportQuestionsStateModel(
              EntryDetails(EPU(235), EntryNumber("111111X"), today),
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route6),
                priorityGoods = Some(ImportPriorityGoods.HumanRemains),
                freightType = Some(ImportFreightType.Air)
              )
            )
          )
        )
        val callbackUrl =
          appConfig.baseInternalCallbackUrl + s"/internal/callback-from-upscan/new/journey/${SHA256
              .compute(journeyId.value)}"
        givenUpscanInitiateSucceeds(callbackUrl)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "contactEmail" -> "someone@email.com"
        )

        val result = await(request("/new/import/contact-information").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-file.first.title"))
        result.body should include(htmlEscapedMessage("view.upload-file.first.heading"))

        journey.getState shouldBe UploadFile(
          hostData = FileUploadHostData(
            EntryDetails(EPU(235), EntryNumber("111111X"), today),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route6),
              priorityGoods = Some(ImportPriorityGoods.HumanRemains),
              freightType = Some(ImportFreightType.Air),
              contactInfo = Some(ImportContactInfo(contactEmail = "someone@email.com"))
            )
          ),
          reference = "11370e18-6e24-453e-b45a-76d3e32ea33d",
          uploadRequest = UploadRequest(
            href = "https://bucketName.s3.eu-west-2.amazonaws.com",
            fields = Map(
              "Content-Type"            -> "application/xml",
              "acl"                     -> "private",
              "key"                     -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
              "policy"                  -> "xxxxxxxx==",
              "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
              "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
              "x-amz-date"              -> "yyyyMMddThhmmssZ",
              "x-amz-meta-callback-url" -> callbackUrl,
              "x-amz-signature"         -> "xxxx",
              "success_action_redirect" -> "https://myservice.com/nextPage",
              "error_action_redirect"   -> "https://myservice.com/errorPage"
            )
          ),
          fileUploads = FileUploads(files =
            Seq(FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"))
          )
        )
      }
    }

    "GET /new/import/check-your-answers" should {
      "show the import questions summary page" in {

        val state = ImportQuestionsSummary(
          ImportQuestionsStateModel(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival))
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/import/check-your-answers").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.summary.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.summary.heading"))
        result.body should include(routes.CreateCaseJourneyController.showFileUpload.url)
        journey.getState shouldBe state
      }
    }

    "POST /new/create-case" should {
      "create case and show the confirmation page for an export" in {
        journey.setState(
          ExportQuestionsSummary(
            ExportQuestionsStateModel(
              TestData.exportEntryDetails,
              TestData.fullExportQuestions(dateTimeOfArrival),
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
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "GB123456789012345"))
        givenCreateCaseApiRequestSucceeds()

        val result = await(request("/new/create-case").post(""))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.create-case-confirmation.title"))
        result.body should include(htmlEscapedMessage("view.create-case-confirmation.heading"))
        verifyCreateCaseRequestHappened(1)
        journey.getState shouldBe CreateCaseConfirmation(
          TestData.exportEntryDetails,
          TestData.fullExportQuestions(dateTimeOfArrival),
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
          TraderServicesResult(
            "A1234567890",
            generatedAt,
            List(
              FileTransferResult(
                upscanReference = "foo1",
                success = true,
                httpStatus = 201,
                transferredAt = LocalDateTime.parse("2021-04-18T12:07:36")
              )
            )
          ),
          CaseSLA(Some(generatedAt.plusHours(2)))
        )
      }

      "create case and show the confirmation page for an import" in {
        journey.setState(
          ImportQuestionsSummary(
            ImportQuestionsStateModel(
              TestData.importEntryDetails,
              TestData.fullImportQuestions(dateTimeOfArrival),
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
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "GB123456789012345"))
        givenCreateCaseApiRequestSucceeds()

        val result = await(request("/new/create-case").post(""))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.create-case-confirmation.title"))
        result.body should include(htmlEscapedMessage("view.create-case-confirmation.heading"))
        verifyCreateCaseRequestHappened(1)
        journey.getState shouldBe CreateCaseConfirmation(
          TestData.importEntryDetails,
          TestData.fullImportQuestions(dateTimeOfArrival),
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
          TraderServicesResult(
            "A1234567890",
            generatedAt,
            List(
              FileTransferResult(
                upscanReference = "foo1",
                success = true,
                httpStatus = 201,
                transferredAt = LocalDateTime.parse("2021-04-18T12:07:36")
              )
            )
          ),
          CaseSLA(Some(generatedAt.plusHours(2)))
        )
      }

      "show missing information page if questionnaire answers are not complete for export" in {

        val model = ExportQuestionsStateModel(
          TestData.exportEntryDetails,
          TestData
            .fullExportQuestions(dateTimeOfArrival)
            .copy(freightType = None),
          Some(FileUploads(files = Seq(TestData.acceptedFileUpload)))
        )
        journey.setState(ExportQuestionsSummary(model))
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "GB123456789012345"))
        givenCreateCaseApiRequestSucceeds()

        val result = await(request("/new/create-case").post(""))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.missing-information.title"))
        result.body should include(htmlEscapedMessage("view.missing-information.heading"))
        verifyCreateCaseRequestHappened(0)
        journey.getState shouldBe ExportQuestionsMissingInformationError(model)
      }

      "show missing information page if questionnaire answers are not complete for import" in {

        val model = ImportQuestionsStateModel(
          TestData.importEntryDetails,
          TestData
            .fullImportQuestions(dateTimeOfArrival)
            .copy(freightType = None),
          Some(FileUploads(files = Seq(TestData.acceptedFileUpload)))
        )
        journey.setState(ImportQuestionsSummary(model))
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "GB123456789012345"))
        givenCreateCaseApiRequestSucceeds()

        val result = await(request("/new/create-case").post(""))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.missing-information.title"))
        result.body should include(htmlEscapedMessage("view.missing-information.heading"))
        verifyCreateCaseRequestHappened(0)
        journey.getState shouldBe ImportQuestionsMissingInformationError(model)
      }

      "show case already exists page for an export" in {

        val model = ExportQuestionsStateModel(
          TestData.exportEntryDetails,
          TestData.fullExportQuestions(dateTimeOfArrival),
          Some(FileUploads(files = Seq(TestData.acceptedFileUpload)))
        )
        journey.setState(ExportQuestionsSummary(model))
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "GB123456789012345"))
        givenCreateCaseApiRequestReturnsDuplicateCaseError()

        val result = await(request("/new/create-case").post(""))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.case-already-exists.title"))
        result.body should include(htmlEscapedMessage("view.case-already-exists.heading"))
        result.body should include(htmlEscapedMessage("common.feedback.title"))
        result.body should include(htmlEscapedMessage("common.feedback.p1"))
        result.body should include(htmlEscapedMessage("common.feedback.link"))
        result.body should include(htmlEscapedMessage("common.feedback.p2"))
        verifyCreateCaseRequestHappened(1)
        journey.getState shouldBe CaseAlreadyExists("dummy-case-reference-number")
      }

      "show case already exists page for an import" in {

        val model = ImportQuestionsStateModel(
          TestData.importEntryDetails,
          TestData.fullImportQuestions(dateTimeOfArrival),
          Some(FileUploads(files = Seq(TestData.acceptedFileUpload)))
        )
        journey.setState(ImportQuestionsSummary(model))
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "GB123456789012345"))
        givenCreateCaseApiRequestReturnsDuplicateCaseError()

        val result = await(request("/new/create-case").post(""))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.case-already-exists.title"))
        result.body should include(htmlEscapedMessage("view.case-already-exists.heading"))
        result.body should include(htmlEscapedMessage("common.feedback.title"))
        result.body should include(htmlEscapedMessage("common.feedback.p1"))
        result.body should include(htmlEscapedMessage("common.feedback.link"))
        result.body should include(htmlEscapedMessage("common.feedback.p2"))
        verifyCreateCaseRequestHappened(1)
        journey.getState shouldBe CaseAlreadyExists("dummy-case-reference-number")
      }
    }

    "GET /new/confirmation" should {
      "show the confirmation page if in CreateCaseConfirmation state" in {

        val state = CreateCaseConfirmation(
          TestData.exportEntryDetails,
          TestData.fullExportQuestions(dateTimeOfArrival),
          Seq(
            UploadedFile(
              "foo-123",
              "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
              ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              "test.pdf",
              "application/pdf",
              Some(4567890)
            )
          ),
          TraderServicesResult("TBC", generatedAt),
          CaseSLA(Some(generatedAt.plusHours(2)))
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/confirmation").get)

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.create-case-confirmation.title"))
        result.body should include(htmlEscapedMessage("view.create-case-confirmation.heading"))
        result.body should include(
          s"${htmlEscapedMessage("view.create-case-confirmation.date")} ${generatedAt.ddMMYYYYAtTimeFormat}"
        )

        journey.getState shouldBe state
      }

      "goto CaseAlreadySubmitted if in CreateCaseConfirmation state" in {

        val state = CreateCaseConfirmation(
          TestData.exportEntryDetails,
          TestData.fullExportQuestions(dateTimeOfArrival),
          Seq(
            UploadedFile(
              "foo-123",
              "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
              ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              "test.pdf",
              "application/pdf",
              Some(4567890)
            )
          ),
          TraderServicesResult("TBC", generatedAt),
          CaseSLA(Some(generatedAt.plusHours(2)))
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result1 = await(request("/new/export/check-your-answers").get)

        result1.status shouldBe 200
        result1.body should include(htmlEscapedPageTitle("view.case-already-submitted.title"))
        result1.body should include(htmlEscapedMessage("view.case-already-submitted.heading"))

        val result2 = await(request("/new/import/check-your-answers").get())

        result2.status shouldBe 200
        result2.body should include(htmlEscapedPageTitle("view.case-already-submitted.title"))
        result2.body should include(htmlEscapedMessage("view.case-already-submitted.heading"))

        journey.getState shouldBe CaseAlreadySubmitted
      }
    }

    "GET /new/confirmation/receipt" should {
      "download the confirmation receipt" in {

        val state = CreateCaseConfirmation(
          TestData.exportEntryDetails,
          TestData.fullExportQuestions(dateTimeOfArrival),
          Seq(
            UploadedFile(
              "foo-123",
              "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
              ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              "test.pdf",
              "application/pdf",
              Some(4567890)
            )
          ),
          TraderServicesResult("ABC01234567890", generatedAt),
          CaseSLA(Some(generatedAt.plusHours(2)))
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        WireMock.stubFor(
          WireMock
            .get(WireMock.urlEqualTo("/send-documents-for-customs-check/assets/stylesheets/download-receipt.css"))
            .willReturn(WireMock.aResponse.withBody(""))
        )

        val result = await(request("/new/confirmation/receipt").get)

        result.status shouldBe 200
        result.header("Content-Disposition") shouldBe Some(
          """attachment; filename="Document_receipt_Z00000Z.html""""
        )

        result.body should include(htmlEscapedMessage("view.create-case-confirmation.heading"))
        result.body should include(
          s"${htmlEscapedMessage("receipt.documentsReceivedOn", generatedAt.ddMMYYYYAtTimeFormat)}"
        )

        journey.getState shouldBe state
      }
    }

    "GET /new/upload-files" should {
      "show the upload multiple files page for an importer" in {

        val state = UploadMultipleFiles(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          fileUploads = FileUploads()
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/upload-files").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-multiple-files.title"))
        result.body should include(htmlEscapedMessage("view.upload-multiple-files.heading"))
        journey.getState shouldBe state
      }

      "show the upload multiple files page for an exporter" in {

        val state = UploadMultipleFiles(
          FileUploadHostData(TestData.exportEntryDetails, TestData.fullExportQuestions(dateTimeOfArrival)),
          fileUploads = FileUploads()
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/upload-files").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-multiple-files.title"))
        result.body should include(htmlEscapedMessage("view.upload-multiple-files.heading"))
        journey.getState shouldBe state
      }

      "retreat from summary to the upload multiple files page for an importer" in {

        val state = ImportQuestionsSummary(
          ImportQuestionsStateModel(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival))
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/upload-files").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-multiple-files.title"))
        result.body should include(htmlEscapedMessage("view.upload-multiple-files.heading"))
        journey.getState shouldBe UploadMultipleFiles(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          fileUploads = FileUploads()
        )
      }

      "retreat from summary to the upload multiple files page for an exporter" in {

        val state = ExportQuestionsSummary(
          ExportQuestionsStateModel(TestData.exportEntryDetails, TestData.fullExportQuestions(dateTimeOfArrival))
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/upload-files").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-multiple-files.title"))
        result.body should include(htmlEscapedMessage("view.upload-multiple-files.heading"))
        journey.getState shouldBe UploadMultipleFiles(
          FileUploadHostData(TestData.exportEntryDetails, TestData.fullExportQuestions(dateTimeOfArrival)),
          fileUploads = FileUploads()
        )
      }
    }

    "POST /new/upload-files/initialise/:uploadId" should {
      "initialise first file upload" in {

        val state = UploadMultipleFiles(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          fileUploads = FileUploads()
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val callbackUrl =
          appConfig.baseInternalCallbackUrl + s"/internal/callback-from-upscan/new/journey/${SHA256
              .compute(journeyId.value)}"
        givenUpscanInitiateSucceeds(callbackUrl)

        val result = await(request("/new/upload-files/initialise/001").post(""))

        result.status shouldBe 200
        val json = result.body[JsValue]
        (json \ "upscanReference").as[String] shouldBe "11370e18-6e24-453e-b45a-76d3e32ea33d"
        (json \ "uploadId").as[String] shouldBe "001"
        (json \ "uploadRequest").as[JsObject] shouldBe Json.obj(
          "href" -> "https://bucketName.s3.eu-west-2.amazonaws.com",
          "fields" -> Json.obj(
            "Content-Type"            -> "application/xml",
            "acl"                     -> "private",
            "key"                     -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
            "policy"                  -> "xxxxxxxx==",
            "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
            "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
            "x-amz-date"              -> "yyyyMMddThhmmssZ",
            "x-amz-meta-callback-url" -> callbackUrl,
            "x-amz-signature"         -> "xxxx",
            "success_action_redirect" -> "https://myservice.com/nextPage",
            "error_action_redirect"   -> "https://myservice.com/errorPage"
          )
        )

        journey.getState shouldBe
          UploadMultipleFiles(
            FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
            fileUploads = FileUploads(files =
              Seq(
                FileUpload.Initiated(
                  Nonce.Any,
                  Timestamp.Any,
                  "11370e18-6e24-453e-b45a-76d3e32ea33d",
                  uploadId = Some("001"),
                  uploadRequest = Some(
                    UploadRequest(
                      href = "https://bucketName.s3.eu-west-2.amazonaws.com",
                      fields = Map(
                        "Content-Type"            -> "application/xml",
                        "acl"                     -> "private",
                        "key"                     -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
                        "policy"                  -> "xxxxxxxx==",
                        "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
                        "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
                        "x-amz-date"              -> "yyyyMMddThhmmssZ",
                        "x-amz-meta-callback-url" -> callbackUrl,
                        "x-amz-signature"         -> "xxxx",
                        "success_action_redirect" -> "https://myservice.com/nextPage",
                        "error_action_redirect"   -> "https://myservice.com/errorPage"
                      )
                    )
                  )
                )
              )
            )
          )
      }

      "initialise next file upload" in {

        val state = UploadMultipleFiles(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          fileUploads = FileUploads(
            Seq(FileUpload.Posted(Nonce.Any, Timestamp.Any, "23370e18-6e24-453e-b45a-76d3e32ea389"))
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val callbackUrl =
          appConfig.baseInternalCallbackUrl + s"/internal/callback-from-upscan/new/journey/${SHA256
              .compute(journeyId.value)}"
        givenUpscanInitiateSucceeds(callbackUrl)

        val result = await(request("/new/upload-files/initialise/002").post(""))

        result.status shouldBe 200
        val json = result.body[JsValue]
        (json \ "upscanReference").as[String] shouldBe "11370e18-6e24-453e-b45a-76d3e32ea33d"
        (json \ "uploadId").as[String] shouldBe "002"
        (json \ "uploadRequest").as[JsObject] shouldBe Json.obj(
          "href" -> "https://bucketName.s3.eu-west-2.amazonaws.com",
          "fields" -> Json.obj(
            "Content-Type"            -> "application/xml",
            "acl"                     -> "private",
            "key"                     -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
            "policy"                  -> "xxxxxxxx==",
            "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
            "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
            "x-amz-date"              -> "yyyyMMddThhmmssZ",
            "x-amz-meta-callback-url" -> callbackUrl,
            "x-amz-signature"         -> "xxxx",
            "success_action_redirect" -> "https://myservice.com/nextPage",
            "error_action_redirect"   -> "https://myservice.com/errorPage"
          )
        )

        journey.getState shouldBe
          UploadMultipleFiles(
            FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
            fileUploads = FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "23370e18-6e24-453e-b45a-76d3e32ea389"),
                FileUpload.Initiated(
                  Nonce.Any,
                  Timestamp.Any,
                  "11370e18-6e24-453e-b45a-76d3e32ea33d",
                  uploadId = Some("002"),
                  uploadRequest = Some(
                    UploadRequest(
                      href = "https://bucketName.s3.eu-west-2.amazonaws.com",
                      fields = Map(
                        "Content-Type"            -> "application/xml",
                        "acl"                     -> "private",
                        "key"                     -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
                        "policy"                  -> "xxxxxxxx==",
                        "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
                        "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
                        "x-amz-date"              -> "yyyyMMddThhmmssZ",
                        "x-amz-meta-callback-url" -> callbackUrl,
                        "x-amz-signature"         -> "xxxx",
                        "success_action_redirect" -> "https://myservice.com/nextPage",
                        "error_action_redirect"   -> "https://myservice.com/errorPage"
                      )
                    )
                  )
                )
              )
            )
          )
      }
    }

    "GET /new/file-upload" should {
      "show the upload first document page for the importer" in {

        val state = ImportQuestionsSummary(
          ImportQuestionsStateModel(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival))
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val callbackUrl =
          appConfig.baseInternalCallbackUrl + s"/internal/callback-from-upscan/new/journey/${SHA256
              .compute(journeyId.value)}"
        givenUpscanInitiateSucceeds(callbackUrl)

        val result = await(request("/new/file-upload").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-file.first.title"))
        result.body should include(htmlEscapedMessage("view.upload-file.first.heading"))
        journey.getState shouldBe UploadFile(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          reference = "11370e18-6e24-453e-b45a-76d3e32ea33d",
          uploadRequest = UploadRequest(
            href = "https://bucketName.s3.eu-west-2.amazonaws.com",
            fields = Map(
              "Content-Type"            -> "application/xml",
              "acl"                     -> "private",
              "key"                     -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
              "policy"                  -> "xxxxxxxx==",
              "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
              "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
              "x-amz-date"              -> "yyyyMMddThhmmssZ",
              "x-amz-meta-callback-url" -> callbackUrl,
              "x-amz-signature"         -> "xxxx",
              "success_action_redirect" -> "https://myservice.com/nextPage",
              "error_action_redirect"   -> "https://myservice.com/errorPage"
            )
          ),
          fileUploads = FileUploads(files =
            Seq(FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"))
          )
        )
      }

      "show the upload first document page for the exporter" in {

        val state = ExportQuestionsSummary(
          ExportQuestionsStateModel(TestData.exportEntryDetails, TestData.fullExportQuestions(dateTimeOfArrival))
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val callbackUrl =
          appConfig.baseInternalCallbackUrl + s"/internal/callback-from-upscan/new/journey/${SHA256
              .compute(journeyId.value)}"
        givenUpscanInitiateSucceeds(callbackUrl)

        val result = await(request("/new/file-upload").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-file.first.title"))
        result.body should include(htmlEscapedMessage("view.upload-file.first.heading"))
        journey.getState shouldBe UploadFile(
          FileUploadHostData(TestData.exportEntryDetails, TestData.fullExportQuestions(dateTimeOfArrival)),
          reference = "11370e18-6e24-453e-b45a-76d3e32ea33d",
          uploadRequest = UploadRequest(
            href = "https://bucketName.s3.eu-west-2.amazonaws.com",
            fields = Map(
              "Content-Type"            -> "application/xml",
              "acl"                     -> "private",
              "key"                     -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
              "policy"                  -> "xxxxxxxx==",
              "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
              "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
              "x-amz-date"              -> "yyyyMMddThhmmssZ",
              "x-amz-meta-callback-url" -> callbackUrl,
              "x-amz-signature"         -> "xxxx",
              "success_action_redirect" -> "https://myservice.com/nextPage",
              "error_action_redirect"   -> "https://myservice.com/errorPage"
            )
          ),
          fileUploads = FileUploads(files =
            Seq(FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"))
          )
        )
      }
    }

    "GET /new/file-verification" should {
      "display waiting for file verification page" in {
        journey.setState(
          UploadFile(
            FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
            "2b72fe99-8adf-4edb-865e-622ae710f77c",
            UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            FileUploads(files =
              Seq(
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/file-verification").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-file.waiting"))
        result.body should include(htmlEscapedMessage("view.upload-file.waiting"))

        journey.getState shouldBe WaitingForFileVerification(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          "2b72fe99-8adf-4edb-865e-622ae710f77c",
          UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
          FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c"),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
            )
          )
        )
      }
    }

    "GET /new/journey/:journeyId/file-rejected" should {
      "set current file upload status as rejected and return 204 NoContent" in {
        journey.setState(
          UploadFile(
            FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
            "11370e18-6e24-453e-b45a-76d3e32ea33d",
            UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            FileUploads(files =
              Seq(
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result1 =
          await(
            requestWithoutJourneyId(
              s"/new/journey/${SHA256.compute(journeyId.value)}/file-rejected?key=11370e18-6e24-453e-b45a-76d3e32ea33d&errorCode=ABC123&errorMessage=ABC+123"
            ).get()
          )

        result1.status shouldBe 204
        result1.body.isEmpty shouldBe true
        journey.getState shouldBe UploadFile(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          "11370e18-6e24-453e-b45a-76d3e32ea33d",
          UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
          FileUploads(files =
            Seq(
              FileUpload.Rejected(
                Nonce.Any,
                Timestamp.Any,
                "11370e18-6e24-453e-b45a-76d3e32ea33d",
                S3UploadError(
                  key = "11370e18-6e24-453e-b45a-76d3e32ea33d",
                  errorCode = "ABC123",
                  errorMessage = "ABC 123"
                )
              ),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
            )
          ),
          Some(
            FileTransmissionFailed(
              S3UploadError("11370e18-6e24-453e-b45a-76d3e32ea33d", "ABC123", "ABC 123", None, None)
            )
          )
        )
      }
    }

    "GET /new/journey/:journeyId/file-verification" should {
      "set current file upload status as posted and return 204 NoContent" in {
        journey.setState(
          UploadFile(
            FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
            "11370e18-6e24-453e-b45a-76d3e32ea33d",
            UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            FileUploads(files =
              Seq(
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result1 =
          await(requestWithoutJourneyId(s"/new/journey/${SHA256.compute(journeyId.value)}/file-verification").get())

        result1.status shouldBe 202
        result1.body.isEmpty shouldBe true
        journey.getState shouldBe WaitingForFileVerification(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          "11370e18-6e24-453e-b45a-76d3e32ea33d",
          UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
          FileUpload.Posted(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
          FileUploads(files =
            Seq(
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
            )
          )
        )
      }
    }

    "GET /new/file-verification/:reference/status" should {
      "return file verification status" in {

        val state = FileUploaded(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(
                Nonce.Any,
                Timestamp.Any,
                "11370e18-6e24-453e-b45a-76d3e32ea33d",
                uploadRequest =
                  Some(UploadRequest(href = "https://s3.amazonaws.com/bucket/123abc", fields = Map("foo1" -> "bar1")))
              ),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c"),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "f029444f-415c-4dec-9cf2-36774ec63ab8",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              ),
              FileUpload.Failed(
                Nonce.Any,
                Timestamp.Any,
                "4b1e15a4-4152-4328-9448-4924d9aee6e2",
                UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
              ),
              FileUpload.Rejected(
                Nonce.Any,
                Timestamp.Any,
                "4b1e15a4-4152-4328-9448-4924d9aee6e3",
                details = S3UploadError("key", "errorCode", "Invalid file type.")
              ),
              FileUpload.Duplicate(
                Nonce.Any,
                Timestamp.Any,
                "4b1e15a4-4152-4328-9448-4924d9aee6e4",
                checksum = "0" * 64,
                existingFileName = "test.pdf",
                duplicateFileName = "test1.png"
              )
            )
          ),
          acknowledged = false
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result1 =
          await(
            request("/new/file-verification/11370e18-6e24-453e-b45a-76d3e32ea33d/status")
              .get()
          )
        result1.status shouldBe 200
        result1.body shouldBe """{"reference":"11370e18-6e24-453e-b45a-76d3e32ea33d","fileStatus":"NOT_UPLOADED","uploadRequest":{"href":"https://s3.amazonaws.com/bucket/123abc","fields":{"foo1":"bar1"}}}"""
        journey.getState shouldBe state

        val result2 =
          await(request("/new/file-verification/2b72fe99-8adf-4edb-865e-622ae710f77c/status").get())
        result2.status shouldBe 200
        result2.body shouldBe """{"reference":"2b72fe99-8adf-4edb-865e-622ae710f77c","fileStatus":"WAITING"}"""
        journey.getState shouldBe state

        val result3 =
          await(request("/new/file-verification/f029444f-415c-4dec-9cf2-36774ec63ab8/status").get())
        result3.status shouldBe 200
        result3.body shouldBe """{"reference":"f029444f-415c-4dec-9cf2-36774ec63ab8","fileStatus":"ACCEPTED","fileMimeType":"application/pdf","fileName":"test.pdf","fileSize":4567890,"previewUrl":"/send-documents-for-customs-check/new/file-uploaded/f029444f-415c-4dec-9cf2-36774ec63ab8/test.pdf"}"""
        journey.getState shouldBe state

        val result4 =
          await(request("/new/file-verification/4b1e15a4-4152-4328-9448-4924d9aee6e2/status").get())
        result4.status shouldBe 200
        result4.body shouldBe """{"reference":"4b1e15a4-4152-4328-9448-4924d9aee6e2","fileStatus":"FAILED","errorMessage":"The selected file contains a virus - upload a different one"}"""
        journey.getState shouldBe state

        val result5 =
          await(request("/new/file-verification/f0e317f5-d394-42cc-93f8-e89f4fc0114c/status").get())
        result5.status shouldBe 404
        journey.getState shouldBe state

        val result6 =
          await(request("/new/file-verification/4b1e15a4-4152-4328-9448-4924d9aee6e3/status").get())
        result6.status shouldBe 200
        result6.body shouldBe """{"reference":"4b1e15a4-4152-4328-9448-4924d9aee6e3","fileStatus":"REJECTED","errorMessage":"The selected file could not be uploaded"}"""
        journey.getState shouldBe state

        val result7 =
          await(request("/new/file-verification/4b1e15a4-4152-4328-9448-4924d9aee6e4/status").get())
        result7.status shouldBe 200
        result7.body shouldBe """{"reference":"4b1e15a4-4152-4328-9448-4924d9aee6e4","fileStatus":"DUPLICATE","errorMessage":"The selected file has already been uploaded"}"""
        journey.getState shouldBe state
      }
    }

    "GET /new/file-uploaded" should {
      "show uploaded singular file view" in {
        val state = FileUploaded(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          fileUploads = FileUploads(files = Seq(TestData.acceptedFileUpload)),
          acknowledged = true
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/file-uploaded").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.file-uploaded.singular.title", "1"))
        result.body should include(htmlEscapedMessage("view.file-uploaded.singular.heading", "1"))
        journey.getState shouldBe state
      }

      "show uploaded plural file view" in {
        val state = FileUploaded(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          fileUploads = FileUploads(files =
            Seq(
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "11370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test2.pdf",
                "application/pdf",
                Some(5234567)
              ),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "22370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test1.png",
                "image/png",
                Some(4567890)
              )
            )
          ),
          acknowledged = true
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/file-uploaded").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.file-uploaded.plural.title", "2"))
        result.body should include(htmlEscapedMessage("view.file-uploaded.plural.heading", "2"))
        journey.getState shouldBe state
      }

      "show file upload summary view" in {
        val state = FileUploaded(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          fileUploads = FileUploads(files = for (_ <- 1 to 10) yield TestData.acceptedFileUpload),
          acknowledged = true
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/file-uploaded").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.file-uploaded.plural.title", "10"))
        result.body should include(htmlEscapedMessage("view.file-uploaded.plural.heading", "10"))
        journey.getState shouldBe state
      }
    }

    "POST /new/file-uploaded" should {

      val FILES_LIMIT = 10

      "show upload a file view for export when yes and number of files below the limit" in {

        val fileUploads = FileUploads(files = for (_ <- 1 until FILES_LIMIT) yield TestData.acceptedFileUpload)
        val state = FileUploaded(
          FileUploadHostData(TestData.exportEntryDetails, TestData.fullExportQuestions(dateTimeOfArrival)),
          fileUploads
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val callbackUrl =
          appConfig.baseInternalCallbackUrl + s"/internal/callback-from-upscan/new/journey/${SHA256
              .compute(journeyId.value)}"
        givenUpscanInitiateSucceeds(callbackUrl)

        val result = await(
          request("/new/file-uploaded")
            .post(Map("uploadAnotherFile" -> "yes"))
        )

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-file.next.title"))
        result.body should include(htmlEscapedMessage("view.upload-file.next.heading"))
        journey.getState shouldBe UploadFile(
          FileUploadHostData(TestData.exportEntryDetails, TestData.fullExportQuestions(dateTimeOfArrival)),
          reference = "11370e18-6e24-453e-b45a-76d3e32ea33d",
          uploadRequest = UploadRequest(
            href = "https://bucketName.s3.eu-west-2.amazonaws.com",
            fields = Map(
              "Content-Type"            -> "application/xml",
              "acl"                     -> "private",
              "key"                     -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
              "policy"                  -> "xxxxxxxx==",
              "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
              "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
              "x-amz-date"              -> "yyyyMMddThhmmssZ",
              "x-amz-meta-callback-url" -> callbackUrl,
              "x-amz-signature"         -> "xxxx",
              "success_action_redirect" -> "https://myservice.com/nextPage",
              "error_action_redirect"   -> "https://myservice.com/errorPage"
            )
          ),
          fileUploads = FileUploads(files =
            fileUploads.files ++
              Seq(FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"))
          )
        )
      }

      "show upload a file view for import when yes and number of files below the limit" in {

        val fileUploads = FileUploads(files = for (_ <- 1 until FILES_LIMIT) yield TestData.acceptedFileUpload)
        val state = FileUploaded(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          fileUploads
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val callbackUrl =
          appConfig.baseInternalCallbackUrl + s"/internal/callback-from-upscan/new/journey/${SHA256
              .compute(journeyId.value)}"
        givenUpscanInitiateSucceeds(callbackUrl)

        val result = await(
          request("/new/file-uploaded")
            .post(Map("uploadAnotherFile" -> "yes"))
        )

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-file.next.title"))
        result.body should include(htmlEscapedMessage("view.upload-file.next.heading"))
        journey.getState shouldBe UploadFile(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          reference = "11370e18-6e24-453e-b45a-76d3e32ea33d",
          uploadRequest = UploadRequest(
            href = "https://bucketName.s3.eu-west-2.amazonaws.com",
            fields = Map(
              "Content-Type"            -> "application/xml",
              "acl"                     -> "private",
              "key"                     -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
              "policy"                  -> "xxxxxxxx==",
              "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
              "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
              "x-amz-date"              -> "yyyyMMddThhmmssZ",
              "x-amz-meta-callback-url" -> callbackUrl,
              "x-amz-signature"         -> "xxxx",
              "success_action_redirect" -> "https://myservice.com/nextPage",
              "error_action_redirect"   -> "https://myservice.com/errorPage"
            )
          ),
          fileUploads = FileUploads(files =
            fileUploads.files ++
              Seq(FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"))
          )
        )
      }

      "show check-your-anwers page for export when yes and files number limit has been reached" in {

        val fileUploads = FileUploads(files = for (_ <- 1 to FILES_LIMIT) yield TestData.acceptedFileUpload)
        val state = FileUploaded(
          FileUploadHostData(TestData.exportEntryDetails, TestData.fullExportQuestions(dateTimeOfArrival)),
          fileUploads
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(
          request("/new/file-uploaded")
            .post(Map("uploadAnotherFile" -> "yes"))
        )

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.summary.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.summary.heading"))
        result.body should include(routes.CreateCaseJourneyController.showFileUpload.url)
        journey.getState shouldBe ExportQuestionsSummary(
          ExportQuestionsStateModel(
            TestData.exportEntryDetails,
            TestData.fullExportQuestions(dateTimeOfArrival),
            Some(fileUploads)
          )
        )
      }

      "show check-your-anwers page for import when yes and files number limit has been reached" in {

        val fileUploads = FileUploads(files = for (_ <- 1 to FILES_LIMIT) yield TestData.acceptedFileUpload)
        val state = FileUploaded(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          fileUploads
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(
          request("/new/file-uploaded")
            .post(Map("uploadAnotherFile" -> "yes"))
        )

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.summary.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.summary.heading"))
        result.body should include(routes.CreateCaseJourneyController.showFileUpload.url)
        journey.getState shouldBe ImportQuestionsSummary(
          ImportQuestionsStateModel(
            TestData.importEntryDetails,
            TestData.fullImportQuestions(dateTimeOfArrival),
            Some(fileUploads)
          )
        )
      }

      "show check-your-anwers page for export when no and files number below the limit" in {

        val fileUploads = FileUploads(files = for (_ <- 1 until FILES_LIMIT) yield TestData.acceptedFileUpload)
        val state = FileUploaded(
          FileUploadHostData(TestData.exportEntryDetails, TestData.fullExportQuestions(dateTimeOfArrival)),
          fileUploads
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(
          request("/new/file-uploaded")
            .post(Map("uploadAnotherFile" -> "no"))
        )

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.summary.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.summary.heading"))
        result.body should include(routes.CreateCaseJourneyController.showFileUpload.url)
        journey.getState shouldBe ExportQuestionsSummary(
          ExportQuestionsStateModel(
            TestData.exportEntryDetails,
            TestData.fullExportQuestions(dateTimeOfArrival),
            Some(fileUploads)
          )
        )
      }

      "show check-your-anwers page for import when no and files number below the limit" in {

        val fileUploads = FileUploads(files = for (_ <- 1 until FILES_LIMIT) yield TestData.acceptedFileUpload)
        val state = FileUploaded(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          fileUploads
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(
          request("/new/file-uploaded")
            .post(Map("uploadAnotherFile" -> "no"))
        )

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.summary.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.summary.heading"))
        result.body should include(routes.CreateCaseJourneyController.showFileUpload.url)
        journey.getState shouldBe ImportQuestionsSummary(
          ImportQuestionsStateModel(
            TestData.importEntryDetails,
            TestData.fullImportQuestions(dateTimeOfArrival),
            Some(fileUploads)
          )
        )
      }

      "show check-your-anwers page for export when no and files number above the limit" in {

        val fileUploads = FileUploads(files = for (_ <- 1 to FILES_LIMIT) yield TestData.acceptedFileUpload)
        val state = FileUploaded(
          FileUploadHostData(TestData.exportEntryDetails, TestData.fullExportQuestions(dateTimeOfArrival)),
          fileUploads
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(
          request("/new/file-uploaded")
            .post(Map("uploadAnotherFile" -> "no"))
        )

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.summary.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.summary.heading"))
        result.body should include(routes.CreateCaseJourneyController.showFileUpload.url)
        journey.getState shouldBe ExportQuestionsSummary(
          ExportQuestionsStateModel(
            TestData.exportEntryDetails,
            TestData.fullExportQuestions(dateTimeOfArrival),
            Some(fileUploads)
          )
        )
      }

      "show check-your-anwers page for import when no and files number above the limit" in {

        val fileUploads = FileUploads(files = for (_ <- 1 to FILES_LIMIT) yield TestData.acceptedFileUpload)
        val state = FileUploaded(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          fileUploads
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(
          request("/new/file-uploaded")
            .post(Map("uploadAnotherFile" -> "no"))
        )

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.summary.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.summary.heading"))
        result.body should include(routes.CreateCaseJourneyController.showFileUpload.url)
        journey.getState shouldBe ImportQuestionsSummary(
          ImportQuestionsStateModel(
            TestData.importEntryDetails,
            TestData.fullImportQuestions(dateTimeOfArrival),
            Some(fileUploads)
          )
        )
      }
    }

    "GET /new/file-rejected" should {
      "show upload document again" in {
        journey.setState(
          UploadFile(
            FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
            "2b72fe99-8adf-4edb-865e-622ae710f77c",
            UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            FileUploads(files =
              Seq(
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(
          request(
            "/new/file-rejected?key=2b72fe99-8adf-4edb-865e-622ae710f77c&errorCode=EntityTooLarge&errorMessage=Entity+Too+Large"
          ).get()
        )

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-file.first.title"))
        result.body should include(htmlEscapedMessage("view.upload-file.first.heading"))
        journey.getState shouldBe UploadFile(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          "2b72fe99-8adf-4edb-865e-622ae710f77c",
          UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
            )
          ),
          Some(
            FileTransmissionFailed(
              S3UploadError("2b72fe99-8adf-4edb-865e-622ae710f77c", "EntityTooLarge", "Entity Too Large")
            )
          )
        )
      }
    }

    "POST /new/file-rejected" should {
      "mark file upload as rejected" in {
        journey.setState(
          UploadMultipleFiles(
            FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
            FileUploads(files =
              Seq(
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(
          request("/new/file-rejected").post(
            Json.obj(
              "key"          -> "2b72fe99-8adf-4edb-865e-622ae710f77c",
              "errorCode"    -> "EntityTooLarge",
              "errorMessage" -> "Entity Too Large"
            )
          )
        )

        result.status shouldBe 201

        journey.getState shouldBe UploadMultipleFiles(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Rejected(
                Nonce.Any,
                Timestamp.Any,
                "2b72fe99-8adf-4edb-865e-622ae710f77c",
                S3UploadError("2b72fe99-8adf-4edb-865e-622ae710f77c", "EntityTooLarge", "Entity Too Large")
              )
            )
          )
        )
      }
    }

    "GET /new/file-uploaded/:reference/remove" should {
      "remove file from upload list by reference" in {

        val state = FileUploaded(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          fileUploads = FileUploads(files =
            Seq(
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "11370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test2.pdf",
                "application/pdf",
                Some(5234567)
              ),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "22370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test1.png",
                "image/png",
                Some(4567890)
              )
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/file-uploaded/11370e18-6e24-453e-b45a-76d3e32ea33d/remove").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.file-uploaded.singular.title", "1"))
        result.body should include(htmlEscapedMessage("view.file-uploaded.singular.heading", "1"))
        journey.getState shouldBe FileUploaded(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          fileUploads = FileUploads(files =
            Seq(
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "22370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test1.png",
                "image/png",
                Some(4567890)
              )
            )
          )
        )
      }
    }

    "POST /new/file-uploaded/:reference/remove" should {
      "remove file from upload list by reference" in {

        val state = UploadMultipleFiles(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          fileUploads = FileUploads(files =
            Seq(
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "11370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test2.pdf",
                "application/pdf",
                Some(5234567)
              ),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "22370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test1.png",
                "image/png",
                Some(4567890)
              )
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/new/file-uploaded/11370e18-6e24-453e-b45a-76d3e32ea33d/remove").post(""))

        result.status shouldBe 204

        journey.getState shouldBe UploadMultipleFiles(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          fileUploads = FileUploads(files =
            Seq(
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "22370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test1.png",
                "image/png",
                Some(4567890)
              )
            )
          )
        )
      }
    }

    "GET /new/file-uploaded/:reference" should {
      "stream the uploaded file content back if exists" in {
        val bytes = Array.ofDim[Byte](1024 * 1024)
        Random.nextBytes(bytes)
        val upscanUrl = stubForFileDownload(200, bytes, "test.pdf")

        val state = FileUploaded(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c"),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "f029444f-415c-4dec-9cf2-36774ec63ab8",
                upscanUrl,
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              ),
              FileUpload.Failed(
                Nonce.Any,
                Timestamp.Any,
                "4b1e15a4-4152-4328-9448-4924d9aee6e2",
                UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
              )
            )
          ),
          acknowledged = false
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result =
          await(
            request("/new/file-uploaded/f029444f-415c-4dec-9cf2-36774ec63ab8/test.pdf")
              .get()
          )
        result.status shouldBe 200
        result.header("Content-Type") shouldBe Some("application/pdf")
        result.header("Content-Length") shouldBe Some(s"${bytes.length}")
        result.header("Content-Disposition") shouldBe Some("""inline; filename="test.pdf"; filename*=utf-8''test.pdf""")
        result.bodyAsBytes.toArray[Byte] shouldBe bytes
        journey.getState shouldBe state
      }

      "return error page if file does not exist" in {
        val upscanUrl = stubForFileDownloadFailure(404, "test.pdf")

        val state = FileUploaded(
          FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c"),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "f029444f-415c-4dec-9cf2-36774ec63ab8",
                upscanUrl,
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              ),
              FileUpload.Failed(
                Nonce.Any,
                Timestamp.Any,
                "4b1e15a4-4152-4328-9448-4924d9aee6e2",
                UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
              )
            )
          ),
          acknowledged = false
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result =
          await(
            request("/new/file-uploaded/f029444f-415c-4dec-9cf2-36774ec63ab8/test.pdf")
              .get()
          )
        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("global.error.500.title"))
        result.body should include(htmlEscapedMessage("global.error.500.heading"))
        journey.getState shouldBe state
      }
    }

    "GET /new/journey/:journeyId/file-posted" should {
      "set current file upload status as posted and return 201 Created" in {
        journey.setState(
          UploadMultipleFiles(
            FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
            FileUploads(files =
              Seq(
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result =
          await(
            requestWithoutJourneyId(
              s"/new/journey/${SHA256.compute(journeyId.value)}/file-posted?key=11370e18-6e24-453e-b45a-76d3e32ea33d&bucket=foo"
            ).get()
          )

        result.status shouldBe 201
        result.body.isEmpty shouldBe true
        result.headerValues(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN) shouldBe Seq("*")
        journey.getState should beState(
          UploadMultipleFiles(
            FileUploadHostData(TestData.importEntryDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
      }
    }

    "OPTIONS /new/journey/:journeyId/file-rejected" should {
      "return 201 with access control header" in {
        val result =
          await(
            request(s"/new/journey/${SHA256.compute(journeyId.value)}/file-rejected")
              .options()
          )
        result.status shouldBe 201
        result.body.isEmpty shouldBe true
        result.headerValues(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN) shouldBe Seq("*")
      }
    }

    "OPTIONS /new/journey/:journeyId/file-posted" should {
      "return 201 with access control header" in {
        val result =
          await(
            request(s"/new/journey/${SHA256.compute(journeyId.value)}/file-posted")
              .options()
          )
        result.status shouldBe 201
        result.body.isEmpty shouldBe true
        result.headerValues(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN) shouldBe Seq("*")
      }
    }

    "GET /foo" should {
      "return an error page not found" in {
        val state = journey.getState
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/foo").get())

        result.status shouldBe 404
        result.body should include("Page not found")
        journey.getState shouldBe state
      }
    }
  }
}

trait CreateCaseJourneyISpecSetup extends ServerISpec with StateMatchers {

  val authToken: String = UUID.randomUUID.toString

  implicit val journeyId: JourneyId = JourneyId()

  implicit val hc: HeaderCarrier =
    HeaderCarrier(authorization = Some(Authorization(authToken)), sessionId = Some(SessionId(journeyId.value)))
      .withExtraHeaders((journey.journeyKey, journeyId.value))

  import play.api.i18n._
  implicit val messages: Messages = MessagesImpl(Lang("en"), app.injector.instanceOf[MessagesApi])

  val dateTime: LocalDateTime = LocalDateTime.now()
  val dateTimeOfArrival: LocalDateTime = dateTime.plusDays(1).truncatedTo(ChronoUnit.MINUTES)

  val today: LocalDate = LocalDate.now
  val (y, m, d) = (today.getYear, today.getMonthValue, today.getDayOfMonth)

  lazy val controller: CreateCaseJourneyController = app.injector.instanceOf[CreateCaseJourneyController]

  lazy val journey: TestJourneyService with CreateCaseJourneyService with EncryptedSessionCache[State, HeaderCarrier] = new support.TestJourneyService with CreateCaseJourneyService
  with EncryptedSessionCache[State, HeaderCarrier] {

    override lazy val actorSystem: ActorSystem = app.injector.instanceOf[ActorSystem]
    override lazy val cacheRepository: CacheRepository = app.injector.instanceOf[CacheRepository]
    lazy val keyProvider: KeyProvider = KeyProvider(app.injector.instanceOf[Config])

    override lazy val keyProviderFromContext: HeaderCarrier => KeyProvider =
      _ => KeyProvider(keyProvider, None)

    override def getJourneyId(hc: HeaderCarrier): Option[String] = hc.sessionId.map(_.value).map(SHA256.compute)

    override val stateFormats: Format[State] = CreateCaseJourneyStateFormats.formats
    override val root: State = model.root
    override val default: State = root
  }

  final def fakeRequest(cookies: Cookie*)(implicit
    journeyId: JourneyId
  ): Request[AnyContent] =
    fakeRequest("GET", "/", cookies: _*)

  final def fakeRequest(method: String, path: String, cookies: Cookie*)(implicit
    journeyId: JourneyId
  ): Request[AnyContent] =
    FakeRequest(Call(method, path))
      .withCookies(cookies: _*)
      .withSession(journey.journeyKey -> journeyId.value)

  final def request(path: String, isInternalUrl: Boolean = false)(implicit
    journeyId: JourneyId
  ): StandaloneWSRequest = {
    val sessionCookie =
      sessionCookieBaker
        .encodeAsCookie(
          Session(Map(SessionKeys.sessionId -> journeyId.value, SessionKeys.authToken -> "Bearer XYZ"))
        )

    val baseUrlPath = if (isInternalUrl) baseInternalUrl else baseUrl

    wsClient
      .url(s"$baseUrlPath$path")
      .withHttpHeaders(HeaderNames.AUTHORIZATION -> "Bearer token", journey.journeyKey -> journeyId.value)
      .withCookies(
        Seq(
          DefaultWSCookie(
            sessionCookie.name,
            sessionCookieCrypto.crypto.encrypt(PlainText(sessionCookie.value)).value
          ),
          DefaultWSCookie(SessionKeys.sessionId, journeyId.value)
        ): _*
      )
  }

  final def requestWithCookies(path: String, cookies: (String, String)*)(implicit
    journeyId: JourneyId
  ): StandaloneWSRequest = {
    val sessionCookie =
      sessionCookieBaker
        .encodeAsCookie(Session(Map(SessionKeys.sessionId -> journeyId.value, SessionKeys.authToken -> "Bearer XYZ")))

    wsClient
      .url(s"$baseUrl$path")
      .withHttpHeaders(HeaderNames.AUTHORIZATION -> "Bearer token", journey.journeyKey -> journeyId.value)
      .withCookies(
        cookies.map(c => DefaultWSCookie(c._1, c._2)) :+ DefaultWSCookie(
          sessionCookie.name,
          sessionCookieCrypto.crypto.encrypt(PlainText(sessionCookie.value)).value
        ): _*
      )
  }

}
