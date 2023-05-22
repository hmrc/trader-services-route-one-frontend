package uk.gov.hmrc.traderservices.services

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.traderservices.models.AmendCaseModel
import uk.gov.hmrc.traderservices.support.AppISpec

import java.util.UUID

class MongoDBCachedAmendCaseJourneyServiceSpec extends AppISpec {

  lazy val service: MongoDBCachedAmendCaseJourneyService =
    app.injector.instanceOf[MongoDBCachedAmendCaseJourneyService]

  import service.model.State

  implicit val hc: HeaderCarrier =
    HeaderCarrier()
      .withExtraHeaders("AmendCaseJourney" -> UUID.randomUUID.toString)

  "MongoDBCachedAmendCaseJourneyService" should {

    "keep breadcrumbs when no change in state" in {
      service.updateBreadcrumbs(State.Start, State.Start, Nil) shouldBe Nil
    }

    "update breadcrumbs when new state" in {
      service.updateBreadcrumbs(State.EnterCaseReferenceNumber(), State.Start, Nil) shouldBe List(
        State.Start
      )

      service.updateBreadcrumbs(
        State.SelectTypeOfAmendment(AmendCaseModel()),
        State.EnterCaseReferenceNumber(),
        List(State.Start)
      ) shouldBe List(State.EnterCaseReferenceNumber(), State.Start)
    }

    "trim breadcrumbs when returning back to the previous state" in {
      service.updateBreadcrumbs(
        State.EnterCaseReferenceNumber(),
        State.SelectTypeOfAmendment(AmendCaseModel()),
        List(State.EnterCaseReferenceNumber(), State.Start)
      ) shouldBe List(State.Start)
    }
  }

}
