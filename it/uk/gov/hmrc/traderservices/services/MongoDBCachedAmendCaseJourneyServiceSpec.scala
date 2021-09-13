package uk.gov.hmrc.traderservices.services

import uk.gov.hmrc.traderservices.support.AppISpec
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.UUID

class MongoDBCachedAmendCaseJourneyServiceSpec extends AppISpec {

  lazy val service: MongoDBCachedAmendCaseJourneyService =
    app.injector.instanceOf[MongoDBCachedAmendCaseJourneyService]

  import service.model.{State, Transitions}

  implicit val hc: HeaderCarrier =
    HeaderCarrier()
      .withExtraHeaders("AmendCaseJourney" -> UUID.randomUUID.toString)

  "MongoDBCachedAmendCaseJourneyService" should {
    "apply start transition" in {
      await(service.apply(Transitions.start)) shouldBe ((State.Start, Nil))
    }
  }

}
