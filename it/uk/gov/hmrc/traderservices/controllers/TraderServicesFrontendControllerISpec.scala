package uk.gov.hmrc.traderservices.controllers

import java.time.LocalDate

import play.api.Application
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.services.TraderServicesFrontendJourneyServiceWithHeaderCarrier
import uk.gov.hmrc.traderservices.stubs.{TraderServicesStubs}
import uk.gov.hmrc.traderservices.support.{AppISpec, InMemoryJourneyService, TestJourneyService}

import scala.concurrent.ExecutionContext.Implicits.global

class TraderServicesFrontendControllerISpec
    extends TraderServicesFrontendControllerISpecSetup with TraderServicesStubs with JourneyStateHelpers {

  import journey.model.State._

  "TraderServicesFrontendController" when {

    "GET /" should {
      "show the start page" in {
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val result = controller.showStart(fakeRequest)
        status(result) shouldBe 200
        journey.get shouldBe Some((Start, Nil))
      }
    }

    "GET /pre-clearance" should {
      "show the enter declaration page" in {
        journey.set(Start, Nil)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val result = controller.showEnterDeclarationDetails(fakeRequest)
        status(result) shouldBe 200
        journey.get shouldBe Some((EnterDeclarationDetails(None), List(Start)))
      }
    }

    "POST /pre-clearance/declaration-details" should {

      "submit the declaration details and redirect to the export-questions if request details pass validation and entry number is for export" in {
        journey.set(EnterDeclarationDetails(None), List(Start))
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val request = fakeRequest
          .withFormUrlEncodedBody(
            "entryDate.year"  -> "2020",
            "entryDate.month" -> "09",
            "entryDate.day"   -> "23",
            "epu"             -> "235",
            "entryNumber"     -> "A11111X"
          )
        val result = controller.submitDeclarationDetails(request)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some("/trader-services/pre-clearance/export-questions/request-type")
        journey.get shouldBe Some(
          (
            AnswerExportQuestionsRequestType(
              DeclarationDetails(EPU(235), EntryNumber("A11111X"), LocalDate.parse("2020-09-23")),
              ExportQuestions()
            ),
            List(EnterDeclarationDetails(None), Start)
          )
        )
      }

      "submit the declaration details and redirect to the import-questions if request details pass validation and entry number is for import" in {
        journey.set(EnterDeclarationDetails(None), List(Start))
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val request = fakeRequest
          .withFormUrlEncodedBody(
            "entryDate.year"  -> "2020",
            "entryDate.month" -> "09",
            "entryDate.day"   -> "23",
            "epu"             -> "235",
            "entryNumber"     -> "111111X"
          )
        val result = controller.submitDeclarationDetails(request)
        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some("/trader-services/pre-clearance/import-questions/request-type")
        journey.get shouldBe Some(
          (
            AnswerImportQuestionsRequestType(
              DeclarationDetails(EPU(235), EntryNumber("111111X"), LocalDate.parse("2020-09-23")),
              ImportQuestions()
            ),
            List(EnterDeclarationDetails(None), Start)
          )
        )
      }
    }
  }

}

trait JourneyStateHelpers {

  def journey: TestInMemoryTraderServicesFrontendJourneyService

}

class TestInMemoryTraderServicesFrontendJourneyService
    extends TraderServicesFrontendJourneyServiceWithHeaderCarrier with InMemoryJourneyService[HeaderCarrier]
    with TestJourneyService[HeaderCarrier]

trait TraderServicesFrontendControllerISpecSetup extends AppISpec {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def fakeApplication: Application =
    appBuilder
      .overrides(
        bind(classOf[TraderServicesFrontendJourneyServiceWithHeaderCarrier])
          .to(classOf[TestInMemoryTraderServicesFrontendJourneyService])
      )
      .build()

  lazy val controller: TraderServicesFrontendController =
    app.injector.instanceOf[TraderServicesFrontendController]

  lazy val journey: TestInMemoryTraderServicesFrontendJourneyService =
    controller.journeyService
      .asInstanceOf[TestInMemoryTraderServicesFrontendJourneyService]

  def fakeRequest = FakeRequest().withSession(controller.journeyService.journeyKey -> "fooId")

}
