package test.uk.gov.hmrc.traderservices.services

import test.uk.gov.hmrc.traderservices.support.AppISpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.traderservices.services.MongoDBCachedCreateCaseJourneyService

import java.util.UUID

class MongoDBCachedCreateCaseJourneyServiceSpec extends AppISpec {

  lazy val service: MongoDBCachedCreateCaseJourneyService =
    app.injector.instanceOf[MongoDBCachedCreateCaseJourneyService]

  import service.model.CreateCaseJourneyState

  implicit val hc: HeaderCarrier =
    HeaderCarrier()
      .withExtraHeaders("CreateCaseJourney" -> UUID.randomUUID.toString)

  "MongoDBCachedCreateCaseJourneyService" should {

    "keep breadcrumbs when no change in state" in {
      service.updateBreadcrumbs(CreateCaseJourneyState.Start, CreateCaseJourneyState.Start, Nil) shouldBe Nil
    }

    "update breadcrumbs when new state" in {
      service.updateBreadcrumbs(
        CreateCaseJourneyState.EnterEntryDetails(),
        CreateCaseJourneyState.Start,
        Nil
      ) shouldBe List(
        CreateCaseJourneyState.Start
      )

      service.updateBreadcrumbs(
        CreateCaseJourneyState.EnterEntryDetails(),
        CreateCaseJourneyState.ChooseNewOrExistingCase(),
        List(CreateCaseJourneyState.Start)
      ) shouldBe List(CreateCaseJourneyState.ChooseNewOrExistingCase(), CreateCaseJourneyState.Start)
    }

    "trim breadcrumbs when returning back to the previous state" in {
      service.updateBreadcrumbs(
        CreateCaseJourneyState.ChooseNewOrExistingCase(),
        CreateCaseJourneyState.EnterEntryDetails(),
        List(CreateCaseJourneyState.ChooseNewOrExistingCase(), CreateCaseJourneyState.Start)
      ) shouldBe List(CreateCaseJourneyState.Start)
    }
  }

}
