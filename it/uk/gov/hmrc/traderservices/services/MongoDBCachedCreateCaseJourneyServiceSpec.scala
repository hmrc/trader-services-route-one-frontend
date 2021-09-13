package uk.gov.hmrc.traderservices.services

import uk.gov.hmrc.traderservices.support.AppISpec
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.UUID

class MongoDBCachedPizzaTaxJourneyServiceSpec extends AppISpec {

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
  }

}
