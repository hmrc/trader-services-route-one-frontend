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
import uk.gov.hmrc.traderservices.journeys.TraderServicesFrontendJourneyStateFormats
import uk.gov.hmrc.traderservices.models.{DeclarationDetails, EPU, EntryNumber, ExportQuestions, ImportQuestions, VesselDetails}
import uk.gov.hmrc.traderservices.services.{MongoDBCachedJourneyService, TraderServicesFrontendJourneyService}
import uk.gov.hmrc.traderservices.stubs.{JourneyTestData, TraderServicesStubs}
import uk.gov.hmrc.traderservices.support.{ServerISpec, TestJourneyService}

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.traderservices.models.ExportRequestType
import uk.gov.hmrc.traderservices.models.ExportRouteType
import uk.gov.hmrc.traderservices.models.ExportPriorityGoods
import uk.gov.hmrc.traderservices.models.ExportFreightType
import uk.gov.hmrc.traderservices.models.ImportRequestType
import uk.gov.hmrc.traderservices.models.ImportRouteType
import uk.gov.hmrc.traderservices.models.ImportFreightType
import uk.gov.hmrc.traderservices.models.ImportPriorityGoods
import uk.gov.hmrc.traderservices.models.VesselDetails
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.time.LocalDateTime

class TraderServicesFrontendISpec
    extends TraderServicesFrontendISpecSetup with TraderServicesStubs with JourneyTestData {

  import journey.model.State._

  val dateTime = LocalDateTime.now()

  "TraderServicesFrontend" when {

    "GET /trader-services/" should {
      "show the start page" in {
        implicit val journeyId: JourneyId = JourneyId()
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.start.title"))
        journey.getState shouldBe Start
      }
    }

    "GET /trader-services/pre-clearance/declaration-details" should {
      "show the enter declaration details page" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(Start)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/declaration-details").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.declaration-details.title"))
        result.body should include(htmlEscapedMessage("view.declaration-details.heading"))
        journey.getState shouldBe EnterDeclarationDetails(None)
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
        result.body should include(htmlEscapedMessage("view.export-questions.requestType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.requestType.heading"))
        journey.getState shouldBe AnswerExportQuestionsRequestType(
          DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
          ExportQuestions()
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
          DeclarationDetails(EPU(235), EntryNumber("111111X"), LocalDate.parse("2020-09-23")),
          ImportQuestions()
        )
      }
    }

    "GET /pre-clearance/export-questions/request-type" should {
      "show the export request type question page" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsRequestType(
            DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
            ExportQuestions()
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/export-questions/request-type").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.export-questions.requestType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.requestType.heading"))
        journey.getState shouldBe AnswerExportQuestionsRequestType(
          DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
          ExportQuestions()
        )
      }
    }

    "POST /pre-clearance/export-questions/request-type" should {

      "submit the form and ask next for routeType if not Hold" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsRequestType(
            DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
            ExportQuestions()
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("requestType" -> "New")

        val result = await(request("/pre-clearance/export-questions/request-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.export-questions.routeType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.routeType.heading"))
        journey.getState shouldBe AnswerExportQuestionsRouteType(
          DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
          ExportQuestions(requestType = Some(ExportRequestType.New))
        )
      }

      "submit the form and, if Hold, ask next does the consignment has any priority goods" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsRequestType(
            DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
            ExportQuestions()
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("requestType" -> "Hold")

        val result = await(request("/pre-clearance/export-questions/request-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.export-questions.hasPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.hasPriorityGoods.heading"))
        journey.getState shouldBe AnswerExportQuestionsHasPriorityGoods(
          DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
          ExportQuestions(requestType = Some(ExportRequestType.Hold))
        )
      }
    }

    "GET /pre-clearance/export-questions/route-type" should {
      "show the export route type question page" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsRouteType(
            DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
            ExportQuestions(requestType = Some(ExportRequestType.C1601))
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/export-questions/route-type").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.export-questions.routeType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.routeType.heading"))
        journey.getState shouldBe AnswerExportQuestionsRouteType(
          DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
          ExportQuestions(requestType = Some(ExportRequestType.C1601))
        )
      }
    }

    "POST /pre-clearance/export-questions/route-type" should {

      "submit the form and ask next for hasPriorityGoods" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsRouteType(
            DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
            ExportQuestions(requestType = Some(ExportRequestType.C1602))
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("routeType" -> "Route3")

        val result = await(request("/pre-clearance/export-questions/route-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.export-questions.hasPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.hasPriorityGoods.heading"))
        journey.getState shouldBe AnswerExportQuestionsHasPriorityGoods(
          DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
          ExportQuestions(requestType = Some(ExportRequestType.C1602), routeType = Some(ExportRouteType.Route3))
        )
      }
    }

    "GET /pre-clearance/export-questions/has-priority-goods" should {
      "show the export has priority goods page" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsHasPriorityGoods(
            DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
            ExportQuestions(requestType = Some(ExportRequestType.C1603), routeType = Some(ExportRouteType.Route6))
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/export-questions/has-priority-goods").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.export-questions.hasPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.hasPriorityGoods.heading"))
        journey.getState shouldBe AnswerExportQuestionsHasPriorityGoods(
          DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
          ExportQuestions(requestType = Some(ExportRequestType.C1603), routeType = Some(ExportRouteType.Route6))
        )
      }
    }

    "POST /pre-clearance/export-questions/has-priority-goods" should {
      "submit YES choice and ask next for which priority goods" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsHasPriorityGoods(
            DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
            ExportQuestions(requestType = Some(ExportRequestType.New), routeType = Some(ExportRouteType.Route2))
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("hasPriorityGoods" -> "yes")

        val result = await(request("/pre-clearance/export-questions/has-priority-goods").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.export-questions.whichPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.whichPriorityGoods.heading"))
        journey.getState shouldBe AnswerExportQuestionsWhichPriorityGoods(
          DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
          ExportQuestions(requestType = Some(ExportRequestType.New), routeType = Some(ExportRouteType.Route2))
        )
      }

      "submit NO choice and ask next for transport type" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsHasPriorityGoods(
            DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
            ExportQuestions(requestType = Some(ExportRequestType.New), routeType = Some(ExportRouteType.Route2))
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("hasPriorityGoods" -> "no")

        val result = await(request("/pre-clearance/export-questions/has-priority-goods").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.export-questions.freightType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.freightType.heading"))
        journey.getState shouldBe AnswerExportQuestionsFreightType(
          DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
          ExportQuestions(requestType = Some(ExportRequestType.New), routeType = Some(ExportRouteType.Route2))
        )
      }
    }

    "GET /pre-clearance/export-questions/which-priority-goods" should {
      "show the export which priority goods page" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsWhichPriorityGoods(
            DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(requestType = Some(ExportRequestType.C1603), routeType = Some(ExportRouteType.Route6))
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/export-questions/which-priority-goods").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.export-questions.whichPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.whichPriorityGoods.heading"))
        journey.getState shouldBe AnswerExportQuestionsWhichPriorityGoods(
          DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
          ExportQuestions(requestType = Some(ExportRequestType.C1603), routeType = Some(ExportRouteType.Route6))
        )
      }
    }

    "POST /pre-clearance/export-questions/which-priority-goods" should {
      "submit selected priority goods and ask next for transport type" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsWhichPriorityGoods(
            DeclarationDetails(EPU(236), EntryNumber("X11111X"), LocalDate.parse("2020-09-21")),
            ExportQuestions(requestType = Some(ExportRequestType.C1603), routeType = Some(ExportRouteType.Route3))
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("priorityGoods" -> "LiveAnimals")

        val result = await(request("/pre-clearance/export-questions/which-priority-goods").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.export-questions.freightType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.freightType.heading"))
        journey.getState shouldBe AnswerExportQuestionsFreightType(
          DeclarationDetails(EPU(236), EntryNumber("X11111X"), LocalDate.parse("2020-09-21")),
          ExportQuestions(
            requestType = Some(ExportRequestType.C1603),
            routeType = Some(ExportRouteType.Route3),
            priorityGoods = Some(ExportPriorityGoods.LiveAnimals)
          )
        )
      }
    }

    "GET /pre-clearance/export-questions/transport-type" should {
      "show the export transport type page" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsFreightType(
            DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1603),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HighValueArt)
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/export-questions/transport-type").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.export-questions.freightType.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.freightType.heading"))
        journey.getState shouldBe AnswerExportQuestionsFreightType(
          DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
          ExportQuestions(
            requestType = Some(ExportRequestType.C1603),
            routeType = Some(ExportRouteType.Route6),
            priorityGoods = Some(ExportPriorityGoods.HighValueArt)
          )
        )
      }
    }

    "POST /pre-clearance/export-questions/transport-type" should {
      "submit selected RORO transport type without C1601 and ask next for optional vessel details" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsFreightType(
            DeclarationDetails(EPU(236), EntryNumber("X11111X"), LocalDate.parse("2020-09-21")),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1603),
              routeType = Some(ExportRouteType.Route3),
              priorityGoods = Some(ExportPriorityGoods.LiveAnimals)
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("freightType" -> "RORO")

        val result = await(request("/pre-clearance/export-questions/transport-type").post(payload))

        result.status shouldBe 200

        journey.getState shouldBe AnswerExportQuestionsOptionalVesselInfo(
          DeclarationDetails(EPU(236), EntryNumber("X11111X"), LocalDate.parse("2020-09-21")),
          ExportQuestions(
            requestType = Some(ExportRequestType.C1603),
            routeType = Some(ExportRouteType.Route3),
            priorityGoods = Some(ExportPriorityGoods.LiveAnimals),
            freightType = Some(ExportFreightType.RORO)
          )
        )
      }

      "submit selected Air transport type with C1601 and ask next for mandatory vessel details" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsFreightType(
            DeclarationDetails(EPU(236), EntryNumber("X11111X"), LocalDate.parse("2020-09-21")),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1601),
              routeType = Some(ExportRouteType.Route3),
              priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks)
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("freightType" -> "Air")

        val result = await(request("/pre-clearance/export-questions/transport-type").post(payload))

        result.status shouldBe 200

        journey.getState shouldBe AnswerExportQuestionsMandatoryVesselInfo(
          DeclarationDetails(EPU(236), EntryNumber("X11111X"), LocalDate.parse("2020-09-21")),
          ExportQuestions(
            requestType = Some(ExportRequestType.C1601),
            routeType = Some(ExportRouteType.Route3),
            priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
            freightType = Some(ExportFreightType.Air)
          )
        )
      }
    }

    "GET /pre-clearance/export-questions/vessel-info-required" should {
      "show the export vessel details page" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsMandatoryVesselInfo(
            DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1601),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HighValueArt),
              freightType = Some(ExportFreightType.Air)
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/export-questions/vessel-info-required").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.export-questions.vessel-details.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.vessel-details.heading"))
        journey.getState shouldBe AnswerExportQuestionsMandatoryVesselInfo(
          DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
          ExportQuestions(
            requestType = Some(ExportRequestType.C1601),
            routeType = Some(ExportRouteType.Route6),
            priorityGoods = Some(ExportPriorityGoods.HighValueArt),
            freightType = Some(ExportFreightType.Air)
          )
        )
      }
    }

    "POST /pre-clearance/export-questions/vessel-info-required" should {
      "submit mandatory vessel details and ask next for contact details" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsMandatoryVesselInfo(
            DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1601),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HighValueArt),
              freightType = Some(ExportFreightType.Air)
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
          "timeOfArrival.hour"    -> f"${dateTimeOfArrival.get(ChronoField.CLOCK_HOUR_OF_AMPM)}%02d",
          "timeOfArrival.minutes" -> f"${dateTimeOfArrival.get(ChronoField.MINUTE_OF_HOUR)}%02d",
          "timeOfArrival.period"  -> { if (dateTimeOfArrival.get(ChronoField.AMPM_OF_DAY) == 0) "AM" else "PM" }
        )

        val result = await(request("/pre-clearance/export-questions/vessel-info-required").post(payload))

        result.status shouldBe 501

        journey.getState shouldBe AnswerExportQuestionsContactInfo(
          DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
          ExportQuestions(
            requestType = Some(ExportRequestType.C1601),
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
      }
    }

    "GET /pre-clearance/export-questions/vessel-info" should {
      "show the export vessel details page" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsOptionalVesselInfo(
            DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HighValueArt),
              freightType = Some(ExportFreightType.Air)
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/export-questions/vessel-info").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.export-questions.vessel-details.title"))
        result.body should include(htmlEscapedMessage("view.export-questions.vessel-details.heading"))
        journey.getState shouldBe AnswerExportQuestionsOptionalVesselInfo(
          DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
          ExportQuestions(
            requestType = Some(ExportRequestType.New),
            routeType = Some(ExportRouteType.Route6),
            priorityGoods = Some(ExportPriorityGoods.HighValueArt),
            freightType = Some(ExportFreightType.Air)
          )
        )
      }
    }

    "POST /pre-clearance/export-questions/vessel-info" should {
      "submit optional vessel details and ask next for contact details" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsOptionalVesselInfo(
            DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HighValueArt),
              freightType = Some(ExportFreightType.Air)
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
          "timeOfArrival.hour"    -> f"${dateTimeOfArrival.get(ChronoField.CLOCK_HOUR_OF_AMPM)}%02d",
          "timeOfArrival.minutes" -> f"${dateTimeOfArrival.get(ChronoField.MINUTE_OF_HOUR)}%02d",
          "timeOfArrival.period"  -> { if (dateTimeOfArrival.get(ChronoField.AMPM_OF_DAY) == 0) "AM" else "PM" }
        )

        val result = await(request("/pre-clearance/export-questions/vessel-info").post(payload))

        result.status shouldBe 501

        journey.getState shouldBe AnswerExportQuestionsContactInfo(
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
      }

      "submit none vessel details and ask next for contact details" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerExportQuestionsOptionalVesselInfo(
            DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route6),
              priorityGoods = Some(ExportPriorityGoods.HighValueArt),
              freightType = Some(ExportFreightType.Air)
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map[String, String]()

        val result = await(request("/pre-clearance/export-questions/vessel-info").post(payload))

        result.status shouldBe 501

        journey.getState shouldBe AnswerExportQuestionsContactInfo(
          DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
          ExportQuestions(
            requestType = Some(ExportRequestType.New),
            routeType = Some(ExportRouteType.Route6),
            priorityGoods = Some(ExportPriorityGoods.HighValueArt),
            freightType = Some(ExportFreightType.Air),
            vesselDetails = None
          )
        )
      }
    }

    "GET /pre-clearance/import-questions/route-type" should {
      "show the import route type question page" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerImportQuestionsRouteType(
            DeclarationDetails(EPU(532), EntryNumber("111111X"), LocalDate.parse("2020-10-08")),
            ImportQuestions(requestType = Some(ImportRequestType.New))
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/import-questions/route-type").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.import-questions.routeType.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.routeType.heading"))
        journey.getState shouldBe AnswerImportQuestionsRouteType(
          DeclarationDetails(EPU(532), EntryNumber("111111X"), LocalDate.parse("2020-10-08")),
          ImportQuestions(requestType = Some(ImportRequestType.New))
        )
      }
    }

    "POST /pre-clearance/import-questions/route-type" should {
      "submit the form and ask next for hasPriorityGoods" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerImportQuestionsRouteType(
            DeclarationDetails(EPU(444), EntryNumber("011111X"), LocalDate.parse("2020-10-01")),
            ImportQuestions(requestType = Some(ImportRequestType.Cancellation))
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("routeType" -> "Route6")

        val result = await(request("/pre-clearance/import-questions/route-type").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.import-questions.hasPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.hasPriorityGoods.heading"))
        journey.getState shouldBe AnswerImportQuestionsHasPriorityGoods(
          DeclarationDetails(EPU(444), EntryNumber("011111X"), LocalDate.parse("2020-10-01")),
          ImportQuestions(requestType = Some(ImportRequestType.Cancellation), routeType = Some(ImportRouteType.Route6))
        )
      }
    }

    "GET /pre-clearance/import-questions/has-priority-goods" should {
      "show the import has priority goods page" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerImportQuestionsHasPriorityGoods(
            DeclarationDetails(EPU(110), EntryNumber("911111X"), LocalDate.parse("2020-09-23")),
            ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route6))
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/import-questions/has-priority-goods").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.import-questions.hasPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.hasPriorityGoods.heading"))
        journey.getState shouldBe AnswerImportQuestionsHasPriorityGoods(
          DeclarationDetails(EPU(110), EntryNumber("911111X"), LocalDate.parse("2020-09-23")),
          ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route6))
        )
      }
    }

    "POST /pre-clearance/import-questions/has-priority-goods" should {
      "submit YES choice and ask next for which priority goods" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerImportQuestionsHasPriorityGoods(
            DeclarationDetails(EPU(101), EntryNumber("811111X"), LocalDate.parse("2020-09-23")),
            ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route2))
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("hasPriorityGoods" -> "yes")

        val result = await(request("/pre-clearance/import-questions/has-priority-goods").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.import-questions.whichPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.whichPriorityGoods.heading"))
        journey.getState shouldBe AnswerImportQuestionsWhichPriorityGoods(
          DeclarationDetails(EPU(101), EntryNumber("811111X"), LocalDate.parse("2020-09-23")),
          ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route2))
        )
      }

      "submit NO choice and ask next for transport type" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerImportQuestionsHasPriorityGoods(
            DeclarationDetails(EPU(100), EntryNumber("711111X"), LocalDate.parse("2020-09-23")),
            ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route2))
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("hasPriorityGoods" -> "no")

        val result = await(request("/pre-clearance/import-questions/has-priority-goods").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.import-questions.hasALVS.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.hasALVS.heading"))
        journey.getState shouldBe AnswerImportQuestionsALVS(
          DeclarationDetails(EPU(100), EntryNumber("711111X"), LocalDate.parse("2020-09-23")),
          ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route2))
        )
      }
    }

    "GET /pre-clearance/import-questions/which-priority-goods" should {
      "show the import which priority goods page" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerImportQuestionsWhichPriorityGoods(
            DeclarationDetails(EPU(230), EntryNumber("111111Z"), LocalDate.parse("2020-10-05")),
            ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route6))
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/import-questions/which-priority-goods").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.import-questions.whichPriorityGoods.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.whichPriorityGoods.heading"))
        journey.getState shouldBe AnswerImportQuestionsWhichPriorityGoods(
          DeclarationDetails(EPU(230), EntryNumber("111111Z"), LocalDate.parse("2020-10-05")),
          ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route6))
        )
      }
    }

    "POST /pre-clearance/import-questions/which-priority-goods" should {
      "submit selected priority goods and ask next for automatic licence verification" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerImportQuestionsWhichPriorityGoods(
            DeclarationDetails(EPU(236), EntryNumber("011111X"), LocalDate.parse("2020-09-21")),
            ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route3))
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("priorityGoods" -> "LiveAnimals")

        val result = await(request("/pre-clearance/import-questions/which-priority-goods").post(payload))

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.import-questions.hasALVS.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.hasALVS.heading"))
        journey.getState shouldBe AnswerImportQuestionsALVS(
          DeclarationDetails(EPU(236), EntryNumber("011111X"), LocalDate.parse("2020-09-21")),
          ImportQuestions(
            requestType = Some(ImportRequestType.New),
            routeType = Some(ImportRouteType.Route3),
            priorityGoods = Some(ImportPriorityGoods.LiveAnimals)
          )
        )
      }
    }

    "GET /pre-clearance/import-questions/automatic-licence-verification" should {
      "show the import has ALVS page" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerImportQuestionsALVS(
            DeclarationDetails(EPU(235), EntryNumber("711111X"), LocalDate.parse("2020-09-23")),
            ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route2))
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/import-questions/automatic-licence-verification").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.import-questions.hasALVS.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.hasALVS.heading"))
        journey.getState shouldBe AnswerImportQuestionsALVS(
          DeclarationDetails(EPU(235), EntryNumber("711111X"), LocalDate.parse("2020-09-23")),
          ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route2))
        )
      }
    }

    "POST /pre-clearance/import-questions/automatic-licence-verification" should {
      for (hasALVS <- Seq(true, false))
        s"submit ${if (hasALVS) "YES" else "NO"} choice and ask next for transport type" in {
          implicit val journeyId: JourneyId = JourneyId()
          journey.setState(
            AnswerImportQuestionsALVS(
              DeclarationDetails(EPU(235), EntryNumber("011111X"), LocalDate.parse("2020-09-23")),
              ImportQuestions(requestType = Some(ImportRequestType.New), routeType = Some(ImportRouteType.Route2))
            )
          )
          givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

          val payload = Map("hasALVS" -> { if (hasALVS) "yes" else "no" })

          val result = await(request("/pre-clearance/import-questions/automatic-licence-verification").post(payload))

          result.status shouldBe 200
          result.body should include(htmlEscapedMessage("view.import-questions.freightType.title"))
          result.body should include(htmlEscapedMessage("view.import-questions.freightType.heading"))
          journey.getState shouldBe AnswerImportQuestionsFreightType(
            DeclarationDetails(EPU(235), EntryNumber("011111X"), LocalDate.parse("2020-09-23")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route2),
              hasALVS = Some(hasALVS)
            )
          )
        }
    }

    "GET /pre-clearance/import-questions/transport-type" should {
      "show the import transport type page" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerImportQuestionsFreightType(
            DeclarationDetails(EPU(230), EntryNumber("311111Z"), LocalDate.parse("2020-10-05")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route6)
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/import-questions/transport-type").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.import-questions.freightType.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.freightType.heading"))
        journey.getState shouldBe AnswerImportQuestionsFreightType(
          DeclarationDetails(EPU(230), EntryNumber("311111Z"), LocalDate.parse("2020-10-05")),
          ImportQuestions(
            requestType = Some(ImportRequestType.New),
            routeType = Some(ImportRouteType.Route6)
          )
        )
      }
    }

    "POST /pre-clearance/import-questions/transport-type" should {
      "submit selected RORO transport type and ask next for optional vessel details" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerImportQuestionsFreightType(
            DeclarationDetails(EPU(236), EntryNumber("211111X"), LocalDate.parse("2020-09-21")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route3)
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("freightType" -> "RORO")

        val result = await(request("/pre-clearance/import-questions/transport-type").post(payload))

        result.status shouldBe 200

        journey.getState shouldBe AnswerImportQuestionsOptionalVesselInfo(
          DeclarationDetails(EPU(236), EntryNumber("211111X"), LocalDate.parse("2020-09-21")),
          ImportQuestions(
            requestType = Some(ImportRequestType.New),
            routeType = Some(ImportRouteType.Route3),
            freightType = Some(ImportFreightType.RORO)
          )
        )
      }

      "submit selected Maritime transport type and ask next for vessel details" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerImportQuestionsFreightType(
            DeclarationDetails(EPU(100), EntryNumber("011111X"), LocalDate.parse("2020-09-21")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route3)
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map("freightType" -> "Maritime")

        val result = await(request("/pre-clearance/import-questions/transport-type").post(payload))

        result.status shouldBe 200

        journey.getState shouldBe AnswerImportQuestionsOptionalVesselInfo(
          DeclarationDetails(EPU(100), EntryNumber("011111X"), LocalDate.parse("2020-09-21")),
          ImportQuestions(
            requestType = Some(ImportRequestType.New),
            routeType = Some(ImportRouteType.Route3),
            freightType = Some(ImportFreightType.Maritime)
          )
        )
      }
    }

    "GET /trader-services/foo" should {
      "return an error page not found" in {
        implicit val journeyId: JourneyId = JourneyId()
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/foo").get())

        result.status shouldBe 404
        result.body should include("This page can’t be found")
        journey.get shouldBe None
      }
    }

    "GET /pre-clearance/import-questions/request-type" should {
      "show the import request type question page" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          AnswerImportQuestionsRequestType(
            DeclarationDetails(EPU(235), EntryNumber("111111X"), LocalDate.parse("2020-09-23")),
            ImportQuestions()
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/pre-clearance/import-questions/request-type").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.import-questions.requestType.title"))
        result.body should include(htmlEscapedMessage("view.import-questions.requestType.heading"))
        journey.getState shouldBe AnswerImportQuestionsRequestType(
          DeclarationDetails(EPU(235), EntryNumber("111111X"), LocalDate.parse("2020-09-23")),
          ImportQuestions()
        )
      }
    }
  }

  "GET /pre-clearance/import-questions/vessel-info" should {
    "show the import vessel details page" in {
      implicit val journeyId: JourneyId = JourneyId()
      journey.setState(
        AnswerImportQuestionsOptionalVesselInfo(
          DeclarationDetails(EPU(230), EntryNumber("111111Z"), LocalDate.parse("2020-10-05")),
          ImportQuestions(
            requestType = Some(ImportRequestType.New),
            routeType = Some(ImportRouteType.Route6),
            priorityGoods = Some(ImportPriorityGoods.HighValueArt),
            freightType = Some(ImportFreightType.Air)
          )
        )
      )
      givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

      val result = await(request("/pre-clearance/import-questions/vessel-info").get())

      result.status shouldBe 200
      result.body should include(htmlEscapedMessage("view.import-questions.vessel-details.title"))
      result.body should include(htmlEscapedMessage("view.import-questions.vessel-details.heading"))
      journey.getState shouldBe AnswerImportQuestionsOptionalVesselInfo(
        DeclarationDetails(EPU(230), EntryNumber("111111Z"), LocalDate.parse("2020-10-05")),
        ImportQuestions(
          requestType = Some(ImportRequestType.New),
          routeType = Some(ImportRouteType.Route6),
          priorityGoods = Some(ImportPriorityGoods.HighValueArt),
          freightType = Some(ImportFreightType.Air)
        )
      )
    }
  }

  "POST /pre-clearance/import-questions/vessel-info" should {
    "submit optional vessel details and ask next for contact details" in {
      implicit val journeyId: JourneyId = JourneyId()
      journey.setState(
        AnswerImportQuestionsOptionalVesselInfo(
          DeclarationDetails(EPU(230), EntryNumber("111111Z"), LocalDate.parse("2020-10-05")),
          ImportQuestions(
            requestType = Some(ImportRequestType.New),
            routeType = Some(ImportRouteType.Route6),
            priorityGoods = Some(ImportPriorityGoods.HighValueArt),
            freightType = Some(ImportFreightType.Air)
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
        "timeOfArrival.hour"    -> f"${dateTimeOfArrival.get(ChronoField.CLOCK_HOUR_OF_AMPM)}%02d",
        "timeOfArrival.minutes" -> f"${dateTimeOfArrival.get(ChronoField.MINUTE_OF_HOUR)}%02d",
        "timeOfArrival.period"  -> { if (dateTimeOfArrival.get(ChronoField.AMPM_OF_DAY) == 0) "AM" else "PM" }
      )

      val result = await(request("/pre-clearance/import-questions/vessel-info").post(payload))

      result.status shouldBe 501

      journey.getState shouldBe AnswerImportQuestionsContactInfo(
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
    }

    "submit none vessel details and ask next for contact details" in {
      implicit val journeyId: JourneyId = JourneyId()
      journey.setState(
        AnswerImportQuestionsOptionalVesselInfo(
          DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
          ImportQuestions(
            requestType = Some(ImportRequestType.New),
            routeType = Some(ImportRouteType.Route6),
            priorityGoods = Some(ImportPriorityGoods.HighValueArt),
            freightType = Some(ImportFreightType.Air)
          )
        )
      )
      givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

      val payload = Map[String, String]()

      val result = await(request("/pre-clearance/import-questions/vessel-info").post(payload))

      result.status shouldBe 501

      journey.getState shouldBe AnswerImportQuestionsContactInfo(
        DeclarationDetails(EPU(230), EntryNumber("A11111Z"), LocalDate.parse("2020-10-05")),
        ImportQuestions(
          requestType = Some(ImportRequestType.New),
          routeType = Some(ImportRouteType.Route6),
          priorityGoods = Some(ImportPriorityGoods.HighValueArt),
          freightType = Some(ImportFreightType.Air),
          vesselDetails = None
        )
      )
    }
  }

}

trait TraderServicesFrontendISpecSetup extends ServerISpec {

  override def fakeApplication: Application = appBuilder.build()

  lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]
  lazy val sessionCookieBaker: SessionCookieBaker = app.injector.instanceOf[SessionCookieBaker]
  lazy val sessionCookieCrypto: SessionCookieCrypto = app.injector.instanceOf[SessionCookieCrypto]

  case class JourneyId(value: String = UUID.randomUUID().toString)

  // define test service capable of manipulating journey state
  lazy val journey = new TestJourneyService[JourneyId]
    with TraderServicesFrontendJourneyService[JourneyId] with MongoDBCachedJourneyService[JourneyId] {

    override lazy val cacheMongoRepository = app.injector.instanceOf[CacheMongoRepository]
    override lazy val applicationCrypto = app.injector.instanceOf[ApplicationCrypto]

    override val stateFormats: Format[model.State] =
      TraderServicesFrontendJourneyStateFormats.formats

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

}
