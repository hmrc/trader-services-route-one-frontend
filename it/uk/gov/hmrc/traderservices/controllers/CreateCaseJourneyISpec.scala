package uk.gov.hmrc.traderservices.controllers

import java.time.LocalDate
import java.util.UUID

import play.api.Application
import play.api.libs.json.Format
import play.api.libs.ws.WSClient
import play.api.mvc.{Cookies, Session, SessionCookieBaker}
import uk.gov.hmrc.cache.repository.CacheMongoRepository
import uk.gov.hmrc.crypto.{ApplicationCrypto, PlainText}
import uk.gov.hmrc.play.bootstrap.frontend.filters.crypto.SessionCookieCrypto
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyStateFormats
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.services.{CreateCaseJourneyService, MongoDBCachedJourneyService}
import uk.gov.hmrc.traderservices.stubs.{TraderServicesApiStubs, UpscanInitiateStubs}
import uk.gov.hmrc.traderservices.support.{ServerISpec, TestJourneyService}
import uk.gov.hmrc.traderservices.support.TestData

import scala.concurrent.ExecutionContext.Implicits.global
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.time.LocalDateTime
import uk.gov.hmrc.traderservices.models.ExportContactInfo
import java.time.ZonedDateTime
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.FileUploadHostData

class CreateCaseJourneyISpec extends CreateCaseJourneyISpecSetup with TraderServicesApiStubs with UpscanInitiateStubs {

  import journey.model.State._
  import journey.model.FileUploadState._

  val dateTime = LocalDateTime.now()

  "CreateCaseJourneyController" when {
    "GET /trader-services/" should {
      "show the start page" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(Start)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.new-or-existing-case.title"))
        result.body should include(htmlEscapedMessage("view.new-or-existing-case.heading"))
        journey.getState shouldBe ChooseNewOrExistingCase()
      }
    }

