package uk.gov.hmrc.traderservices.services

import uk.gov.hmrc.traderservices.support.AppISpec
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.UUID

class MongoDBCachedCreateCaseJourneyServiceSpec extends AppISpec {

  lazy val service: MongoDBCachedCreateCaseJourneyService =
    app.injector.instanceOf[MongoDBCachedCreateCaseJourneyService]

  import service.model.{State, Transitions}

  implicit val hc: HeaderCarrier =
    HeaderCarrier()
      .withExtraHeaders("CreateCaseJourney" -> UUID.randomUUID.toString)

  "MongoDBCachedCreateCaseJourneyService" should {
    "apply start transition" in {
      await(service.apply(Transitions.start)) shouldBe ((State.Start, Nil))
    }

    "keep breadcrumbs when no change in state" in {
      service.updateBreadcrumbs(State.Start, State.Start, Nil) shouldBe Nil
    }

    "update breadcrumbs when new state" in {
      service.updateBreadcrumbs(State.EnterEntryDetails(), State.Start, Nil) shouldBe List(
        State.Start
      )

      service.updateBreadcrumbs(
        State.EnterEntryDetails(),
        State.ChooseNewOrExistingCase(),
        List(State.Start)
      ) shouldBe List(State.ChooseNewOrExistingCase(), State.Start)
    }

    "trim breadcrumbs when returning back to the previous state" in {
      service.updateBreadcrumbs(
        State.ChooseNewOrExistingCase(),
        State.EnterEntryDetails(),
        List(State.ChooseNewOrExistingCase(), State.Start)
      ) shouldBe List(State.Start)
    }
  }

}
