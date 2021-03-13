package uk.gov.hmrc.traderservices.services

import play.api.libs.json.Format
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.repository.CacheRepository
import akka.actor.ActorSystem
import uk.gov.hmrc.traderservices.support.AppISpec
import uk.gov.hmrc.play.fsm.JourneyModel
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.play.fsm.PersistentJourneyService
import scala.concurrent.Future

class MongoDBCachedJourneyServiceISpec extends MongoDBCachedJourneyServiceISpecSetup {

  implicit val context: String = UUID.randomUUID().toString()

  "MongoDBCachedJourneyService" should {

    "store and fetch" in {
      await(service.setState(1))
      await(service.getState) shouldBe Some(1)
      await(service.setState(2))
      await(service.setState(3))
      await(service.getState) shouldBe Some(3)
      await(service.getState) shouldBe Some(3)
      await(service.getState) shouldBe Some(3)
    }

    "modify state sequantially" in {
      await(service.setState(0))
      await(
        Future.sequence(
          Seq(
            service.apply(_ + 1),
            service.apply(_ + 2),
            service.apply(_ + 3),
            service.apply(_ + 4),
            service.apply(_ + 5),
            service.apply(_ * 10),
            service.apply(_ * 0)
          )
        )
      ) shouldBe Seq(1, 3, 6, 10, 15, 150, 0)
    }

    "propagate TransitionNotAllowed exception" in {
      a[service.model.TransitionNotAllowed] shouldBe thrownBy {
        await(service.apply(service.model.notAllowed))
      }
    }
  }

}

trait MongoDBCachedJourneyServiceISpecSetup extends AppISpec {

  // define test service capable of manipulating journey state
  lazy val service = new PersistentJourneyService[String] with MongoDBCachedJourneyService[String] {

    override val journeyKey: String = "TestJourney"
    override val model: TestJourneyModel = new TestJourneyModel

    override lazy val actorSystem: ActorSystem = app.injector.instanceOf[ActorSystem]
    override lazy val cacheRepository = app.injector.instanceOf[CacheRepository]
    override lazy val applicationCrypto = app.injector.instanceOf[ApplicationCrypto]

    override val stateFormats: Format[model.State] =
      SimpleDecimalFormat(_.toInt, BigDecimal.apply(_))

    override def getJourneyId(journeyId: String): Option[String] = Some(journeyId)

    def apply(f: Int => Int)(implicit rc: String): Future[Int] = super.apply(model.modify(f)).map(_._1)
    def get(implicit rc: String): Future[Option[StateAndBreadcrumbs]] = currentState
    def set(stateAndBreadcrumbs: StateAndBreadcrumbs)(implicit rc: String): Future[StateAndBreadcrumbs] =
      super.save(stateAndBreadcrumbs)
    def getState(implicit rc: String): Future[Option[Int]] = currentState.map(_.map(_._1))
    def setState(state: model.State)(implicit rc: String): Future[Int] = super.save((state, Nil)).map(_._1)

  }

  final class TestJourneyModel extends JourneyModel {

    type State = Int
    override val root: Int = 0

    def modify(f: Int => Int) =
      Transition {
        case i => goto(f(i))
      }

    val notAllowed =
      Transition {
        case i if false => goto(i)
      }
  }

}
