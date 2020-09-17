package uk.gov.hmrc.traderservices.controllers

import play.api.Application
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.traderservices.journeys.TraderServicesFrontendJourneyModel.State.{Start}
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.services.TraderServicesFrontendJourneyServiceWithHeaderCarrier
import uk.gov.hmrc.traderservices.stubs.{JourneyTestData, TraderServicesStubs}
import uk.gov.hmrc.traderservices.support.{AppISpec, InMemoryJourneyService, TestJourneyService}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class TraderServicesFrontendControllerISpec
    extends TraderServicesFrontendControllerISpecSetup with TraderServicesStubs with JourneyStateHelpers {

  import journey.model.State._

  "TraderServicesFrontendController" when {

    "GET /" should {

      "redirect to the start page" in {
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber","foo"))
        val result = controller.showStart(fakeRequest)
        status(result) shouldBe 200
        journey.get shouldBe Some((Start, Nil))
      }
    }
  }

}

trait JourneyStateHelpers extends JourneyTestData {

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
          .to(classOf[TestInMemoryTraderServicesFrontendJourneyService]))
      .build()

  lazy val controller: TraderServicesFrontendController =
    app.injector.instanceOf[TraderServicesFrontendController]

  lazy val journey: TestInMemoryTraderServicesFrontendJourneyService =
    controller.journeyService
      .asInstanceOf[TestInMemoryTraderServicesFrontendJourneyService]

  def fakeRequest = FakeRequest().withSession(controller.journeyService.journeyKey -> "fooId")

}