    "GET /trader-services/pre-clearance/new-or-existing" should {
      "show the choice between new and existing case" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(ChooseNewOrExistingCase())
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/new-or-existing").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.new-or-existing-case.title"))
        result.body should include(htmlEscapedMessage("view.new-or-existing-case.heading"))
        journey.getState shouldBe ChooseNewOrExistingCase()
      }
    }

    "POST /pre-clearance/new-or-existing" should {
      "submit the choice of New and ask next for declaration details" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(ChooseNewOrExistingCase())
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "newOrExistingCase" -> "New"
        )

        val result = await(request("/pre-clearance/new-or-existing").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.declaration-details.title"))
        result.body should include(htmlEscapedMessage("view.declaration-details.heading"))
        journey.getState shouldBe EnterDeclarationDetails()
      }

      "submit the choice of Existing and ask next for case reference number" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(ChooseNewOrExistingCase())
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "newOrExistingCase" -> "Existing"
        )

        val result = await(request("/pre-clearance/new-or-existing").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.case-reference-number.title"))
        result.body should include(htmlEscapedMessage("view.case-reference-number.heading"))
        journey.getState shouldBe TurnToAmendCaseJourney(true)
      }

      "submit invalid choice and re-display the form page with error" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(ChooseNewOrExistingCase())
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "newOrExistingCase" -> "Foo"
        )

        val result = await(request("/pre-clearance/new-or-existing").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitleWithError("view.new-or-existing-case.title"))
        result.body should include(htmlEscapedMessage("view.new-or-existing-case.heading"))
        journey.getState shouldBe ChooseNewOrExistingCase()
      }
    }

    "GET /trader-services/pre-clearance/declaration-details" should {
      "show declaration details page if at Start" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(EnterDeclarationDetails())
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/declaration-details").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.declaration-details.title"))
        result.body should include(htmlEscapedMessage("view.declaration-details.heading"))
        journey.getState shouldBe EnterDeclarationDetails()
      }

      "redisplay pre-filled enter declaration details page " in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsRequestType(
            ExportQuestionsStateModel(
              DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
              ExportQuestions()
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/declaration-details").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.declaration-details.title"))
        result.body should include(htmlEscapedMessage("view.declaration-details.heading"))
        result.body should (include("235") and include("A11111X"))
        result.body should (include("2020") and include("09") and include("23"))
        journey.getState shouldBe EnterDeclarationDetails(
          Some(DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23"))),
          Some(ExportQuestions())
        )
      }
    }

    "POST /pre-clearance/declaration-details" should {
      "submit the form and ask next for requestType when entryNumber is for export" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(EnterDeclarationDetails(None))
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "entryDate.year"  -> "2020",
          "entryDate.month" -> "09",
          "entryDate.day"   -> "23",
          "epu"             -> "235",
          "entryNumber"     -> "A11111X"
        )

        val result = await(request("/pre-clearance/declaration-details").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.requestType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.requestType.heading"))
        journey.getState shouldBe AnswerExportQuestionsRequestType(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
            ExportQuestions()
          )
        )
      }

      "submit the form and go next page when entryNumber is for import" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(EnterDeclarationDetails(None))
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "entryDate.year"  -> "2020",
          "entryDate.month" -> "09",
          "entryDate.day"   -> "23",
          "epu"             -> "235",
          "entryNumber"     -> "111111X"
        )

        val result = await(request("/pre-clearance/declaration-details").post(payload))

        result.status shouldBe 200
        journey.getState shouldBe AnswerImportQuestionsRequestType(
          ImportQuestionsStateModel(
            DeclarationDetails(EPU(235), EntryNumber("111111X"), LocalDate.parse("2020-09-23")),
            ImportQuestions()
          )
        )
      }

      "submit invalid form and re-display the form page with errors" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(EnterDeclarationDetails())
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "entryDate.year"  -> "202a",
          "entryDate.month" -> "00",
          "entryDate.day"   -> "44",
          "epu"             -> "AAA",
          "entryNumber"     -> "A11X"
        )

        val result = await(request("/pre-clearance/declaration-details").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitleWithError("view.declaration-details.title"))
        result.body should include(htmlEscapedMessage("view.declaration-details.heading"))
        journey.getState shouldBe EnterDeclarationDetails()
      }
    }

    "GET /pre-clearance/export-questions/request-type" should {
      "show the export request type question page" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerExportQuestionsRequestType(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
            ExportQuestions()
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/export-questions/request-type").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.requestType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.requestType.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /pre-clearance/export-questions/request-type" should {
      "submit the form and ask next for routeType" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsRequestType(
            ExportQuestionsStateModel(
              DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
              ExportQuestions()
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("requestType" -> "New")

        val result = await(request("/pre-clearance/export-questions/request-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.routeType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.routeType.heading"))
        journey.getState shouldBe AnswerExportQuestionsRouteType(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
            ExportQuestions(requestType = Some(ExportRequestType.New))
          )
        )
      }

      "submit invalid form and re-display the form page with errors" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerExportQuestionsRequestType(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
            ExportQuestions()
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("requestType" -> "Foo")

        val result = await(request("/pre-clearance/export-questions/request-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitleWithError("view.export-questions.requestType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.requestType.heading"))
        journey.getState shouldBe state
      }
    }

    "GET /pre-clearance/export-questions/route-type" should {
      "show the export route type question page" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerExportQuestionsRouteType(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
            ExportQuestions(requestType = Some(ExportRequestType.C1601))
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/export-questions/route-type").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.routeType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.routeType.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /pre-clearance/export-questions/route-type" should {
      "submit the form and ask next for hasPriorityGoods" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsRouteType(
            ExportQuestionsStateModel(
              DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
              ExportQuestions(requestType = Some(ExportRequestType.C1602))
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("routeType" -> "Route3")

        val result = await(request("/pre-clearance/export-questions/route-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.hasPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.hasPriorityGoods.heading"))
        journey.getState shouldBe AnswerExportQuestionsHasPriorityGoods(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
            ExportQuestions(requestType = Some(ExportRequestType.C1602), routeType = Some(ExportRouteType.Route3))
          )
        )
      }

      "submit invalid form and re-display the form page with errors" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerExportQuestionsRouteType(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
            ExportQuestions(requestType = Some(ExportRequestType.C1602))
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("routeType" -> "Foo")

        val result = await(request("/pre-clearance/export-questions/route-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitleWithError("view.export-questions.routeType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.routeType.heading"))
        journey.getState shouldBe state
      }
    }

    "GET /pre-clearance/export-questions/has-priority-goods" should {
      "show the export has priority goods page" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerExportQuestionsHasPriorityGoods(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
            ExportQuestions(requestType = Some(ExportRequestType.C1603), routeType = Some(ExportRouteType.Route6))
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/export-questions/has-priority-goods").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.hasPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.hasPriorityGoods.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /pre-clearance/export-questions/has-priority-goods" should {
      "submit YES choice and ask next for which priority goods" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsHasPriorityGoods(
            ExportQuestionsStateModel(
              DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
              ExportQuestions(requestType = Some(ExportRequestType.New), routeType = Some(ExportRouteType.Route2))
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("hasPriorityGoods" -> "yes")

        val result = await(request("/pre-clearance/export-questions/has-priority-goods").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.whichPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.whichPriorityGoods.heading"))
        journey.getState shouldBe AnswerExportQuestionsWhichPriorityGoods(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route2),
              hasPriorityGoods = Some(true)
            )
          )
        )
      }

      "submit NO choice and ask next for transport type" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsHasPriorityGoods(
            ExportQuestionsStateModel(
              DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route2)
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("hasPriorityGoods" -> "no")

        val result = await(request("/pre-clearance/export-questions/has-priority-goods").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.freightType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.freightType.heading"))
        journey.getState shouldBe AnswerExportQuestionsFreightType(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route2),
              hasPriorityGoods = Some(false)
            )
          )
        )
      }

      "submit empty choice and re-display the form with error" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerExportQuestionsHasPriorityGoods(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route2)
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("hasPriorityGoods" -> "")

        val result = await(request("/pre-clearance/export-questions/has-priority-goods").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitleWithError("view.export-questions.hasPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.hasPriorityGoods.heading"))
        journey.getState shouldBe state
      }
    }

    "GET /pre-clearance/export-questions/which-priority-goods" should {
      "show the export which priority goods page" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerExportQuestionsWhichPriorityGoods(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(requestType = Some(ExportRequestType.C1603), routeType = Some(ExportRouteType.Route6))
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/export-questions/which-priority-goods").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.whichPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.whichPriorityGoods.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /pre-clearance/export-questions/which-priority-goods" should {
      "submit selected priority goods and ask next for transport type" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsWhichPriorityGoods(
            ExportQuestionsStateModel(
              DeclarationDetails(EPU(236), EntryNumber("X11111X"), LocalDate.parse("2020-09-21")),
              ExportQuestions(requestType = Some(ExportRequestType.C1603), routeType = Some(ExportRouteType.Route3))
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("priorityGoods" -> "LiveAnimals")

        val result = await(request("/pre-clearance/export-questions/which-priority-goods").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.freightType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.freightType.heading"))
        journey.getState shouldBe AnswerExportQuestionsFreightType(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(236), EntryNumber("X11111X"), LocalDate.parse("2020-09-21")),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1603),
              routeType = Some(ExportRouteType.Route3),
              priorityGoods = Some(ExportPriorityGoods.LiveAnimals)
            )
          )
        )
      }

      "submit empty priority goods and re-display the form with error" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerExportQuestionsWhichPriorityGoods(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(236), EntryNumber("X11111X"), LocalDate.parse("2020-09-21")),
            ExportQuestions(requestType = Some(ExportRequestType.C1603), routeType = Some(ExportRouteType.Route3))
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("priorityGoods" -> "")

        val result = await(request("/pre-clearance/export-questions/which-priority-goods").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitleWithError("view.export-questions.whichPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.whichPriorityGoods.heading"))
        journey.getState shouldBe state
      }
    }

    "GET /pre-clearance/export-questions/transport-type" should {
      "show the export transport type page" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerExportQuestionsFreightType(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1603),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HighValueArt)
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/export-questions/transport-type").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.freightType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.freightType.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /pre-clearance/export-questions/transport-type" should {
      "submit selected RORO transport type without C1601 and ask next for optional vessel details" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsFreightType(
            ExportQuestionsStateModel(
              DeclarationDetails(EPU(236), EntryNumber("X11111X"), LocalDate.parse("2020-09-21")),
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

        val result = await(request("/pre-clearance/export-questions/transport-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.vessel-details.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.vessel-details.heading"))

        journey.getState shouldBe AnswerExportQuestionsOptionalVesselInfo(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(236), EntryNumber("X11111X"), LocalDate.parse("2020-09-21")),
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
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsFreightType(
            ExportQuestionsStateModel(
              DeclarationDetails(EPU(236), EntryNumber("X11111X"), LocalDate.parse("2020-09-21")),
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

        val result = await(request("/pre-clearance/export-questions/transport-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.vessel-details.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.vessel-details.heading"))

        journey.getState shouldBe AnswerExportQuestionsMandatoryVesselInfo(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(236), EntryNumber("X11111X"), LocalDate.parse("2020-09-21")),
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
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerExportQuestionsFreightType(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(236), EntryNumber("X11111X"), LocalDate.parse("2020-09-21")),
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

        val result = await(request("/pre-clearance/export-questions/transport-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.freightType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.freightType.heading"))

        journey.getState shouldBe state
      }
    }

    "GET /pre-clearance/export-questions/vessel-info-required" should {
      "show the export vessel details page" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerExportQuestionsMandatoryVesselInfo(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1601),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HighValueArt),
              freightType = Some(ExportFreightType.Air)
            )
          )
        )

        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/export-questions/vessel-info-required").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.vessel-details.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.vessel-details.heading"))
        journey.getState shouldBe state
      }
    }

    "GET /pre-clearance/export-questions/vessel-info-required" should {
      "show the export vessel details page when routeType=Hold" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerExportQuestionsMandatoryVesselInfo(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1603),
              routeType = Some(ExportRouteType.Hold),
              priorityGoods = Some(ExportPriorityGoods.HighValueArt),
              freightType = Some(ExportFreightType.Air)
            )
          )
        )

        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/export-questions/vessel-info-required").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.vessel-details.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.vessel-details.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /pre-clearance/export-questions/vessel-info-required" should {
      "submit mandatory vessel details and ask next for contact details" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsMandatoryVesselInfo(
            ExportQuestionsStateModel(
              DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
              ExportQuestions(
                requestType = Some(ExportRequestType.C1602),
                routeType = Some(ExportRouteType.Route6),
                priorityGoods = Some(ExportPriorityGoods.HighValueArt),
                freightType = Some(ExportFreightType.Air)
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

        val result = await(request("/pre-clearance/export-questions/vessel-info-required").post(payload))

        result.status shouldBe 200

        journey.getState shouldBe AnswerExportQuestionsContactInfo(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1602),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HighValueArt),
              freightType = Some(ExportFreightType.Air),
              vesselDetails = Some(
                VesselDetails(
                  vesselName = Some("Foo Bar"),
                  dateOfArrival = Some(dateTimeOfArrival.toLocalDate()),
                  timeOfArrival = Some(dateTimeOfArrival.toLocalTime())
                )
              )
            )
          )
        )
      }

      "submit incomplete vessel details and re-display the form page with error" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerExportQuestionsMandatoryVesselInfo(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1602),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HighValueArt),
              freightType = Some(ExportFreightType.Air)
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "vesselName" -> "Foo Bar"
        )

        val result = await(request("/pre-clearance/export-questions/vessel-info-required").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.vessel-details.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.vessel-details.heading"))

        journey.getState shouldBe state
      }
    }

    "GET /pre-clearance/export-questions/vessel-info" should {
      "show the export vessel details page" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerExportQuestionsOptionalVesselInfo(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HighValueArt),
              freightType = Some(ExportFreightType.Air)
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/export-questions/vessel-info").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.vessel-details.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.vessel-details.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /pre-clearance/export-questions/vessel-info" should {
      "submit optional vessel details and ask next for contact details" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsOptionalVesselInfo(
            ExportQuestionsStateModel(
              DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route6),
                priorityGoods = Some(ExportPriorityGoods.HighValueArt),
                freightType = Some(ExportFreightType.Air)
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

        val result = await(request("/pre-clearance/export-questions/vessel-info").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.contactInfo.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.contactInfo.heading"))

        journey.getState shouldBe AnswerExportQuestionsContactInfo(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HighValueArt),
              freightType = Some(ExportFreightType.Air),
              vesselDetails = Some(
                VesselDetails(
                  vesselName = Some("Foo Bar"),
                  dateOfArrival = Some(dateTimeOfArrival.toLocalDate()),
                  timeOfArrival = Some(dateTimeOfArrival.toLocalTime())
                )
              )
            )
          )
        )
      }

      "submit none vessel details and ask next for contact details" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsOptionalVesselInfo(
            ExportQuestionsStateModel(
              DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route6),
                priorityGoods = Some(ExportPriorityGoods.HighValueArt),
                freightType = Some(ExportFreightType.Air)
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map[String, String]()

        val result = await(request("/pre-clearance/export-questions/vessel-info").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.contactInfo.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.contactInfo.heading"))

        journey.getState shouldBe AnswerExportQuestionsContactInfo(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HighValueArt),
              freightType = Some(ExportFreightType.Air),
              vesselDetails = Some(VesselDetails())
            )
          )
        )
      }

      "submit invalid vessel details and re-display the form page" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerExportQuestionsOptionalVesselInfo(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HighValueArt),
              freightType = Some(ExportFreightType.Air)
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("dateOfArrival.year" -> "202A")

        val result = await(request("/pre-clearance/export-questions/vessel-info").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.vessel-details.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.vessel-details.heading"))

        journey.getState shouldBe state
      }
    }

    "GET /pre-clearance/export-questions/contact-info" should {
      "show the export contact information question page" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsContactInfo(
            ExportQuestionsStateModel(
              DeclarationDetails(EPU(235), EntryNumber("111111X"), LocalDate.parse("2020-09-23")),
              ExportQuestions()
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/export-questions/contact-info").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.contactInfo.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.contactInfo.heading"))
        journey.getState shouldBe AnswerExportQuestionsContactInfo(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(235), EntryNumber("111111X"), LocalDate.parse("2020-09-23")),
            ExportQuestions()
          )
        )
      }
    }

    "POST /pre-clearance/export-questions/contact-info" should {
      "ask for the next page when only email and name submitted" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsContactInfo(
            ExportQuestionsStateModel(
              DeclarationDetails(EPU(235), EntryNumber("111111X"), LocalDate.parse("2020-09-23")),
              ExportQuestions(
                requestType = Some(ExportRequestType.New),
                routeType = Some(ExportRouteType.Route6),
                priorityGoods = Some(ExportPriorityGoods.HighValueArt),
                freightType = Some(ExportFreightType.Air)
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "contactName"  -> "Full Name",
          "contactEmail" -> "someone@email.com"
        )
        val result = await(request("/pre-clearance/export-questions/contact-info").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.summary.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.summary.heading"))

        journey.getState shouldBe ExportQuestionsSummary(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(235), EntryNumber("111111X"), LocalDate.parse("2020-09-23")),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HighValueArt),
              freightType = Some(ExportFreightType.Air),
              contactInfo = Some(ExportContactInfo(contactName = "Full Name", contactEmail = "someone@email.com"))
            )
          )
        )
      }

      "submit invalid contact info and re-display the form page with error" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerExportQuestionsContactInfo(
          ExportQuestionsStateModel(
            DeclarationDetails(EPU(235), EntryNumber("111111X"), LocalDate.parse("2020-09-23")),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HighValueArt),
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
        val result = await(request("/pre-clearance/export-questions/contact-info").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.contactInfo.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.contactInfo.heading"))

        journey.getState shouldBe state
      }
    }

    "GET /pre-clearance/export-questions/check-your-answers" should {
      "show the export questions summary page" in {
        implicit val journeyId: JourneyId = JourneyId()
        val dateTimeOfArrival = dateTime.plusDays(1).truncatedTo(ChronoUnit.MINUTES)
        val state = ExportQuestionsSummary(
          ExportQuestionsStateModel(TestData.exportDeclarationDetails, TestData.fullExportQuestions(dateTimeOfArrival))
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/export-questions/check-your-answers").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.export-questions.summary.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.summary.heading"))
        journey.getState shouldBe state
      }
    }

    "GET /pre-clearance/import-questions/request-type" should {
      "show the import request type question page" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerImportQuestionsRequestType(
          ImportQuestionsStateModel(
            DeclarationDetails(EPU(235), EntryNumber("111111X"), LocalDate.parse("2020-09-23")),
            ImportQuestions()
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/import-questions/request-type").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.requestType.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.requestType.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /pre-clearance/import-questions/request-type" should {
      "submit the form and ask next for route type" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerImportQuestionsRequestType(
            ImportQuestionsStateModel(
              DeclarationDetails(EPU(444), EntryNumber("011111X"), LocalDate.parse("2020-10-01")),
              ImportQuestions(
                requestType = Some(ImportRequestType.Cancellation),
                routeType = Some(ImportRouteType.Route6)
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("requestType" -> "New")

        val result = await(request("/pre-clearance/import-questions/request-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.routeType.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.routeType.heading"))
        journey.getState shouldBe AnswerImportQuestionsRouteType(
          ImportQuestionsStateModel(
            DeclarationDetails(EPU(444), EntryNumber("011111X"), LocalDate.parse("2020-10-01")),
            ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route6))
          )
        )
      }
    }

    "GET /pre-clearance/import-questions/route-type" should {
      "show the import route type question page" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerImportQuestionsRouteType(
          ImportQuestionsStateModel(
            DeclarationDetails(EPU(532), EntryNumber("111111X"), LocalDate.parse("2020-10-08")),
            ImportQuestions(requestType = Some(ImportRequestType.New))
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/import-questions/route-type").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.routeType.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.routeType.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /pre-clearance/import-questions/route-type" should {
      "submit the form and ask next for hasPriorityGoods" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerImportQuestionsRouteType(
            ImportQuestionsStateModel(
              DeclarationDetails(EPU(444), EntryNumber("011111X"), LocalDate.parse("2020-10-01")),
              ImportQuestions(requestType = Some(ImportRequestType.Cancellation))
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("routeType" -> "Route6")

        val result = await(request("/pre-clearance/import-questions/route-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.hasPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.hasPriorityGoods.heading"))
        journey.getState shouldBe AnswerImportQuestionsHasPriorityGoods(
          ImportQuestionsStateModel(
            DeclarationDetails(EPU(444), EntryNumber("011111X"), LocalDate.parse("2020-10-01")),
            ImportQuestions(
              requestType = Some(ImportRequestType.Cancellation),
              routeType = Some(ImportRouteType.Route6)
            )
          )
        )
      }
    }

    "GET /pre-clearance/import-questions/has-priority-goods" should {
      "show the import has priority goods page" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerImportQuestionsHasPriorityGoods(
          ImportQuestionsStateModel(
            DeclarationDetails(EPU(110), EntryNumber("911111X"), LocalDate.parse("2020-09-23")),
            ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route6))
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/import-questions/has-priority-goods").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.hasPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.hasPriorityGoods.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /pre-clearance/import-questions/has-priority-goods" should {
      "submit YES choice and ask next for which priority goods" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerImportQuestionsHasPriorityGoods(
            ImportQuestionsStateModel(
              DeclarationDetails(EPU(101), EntryNumber("811111X"), LocalDate.parse("2020-09-23")),
              ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route2))
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("hasPriorityGoods" -> "yes")

        val result = await(request("/pre-clearance/import-questions/has-priority-goods").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.whichPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.whichPriorityGoods.heading"))
        journey.getState shouldBe AnswerImportQuestionsWhichPriorityGoods(
          ImportQuestionsStateModel(
            DeclarationDetails(EPU(101), EntryNumber("811111X"), LocalDate.parse("2020-09-23")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route2),
              hasPriorityGoods = Some(true)
            )
          )
        )
      }

      "submit NO choice and ask next for transport type" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerImportQuestionsHasPriorityGoods(
            ImportQuestionsStateModel(
              DeclarationDetails(EPU(100), EntryNumber("711111X"), LocalDate.parse("2020-09-23")),
              ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route2))
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("hasPriorityGoods" -> "no")

        val result = await(request("/pre-clearance/import-questions/has-priority-goods").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.hasALVS.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.hasALVS.heading"))
        journey.getState shouldBe AnswerImportQuestionsALVS(
          ImportQuestionsStateModel(
            DeclarationDetails(EPU(100), EntryNumber("711111X"), LocalDate.parse("2020-09-23")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route2),
              hasPriorityGoods = Some(false)
            )
          )
        )
      }
    }

    "GET /pre-clearance/import-questions/which-priority-goods" should {
      "show the import which priority goods page" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerImportQuestionsWhichPriorityGoods(
          ImportQuestionsStateModel(
            DeclarationDetails(EPU(230), EntryNumber("111111Z"), LocalDate.parse("2020-10-05")),
            ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route6))
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/import-questions/which-priority-goods").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.whichPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.whichPriorityGoods.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /pre-clearance/import-questions/which-priority-goods" should {
      "submit selected priority goods and ask next for automatic licence verification" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerImportQuestionsWhichPriorityGoods(
            ImportQuestionsStateModel(
              DeclarationDetails(EPU(236), EntryNumber("011111X"), LocalDate.parse("2020-09-21")),
              ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route3))
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("priorityGoods" -> "LiveAnimals")

        val result = await(request("/pre-clearance/import-questions/which-priority-goods").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.hasALVS.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.hasALVS.heading"))
        journey.getState shouldBe AnswerImportQuestionsALVS(
          ImportQuestionsStateModel(
            DeclarationDetails(EPU(236), EntryNumber("011111X"), LocalDate.parse("2020-09-21")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route3),
              priorityGoods = Some(ImportPriorityGoods.LiveAnimals)
            )
          )
        )
      }
    }

    "GET /pre-clearance/import-questions/automatic-licence-verification" should {
      "show the import has ALVS page" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerImportQuestionsALVS(
          ImportQuestionsStateModel(
            DeclarationDetails(EPU(235), EntryNumber("711111X"), LocalDate.parse("2020-09-23")),
            ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route2))
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/import-questions/automatic-licence-verification").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.hasALVS.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.hasALVS.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /pre-clearance/import-questions/automatic-licence-verification" should {
      for (hasALVS <- Seq(true, false))
        s"submit ${if (hasALVS) "YES" else "NO"} choice and ask next for transport type" in {
          implicit val journeyId: JourneyId = JourneyId()
          journey.setState(
            AnswerImportQuestionsALVS(
              ImportQuestionsStateModel(
                DeclarationDetails(EPU(235), EntryNumber("011111X"), LocalDate.parse("2020-09-23")),
                ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route2))
              )
            )
          )
          givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

          val payload = Map("hasALVS" -> { if (hasALVS) "yes" else "no" })

          val result = await(request("/pre-clearance/import-questions/automatic-licence-verification").post(payload))

          result.status shouldBe 200
          result.body should include(
            htmlEscapedMessage("view.import-questions.freightType.title") + " - " + htmlEscapedMessage(
              "site.serviceName"
            ) + " - " + htmlEscapedMessage("site.govuk")
          )
          result.body should include(htmlEscapedMessage("view.import-questions.freightType.heading"))
          journey.getState shouldBe AnswerImportQuestionsFreightType(
            ImportQuestionsStateModel(
              DeclarationDetails(EPU(235), EntryNumber("011111X"), LocalDate.parse("2020-09-23")),
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route2),
                hasALVS = Some(hasALVS)
              )
            )
          )
        }
    }

    "GET /pre-clearance/import-questions/transport-type" should {
      "show the import transport type page" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerImportQuestionsFreightType(
          ImportQuestionsStateModel(
            DeclarationDetails(EPU(230), EntryNumber("311111Z"), LocalDate.parse("2020-10-05")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route6)
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/import-questions/transport-type").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.freightType.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.freightType.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /pre-clearance/import-questions/transport-type" should {
      "submit selected transport type and ask next for optional vessel details" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerImportQuestionsFreightType(
            ImportQuestionsStateModel(
              DeclarationDetails(EPU(236), EntryNumber("211111X"), LocalDate.parse("2020-09-21")),
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route3)
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("freightType" -> "RORO")

        val result = await(request("/pre-clearance/import-questions/transport-type").post(payload))

        result.status shouldBe 200

        journey.getState shouldBe AnswerImportQuestionsOptionalVesselInfo(
          ImportQuestionsStateModel(
            DeclarationDetails(EPU(236), EntryNumber("211111X"), LocalDate.parse("2020-09-21")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route3),
              freightType = Some(ImportFreightType.RORO)
            )
          )
        )
      }

      "submit selected transport type and ask next for mandatory vessel details" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerImportQuestionsFreightType(
            ImportQuestionsStateModel(
              DeclarationDetails(EPU(100), EntryNumber("011111X"), LocalDate.parse("2020-09-21")),
              ImportQuestions(
                routeType = Some(ImportRouteType.Hold),
                requestType = Some(ImportRequestType.New)
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("freightType" -> "Maritime")

        val result = await(request("/pre-clearance/import-questions/transport-type").post(payload))

        result.status shouldBe 200

        journey.getState shouldBe AnswerImportQuestionsMandatoryVesselInfo(
          ImportQuestionsStateModel(
            DeclarationDetails(EPU(100), EntryNumber("011111X"), LocalDate.parse("2020-09-21")),
            ImportQuestions(
              routeType = Some(ImportRouteType.Hold),
              requestType = Some(ImportRequestType.New),
              freightType = Some(ImportFreightType.Maritime)
            )
          )
        )
      }
    }

    "GET /pre-clearance/import-questions/vessel-info" should {
      "show the import vessel details page" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerImportQuestionsOptionalVesselInfo(
          ImportQuestionsStateModel(
            DeclarationDetails(EPU(230), EntryNumber("111111Z"), LocalDate.parse("2020-10-05")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route6),
              priorityGoods = Some(ImportPriorityGoods.HighValueArt),
              freightType = Some(ImportFreightType.Air)
            )
          )
        )

        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/import-questions/vessel-info").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.vessel-details.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.vessel-details.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /pre-clearance/import-questions/vessel-info" should {
      "submit optional vessel details and ask next for contact details" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerImportQuestionsOptionalVesselInfo(
            ImportQuestionsStateModel(
              DeclarationDetails(EPU(230), EntryNumber("111111Z"), LocalDate.parse("2020-10-05")),
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route6),
                priorityGoods = Some(ImportPriorityGoods.HighValueArt),
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

        val result = await(request("/pre-clearance/import-questions/vessel-info").post(payload))

        result.status shouldBe 200

        journey.getState shouldBe AnswerImportQuestionsContactInfo(
          ImportQuestionsStateModel(
            DeclarationDetails(EPU(230), EntryNumber("111111Z"), LocalDate.parse("2020-10-05")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route6),
              priorityGoods = Some(ImportPriorityGoods.HighValueArt),
              freightType = Some(ImportFreightType.Air),
              vesselDetails = Some(
                VesselDetails(
                  vesselName = Some("Foo Bar"),
                  dateOfArrival = Some(dateTimeOfArrival.toLocalDate()),
                  timeOfArrival = Some(dateTimeOfArrival.toLocalTime())
                )
              )
            )
          )
        )
      }

      "submit none vessel details and ask next for contact details" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerImportQuestionsOptionalVesselInfo(
            ImportQuestionsStateModel(
              DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route6),
                priorityGoods = Some(ImportPriorityGoods.HighValueArt),
                freightType = Some(ImportFreightType.Air)
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map[String, String]()

        val result = await(request("/pre-clearance/import-questions/vessel-info").post(payload))

        result.status shouldBe 200

        journey.getState shouldBe AnswerImportQuestionsContactInfo(
          ImportQuestionsStateModel(
            DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route6),
              priorityGoods = Some(ImportPriorityGoods.HighValueArt),
              freightType = Some(ImportFreightType.Air),
              vesselDetails = Some(VesselDetails())
            )
          )
        )
      }
    }

    "GET /pre-clearance/import-questions/contact-info" should {
      "show the import contact information question page" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AnswerImportQuestionsContactInfo(
          ImportQuestionsStateModel(
            DeclarationDetails(EPU(235), EntryNumber("111111X"), LocalDate.parse("2020-09-23")),
            ImportQuestions()
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/import-questions/contact-info").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.contactInfo.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.contactInfo.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /pre-clearance/import-questions/contact-info" should {
      "ask for the next page when only email and name submitted" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerImportQuestionsContactInfo(
            ImportQuestionsStateModel(
              DeclarationDetails(EPU(235), EntryNumber("111111X"), LocalDate.parse("2020-09-23")),
              ImportQuestions(
                requestType = Some(ImportRequestType.New),
                routeType = Some(ImportRouteType.Route6),
                priorityGoods = Some(ImportPriorityGoods.HighValueArt),
                freightType = Some(ImportFreightType.Air)
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "contactName"  -> "Full Name",
          "contactEmail" -> "someone@email.com"
        )

        val result = await(request("/pre-clearance/import-questions/contact-info").post(payload))

        result.status shouldBe 200

        journey.getState shouldBe ImportQuestionsSummary(
          ImportQuestionsStateModel(
            DeclarationDetails(EPU(235), EntryNumber("111111X"), LocalDate.parse("2020-09-23")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route6),
              priorityGoods = Some(ImportPriorityGoods.HighValueArt),
              freightType = Some(ImportFreightType.Air),
              contactInfo = Some(ImportContactInfo(contactName = "Full Name", contactEmail = "someone@email.com"))
            )
          )
        )
      }
    }

    "GET /pre-clearance/import-questions/check-your-answers" should {
      "show the import questions summary page" in {
        implicit val journeyId: JourneyId = JourneyId()
        val dateTimeOfArrival = dateTime.plusDays(1).truncatedTo(ChronoUnit.MINUTES)
        val state = ImportQuestionsSummary(
          ImportQuestionsStateModel(TestData.importDeclarationDetails, TestData.fullImportQuestions(dateTimeOfArrival))
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/import-questions/check-your-answers").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.import-questions.summary.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.summary.heading"))
        journey.getState shouldBe state
      }
    }

    "GET /pre-clearance/file-upload" should {
      "show the upload first document page for the importer" in {
        implicit val journeyId: JourneyId = JourneyId()
        val dateTimeOfArrival = dateTime.plusDays(1).truncatedTo(ChronoUnit.MINUTES)
        val state = ImportQuestionsSummary(
          ImportQuestionsStateModel(TestData.importDeclarationDetails, TestData.fullImportQuestions(dateTimeOfArrival))
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val callbackUrl =
          wireMockBaseUrl + s"/trader-services/pre-clearance/journey/${journeyId.value}/callback-from-upscan"
        givenUpscanInitiateSucceeds(callbackUrl)

        val result = await(request("/pre-clearance/file-upload").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-file.first.title"))
        result.body should include(htmlEscapedMessage("view.upload-file.first.heading"))
        journey.getState shouldBe UploadFile(
          FileUploadHostData(TestData.importDeclarationDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
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
          fileUploads = FileUploads(files = Seq(FileUpload.Initiated(1, "11370e18-6e24-453e-b45a-76d3e32ea33d")))
        )
      }

      "show the upload first document page for the exporter" in {
        implicit val journeyId: JourneyId = JourneyId()
        val dateTimeOfArrival = dateTime.plusDays(1).truncatedTo(ChronoUnit.MINUTES)
        val state = ExportQuestionsSummary(
          ExportQuestionsStateModel(TestData.exportDeclarationDetails, TestData.fullExportQuestions(dateTimeOfArrival))
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val callbackUrl =
          wireMockBaseUrl + s"/trader-services/pre-clearance/journey/${journeyId.value}/callback-from-upscan"
        givenUpscanInitiateSucceeds(callbackUrl)

        val result = await(request("/pre-clearance/file-upload").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-file.first.title"))
        result.body should include(htmlEscapedMessage("view.upload-file.first.heading"))
        journey.getState shouldBe UploadFile(
          FileUploadHostData(TestData.exportDeclarationDetails, TestData.fullExportQuestions(dateTimeOfArrival)),
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
          fileUploads = FileUploads(files = Seq(FileUpload.Initiated(1, "11370e18-6e24-453e-b45a-76d3e32ea33d")))
        )
      }
    }

    "POST /pre-clearance/create-case" should {
      "create case and show the confirmation page" in {
        implicit val journeyId: JourneyId = JourneyId()
        val dateTimeOfArrival = dateTime.plusDays(1).truncatedTo(ChronoUnit.MINUTES)
        journey.setState(
          FileUploaded(
            FileUploadHostData(TestData.exportDeclarationDetails, TestData.fullExportQuestions(dateTimeOfArrival)),
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
            acknowledged = true
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "GB123456789012345"))
        givenCreateCaseApiRequestSucceeds()

        val result = await(request("/pre-clearance/create-case").post(""))

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.create-case-confirmation.title"))
        result.body should include(htmlEscapedMessage("view.create-case-confirmation.heading"))
        journey.getState shouldBe CreateCaseConfirmation(
          TestData.exportDeclarationDetails,
          TestData.fullExportQuestions(dateTimeOfArrival),
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
      }
    }

    "GET /pre-clearance/confirmation" should {
      "show the confirmation page" in {
        implicit val journeyId: JourneyId = JourneyId()
        val dateTimeOfArrival = dateTime.plusDays(1).truncatedTo(ChronoUnit.MINUTES)
        val state = CreateCaseConfirmation(
          TestData.exportDeclarationDetails,
          TestData.fullExportQuestions(dateTimeOfArrival),
          Seq(
            UploadedFile(
              "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
              ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              "test.pdf",
              "application/pdf"
            )
          ),
          "TBC"
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/confirmation").get)

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.create-case-confirmation.title"))
        result.body should include(htmlEscapedMessage("view.create-case-confirmation.heading"))
        journey.getState shouldBe state
      }
    }

    "GET /pre-clearance/journey/:journeyId/file-rejected" should {
      "set current file upload status as rejected and return 204 NoContent" in {
        implicit val journeyId: JourneyId = JourneyId()
        val dateTimeOfArrival = dateTime.plusDays(1).truncatedTo(ChronoUnit.MINUTES)
        journey.setState(
          UploadFile(
            FileUploadHostData(TestData.importDeclarationDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
            "11370e18-6e24-453e-b45a-76d3e32ea33d",
            UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            FileUploads(files =
              Seq(
                FileUpload.Initiated(1, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(2, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result1 =
          await(
            requestWithoutJourneyId(
              s"/pre-clearance/journey/${journeyId.value}/file-rejected?key=11370e18-6e24-453e-b45a-76d3e32ea33d&errorCode=ABC123&errorMessage=ABC+123"
            ).get()
          )

        result1.status shouldBe 204
        result1.body.isEmpty shouldBe true
        journey.getState shouldBe (
          UploadFile(
            FileUploadHostData(TestData.importDeclarationDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
            "11370e18-6e24-453e-b45a-76d3e32ea33d",
            UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            FileUploads(files =
              Seq(
                FileUpload.Rejected(
                  1,
                  "11370e18-6e24-453e-b45a-76d3e32ea33d",
                  S3UploadError(
                    key = "11370e18-6e24-453e-b45a-76d3e32ea33d",
                    errorCode = "ABC123",
                    errorMessage = "ABC 123"
                  )
                ),
                FileUpload.Posted(2, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            ),
            Some(
              FileTransmissionFailed(
                S3UploadError("11370e18-6e24-453e-b45a-76d3e32ea33d", "ABC123", "ABC 123", None, None)
              )
            )
          )
        )
      }
    }

    "GET /pre-clearance/journey/:journeyId/file-verification" should {
      "set current file upload status as posted and return 204 NoContent" in {
        implicit val journeyId: JourneyId = JourneyId()
        val dateTimeOfArrival = dateTime.plusDays(1).truncatedTo(ChronoUnit.MINUTES)
        journey.setState(
          UploadFile(
            FileUploadHostData(TestData.importDeclarationDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
            "11370e18-6e24-453e-b45a-76d3e32ea33d",
            UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            FileUploads(files =
              Seq(
                FileUpload.Initiated(1, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(2, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result1 =
          await(requestWithoutJourneyId(s"/pre-clearance/journey/${journeyId.value}/file-verification").get())

        result1.status shouldBe 204
        result1.body.isEmpty shouldBe true
        journey.getState shouldBe (
          WaitingForFileVerification(
            FileUploadHostData(TestData.importDeclarationDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
            "11370e18-6e24-453e-b45a-76d3e32ea33d",
            UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            FileUpload.Posted(1, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(2, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
      }
    }

    "GET /pre-clearance/file-verification/:reference/status" should {
      "return file verification status" in {
        implicit val journeyId: JourneyId = JourneyId()
        val dateTimeOfArrival = dateTime.plusDays(1).truncatedTo(ChronoUnit.MINUTES)
        val state = FileUploaded(
          FileUploadHostData(TestData.importDeclarationDetails, TestData.fullImportQuestions(dateTimeOfArrival)),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(1, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Posted(2, "2b72fe99-8adf-4edb-865e-622ae710f77c"),
              FileUpload.Accepted(
                4,
                "f029444f-415c-4dec-9cf2-36774ec63ab8",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf"
              ),
              FileUpload.Failed(
                3,
                "4b1e15a4-4152-4328-9448-4924d9aee6e2",
                UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
              )
            )
          ),
          acknowledged = false
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result1 =
          await(
            request("/pre-clearance/file-verification/11370e18-6e24-453e-b45a-76d3e32ea33d/status")
              .get()
          )
        result1.status shouldBe 200
        result1.body shouldBe """{"fileStatus":"NOT_UPLOADED"}"""
        journey.getState shouldBe state

        val result2 =
          await(request("/pre-clearance/file-verification/2b72fe99-8adf-4edb-865e-622ae710f77c/status").get())
        result2.status shouldBe 200
        result2.body shouldBe """{"fileStatus":"WAITING"}"""
        journey.getState shouldBe state

        val result3 =
          await(request("/pre-clearance/file-verification/f029444f-415c-4dec-9cf2-36774ec63ab8/status").get())
        result3.status shouldBe 200
        result3.body shouldBe """{"fileStatus":"ACCEPTED"}"""
        journey.getState shouldBe state

        val result4 =
          await(request("/pre-clearance/file-verification/4b1e15a4-4152-4328-9448-4924d9aee6e2/status").get())
        result4.status shouldBe 200
        result4.body shouldBe """{"fileStatus":"FAILED"}"""
        journey.getState shouldBe state

        val result5 =
          await(request("/pre-clearance/file-verification/f0e317f5-d394-42cc-93f8-e89f4fc0114c/status").get())
        result5.status shouldBe 404
        journey.getState shouldBe state
      }
    }

    "GET /trader-services/foo" should {
      "return an error page not found" in {
        implicit val journeyId: JourneyId = JourneyId()
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/foo").get())

        result.status shouldBe 404
        result.body should include("Page not found")
        journey.get shouldBe None
      }
    }
  }
}

trait CreateCaseJourneyISpecSetup extends ServerISpec {

  override def fakeApplication: Application = appBuilder.build()

  lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]
  lazy val sessionCookieBaker: SessionCookieBaker = app.injector.instanceOf[SessionCookieBaker]
  lazy val sessionCookieCrypto: SessionCookieCrypto = app.injector.instanceOf[SessionCookieCrypto]

  case class JourneyId(value: String = UUID.randomUUID().toString)

  // define test service capable of manipulating journey state
  lazy val journey = new TestJourneyService[JourneyId]
    with CreateCaseJourneyService[JourneyId] with MongoDBCachedJourneyService[JourneyId] {

    override lazy val cacheMongoRepository = app.injector.instanceOf[CacheMongoRepository]
    override lazy val applicationCrypto = app.injector.instanceOf[ApplicationCrypto]

    override val stateFormats: Format[model.State] =
      CreateCaseJourneyStateFormats.formats

    override def getJourneyId(journeyId: JourneyId): Option[String] = Some(journeyId.value)
  }

  val baseUrl: String = s"http://localhost:$port/trader-services"

  def request(path: String)(implicit journeyId: JourneyId) = {
    val sessionCookie = sessionCookieBaker.encodeAsCookie(Session(Map(journey.journeyKey -> journeyId.value)))

    wsClient
      .url(s"$baseUrl$path")
      .withHttpHeaders(
        play.api.http.HeaderNames.COOKIE -> Cookies.encodeCookieHeader(
          Seq(
            sessionCookie.copy(value = sessionCookieCrypto.crypto.encrypt(PlainText(sessionCookie.value)).value)
          )
        )
      )
  }

  def requestWithoutJourneyId(path: String) =
    wsClient
      .url(s"$baseUrl$path")

}
