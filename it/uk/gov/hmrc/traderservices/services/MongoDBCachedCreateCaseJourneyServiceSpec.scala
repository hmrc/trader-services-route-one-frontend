//package uk.gov.hmrc.traderservices.services
//
//import uk.gov.hmrc.traderservices.support.AppISpec
//import uk.gov.hmrc.http.HeaderCarrier
//import scala.concurrent.ExecutionContext.Implicits.global
//import java.util.UUID
//
//class MongoDBCachedCreateCaseJourneyServiceSpec extends AppISpec {
//
//  lazy val service: MongoDBCachedCreateCaseJourneyService =
//    app.injector.instanceOf[MongoDBCachedCreateCaseJourneyService]
//
//  import service.model.{CreateCaseJourneyState, Transitions}
//
//  implicit val hc: HeaderCarrier =
//    HeaderCarrier()
//      .withExtraHeaders("CreateCaseJourney" -> UUID.randomUUID.toString)
//
//  "MongoDBCachedCreateCaseJourneyService" should {
//    "apply start transition" in {
//      await(service.apply(Transitions.start)) shouldBe ((CreateCaseJourneyState.Start, Nil))
//    }
//
//    "keep breadcrumbs when no change in state" in {
//      service.updateBreadcrumbs(CreateCaseJourneyState.Start, CreateCaseJourneyState.Start, Nil) shouldBe Nil
//    }
//
//    "update breadcrumbs when new state" in {
//      service.updateBreadcrumbs(
//        CreateCaseJourneyState.EnterEntryDetails(),
//        CreateCaseJourneyState.Start,
//        Nil
//      ) shouldBe List(
//        CreateCaseJourneyState.Start
//      )
//
//      service.updateBreadcrumbs(
//        CreateCaseJourneyState.EnterEntryDetails(),
//        CreateCaseJourneyState.ChooseNewOrExistingCase(),
//        List(CreateCaseJourneyState.Start)
//      ) shouldBe List(CreateCaseJourneyState.ChooseNewOrExistingCase(), CreateCaseJourneyState.Start)
//    }
//
//    "trim breadcrumbs when returning back to the previous state" in {
//      service.updateBreadcrumbs(
//        CreateCaseJourneyState.ChooseNewOrExistingCase(),
//        CreateCaseJourneyState.EnterEntryDetails(),
//        List(CreateCaseJourneyState.ChooseNewOrExistingCase(), CreateCaseJourneyState.Start)
//      ) shouldBe List(CreateCaseJourneyState.Start)
//    }
//  }
//
//}
