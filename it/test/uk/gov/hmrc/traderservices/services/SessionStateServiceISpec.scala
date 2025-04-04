/*
 * Copyright 2025 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.traderservices.services

import org.apache.pekko.actor.ActorSystem
import com.typesafe.config.Config
import org.mongodb.scala.{MongoClient, MongoDatabase}
import play.api.libs.json._
import uk.gov.hmrc.traderservices.support.AppISpec
import uk.gov.hmrc.mongo.{CurrentTimestampSupport, MongoComponent}
import uk.gov.hmrc.mongo.cache.{CacheItem, DataKey}
import uk.gov.hmrc.traderservices.journeys.Transition
import uk.gov.hmrc.traderservices.repository.CacheRepository
import uk.gov.hmrc.traderservices.services.{EncryptedSessionCache, KeyProvider}

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Duration, DurationLong, FiniteDuration}

class SessionStateServiceISpec extends SessionStateServiceISpecSetup {

  override lazy val cacheRepository: CacheRepository =
    app.injector.instanceOf[CacheRepository]

  implicit val context: String = UUID.randomUUID().toString()

  class A

  "MongoDBCachedJourneyService" should {

    "store and fetch a state" in {
      await(service.setState(1))
      await(service.getState) shouldBe Some(1)
      await(service.setState(2))
      await(service.setState(3))
      await(service.getState) shouldBe Some(3)
      await(service.getState) shouldBe Some(3)
      await(service.getState) shouldBe Some(3)
    }

    "modify a state using a root state as a default" in {
      await(service.clear)
      await(service.apply(_ + 1)) shouldBe 1
    }

    "modify current state sequentially" in {
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

    "set, get, modify, clear state sequentially" in {
      await(service.setState(0))
      await(
        Future.sequence(
          Seq(
            service.getState.map(_.get),
            service.setState(1),
            service.getState.map(_.get),
            service.setState(2),
            service.getState.map(_.get),
            service.setState(3),
            service.getState.map(_.get),
            service.setState(4),
            service.getState.map(_.get),
            service.setState(5),
            service.getState.map(_.get),
            service.setState(6),
            service.apply(_ * 2),
            service.getState.map(_.get),
            service.setState(7),
            service.clear
          )
        )
      ) shouldBe Seq(0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 12, 12, 7, ())
    }

    "clear cache" in {
      await(service.setState(123))
      await(service.get) shouldBe Some((123, Nil))
      service.apply(_ + 1)
      await(service.get) shouldBe Some((124, List(123)))
      await(service.clear)
      await(service.get) shouldBe None
      await(service.setState(321))
      await(service.get) shouldBe Some((321, Nil))
    }

    "throw exception when journeyId is not available" in {
      a[RuntimeException] shouldBe thrownBy {
        await(service.setState(111)(null))
      }
      a[RuntimeException] shouldBe thrownBy {
        await(service.apply(_ + 1)(null))
      }
    }

    "do not throw an exception when getting state with missing journeyId" in {
      await(service.getState(null)) shouldBe None
    }

    "do not throw an exception when clearing with missing journeyId" in {
      await(service.clear(null, implicitly[ExecutionContext])) shouldBe (())
    }
  }

}

/** Tests scenario when cache repository findById returns nothing. */
class IgnoringSessionStateServiceISpec extends SessionStateServiceISpecSetup {

  override lazy val cacheRepository: CacheRepository =
    new CacheRepository(
      dummyMongoComponent,
      "foo",
      Duration("1 minute"),
      new CurrentTimestampSupport
    ) {
      override def ensureIndexes: Future[Seq[String]] = Future.successful(Seq.empty)
      override def ensureSchema: Future[Unit] = Future.successful(())
      override def findById(cacheId: String): Future[Option[CacheItem]] = Future.successful(None)
      override def get[A: Reads](cacheId: String)(dataKey: DataKey[A]): Future[Option[A]] = Future.successful(None)
      override def put[A: Writes](cacheId: String)(dataKey: DataKey[A], data: A): Future[CacheItem] =
        Future.successful(CacheItem("foo", Json.obj(), Instant.now(), Instant.now()))
      override def deleteEntity(cacheId: String): Future[Unit] = Future.successful(())
      override def delete[A](cacheId: String)(dataKey: DataKey[A]): Future[Unit] = Future.successful(())
    }

  implicit val context: String = UUID.randomUUID().toString()

  "ignoring MongoDBCachedJourneyService" should {

    "store and fetch a state" in {
      await(service.setState(1))
      await(service.getState) shouldBe None
      await(service.setState(2))
      await(service.getState) shouldBe None
    }

    "modify a state using a root state as a default" in {
      await(service.apply(_ + 10)) shouldBe 10
    }

    "modify current state sequentially" in {
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
      ) shouldBe Seq(1, 2, 3, 4, 5, 0, 0)
    }

    "throw an exception at an attempt to set, get, modify, clear state sequentially" in {
      a[Exception] shouldBe thrownBy {
        await(
          Future.sequence(
            Seq(
              service.getState.map(_.get),
              service.setState(1),
              service.getState.map(_.get),
              service.setState(2),
              service.getState.map(_.get),
              service.setState(3),
              service.getState.map(_.get),
              service.setState(4),
              service.getState.map(_.get),
              service.setState(5),
              service.getState.map(_.get),
              service.setState(6),
              service.apply(_ * 2),
              service.getState.map(_.get),
              service.setState(7),
              service.clear
            )
          )
        )
      }
    }
  }

}

/** Tests scenario when cache repository findById returns an object missing journeyId key. */
class ForgetfulSessionStateServiceISpec extends SessionStateServiceISpecSetup {

  override lazy val cacheRepository: CacheRepository =
    new CacheRepository(
      dummyMongoComponent,
      "foo",
      Duration("1 minute"),
      new CurrentTimestampSupport
    ) {
      override def ensureIndexes: Future[Seq[String]] = Future.successful(Seq.empty)
      override def ensureSchema: Future[Unit] = Future.successful(())
      override def findById(cacheId: String): Future[Option[CacheItem]] =
        Future.successful(Some(CacheItem("foo", Json.obj(), Instant.now(), Instant.now())))
      override def get[A: Reads](cacheId: String)(dataKey: DataKey[A]): Future[Option[A]] = Future.successful(None)
      override def put[A: Writes](cacheId: String)(dataKey: DataKey[A], data: A): Future[CacheItem] =
        Future.successful(CacheItem("foo", Json.obj(), Instant.now(), Instant.now()))
      override def deleteEntity(cacheId: String): Future[Unit] = Future.successful(())
      override def delete[A](cacheId: String)(dataKey: DataKey[A]): Future[Unit] = Future.successful(())
    }

  implicit val context: String = UUID.randomUUID().toString()

  "forgetfull MongoDBCachedJourneyService" should {

    "store and fetch a state" in {
      await(service.setState(1))
      await(service.getState) shouldBe None
      await(service.setState(2))
      await(service.getState) shouldBe None
    }

    "modify a state using a root state as a default" in {
      await(service.apply(_ + 10)) shouldBe 10
    }

    "modify current state sequentially" in {
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
      ) shouldBe Seq(1, 2, 3, 4, 5, 0, 0)
    }

    "throw an exception at an attempt to set, get, modify, clear state sequentially" in {
      a[Exception] shouldBe thrownBy {
        await(
          Future.sequence(
            Seq(
              service.getState.map(_.get),
              service.setState(1),
              service.getState.map(_.get),
              service.setState(2),
              service.getState.map(_.get),
              service.setState(3),
              service.getState.map(_.get),
              service.setState(4),
              service.getState.map(_.get),
              service.setState(5),
              service.getState.map(_.get),
              service.setState(6),
              service.apply(_ * 2),
              service.getState.map(_.get),
              service.setState(7),
              service.clear
            )
          )
        )
      }
    }
  }

}

/** Tests scenario when cache repository findById returns an object with invalid entity format. */
class InvalidSessionStateServiceISpec extends SessionStateServiceISpecSetup {

  // Invalid state format
  override val stateFormats: Format[Int] =
    Format(
      Reads { case json =>
        JsError(s"Expected error")
      },
      Writes.apply(entity => JsString(entity.toString()))
    )

  def encryptedState: JsValue = encrypt(-5, List(-4, -3, -2, -1))

  println(encryptedState)

  override lazy val cacheRepository: CacheRepository =
    new CacheRepository(
      dummyMongoComponent,
      "foo",
      Duration("1 minute"),
      new CurrentTimestampSupport
    ) {
      override def ensureIndexes: Future[Seq[String]] = Future.successful(Seq.empty)
      override def ensureSchema: Future[Unit] = Future.successful(())
      override def findById(cacheId: String): Future[Option[CacheItem]] =
        Future.successful(
          Some(CacheItem("foo", Json.obj("TestJourney" -> encryptedState), Instant.now(), Instant.now()))
        )
      override def get[A: Reads](cacheId: String)(dataKey: DataKey[A]): Future[Option[A]] = Future.successful(None)
      override def put[A: Writes](cacheId: String)(dataKey: DataKey[A], data: A): Future[CacheItem] =
        Future.successful(
          CacheItem("foo", Json.obj("TestJourney" -> encryptedState), Instant.now(), Instant.now())
        )
      override def deleteEntity(cacheId: String): Future[Unit] = Future.successful(())
      override def delete[A](cacheId: String)(dataKey: DataKey[A]): Future[Unit] = Future.successful(())
    }

  implicit val context: String = UUID.randomUUID().toString()

  "invalid MongoDBCachedJourneyService" should {

    "throw an exception at an attempt to store and fetch a state" in {
      an[Exception] shouldBe thrownBy {
        await(service.setState(1))
        await(service.getState)
      }
    }

    "throw an exception at an attempt to modify a state using a root state as a default" in {
      an[Exception] shouldBe thrownBy {
        await(service.apply(_ + 10))
      }
    }

    "clear the cache" in {
      await(service.clear) shouldBe (())
    }

    "throw an exception at an attempt to modify current state sequentially" in {
      an[Exception] shouldBe thrownBy {
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
        )
      }
    }

    "throw an exception at an attempt to set, get, modify, clear state sequentially" in {
      an[Exception] shouldBe thrownBy {
        await(
          Future.sequence(
            Seq(
              service.getState.map(_.get),
              service.setState(1),
              service.getState.map(_.get),
              service.setState(2),
              service.getState.map(_.get),
              service.setState(3),
              service.getState.map(_.get),
              service.setState(4),
              service.getState.map(_.get),
              service.setState(5),
              service.getState.map(_.get),
              service.setState(6),
              service.apply(_ * 2),
              service.getState.map(_.get),
              service.setState(7),
              service.clear
            )
          )
        )
      }
    }
  }

}

/** Tests scenario when cache repository findById throws an exception. */
class BrokenSessionStateServiceISpec extends SessionStateServiceISpecSetup {

  class BrokenServiceException extends Exception

  override lazy val cacheRepository: CacheRepository =
    new CacheRepository(
      dummyMongoComponent,
      "foo",
      Duration("1 minute"),
      new CurrentTimestampSupport
    ) {
      override def ensureIndexes: Future[Seq[String]] = Future.successful(Seq.empty)
      override def ensureSchema: Future[Unit] = Future.successful(())
      override def findById(cacheId: String): Future[Option[CacheItem]] = Future.failed(new BrokenServiceException)
      override def get[A: Reads](cacheId: String)(dataKey: DataKey[A]): Future[Option[A]] =
        Future.failed(new BrokenServiceException)
      override def put[A: Writes](cacheId: String)(dataKey: DataKey[A], data: A): Future[CacheItem] =
        Future.failed(new BrokenServiceException)
      override def deleteEntity(cacheId: String): Future[Unit] = Future.failed(new BrokenServiceException)
      override def delete[A](cacheId: String)(dataKey: DataKey[A]): Future[Unit] =
        Future.failed(new BrokenServiceException)
    }

  implicit val context: String = UUID.randomUUID().toString()

  "broken MongoDBCachedJourneyService" should {

    "throw an exception at an attempt to store and fetch a state" in {
      an[RuntimeException] shouldBe thrownBy {
        await(service.setState(1))
        await(service.getState)
      }
    }

    "throw an exception at an attempt to modify a state using a root state as a default" in {
      an[BrokenServiceException] shouldBe thrownBy {
        await(service.apply(_ + 10))
      }
    }

    "clear the cache" in {
      await(service.clear) shouldBe (())
    }

    "throw an exception at an attempt to modify current state sequentially" in {
      an[BrokenServiceException] shouldBe thrownBy {
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
        )
      }
    }

    "throw an exception at an attempt to set, get, modify, clear state sequentially" in {
      an[RuntimeException] shouldBe thrownBy {
        await(
          Future.sequence(
            Seq(
              service.getState.map(_.get),
              service.setState(1),
              service.getState.map(_.get),
              service.setState(2),
              service.getState.map(_.get),
              service.setState(3),
              service.getState.map(_.get),
              service.setState(4),
              service.getState.map(_.get),
              service.setState(5),
              service.getState.map(_.get),
              service.setState(6),
              service.apply(_ * 2),
              service.getState.map(_.get),
              service.setState(7),
              service.clear
            )
          )
        )
      }
    }
  }

}

trait SessionStateServiceISpecSetup extends AppISpec {

  def cacheRepository: CacheRepository

  val dummyMongoComponent: MongoComponent =
    new MongoComponent {
      override def client: MongoClient = null
      override def database: MongoDatabase = null

      override def initTimeout: FiniteDuration = 5.seconds
    }

  val stateFormats: Format[Int] =
    SimpleDecimalFormat(_.toInt, BigDecimal.apply(_))

  def encrypt(i: Int, is: List[Int])(implicit rc: String): JsValue = service.encrypt(i, is)

  // define test service capable of manipulating journey state
  def modify(f: Int => Int) =
    Transition[Int] { case i => Future.successful(f(i)) }

  val notAllowed =
    Transition[Int] {
      case i if false => Future.successful(i)
    }

  // define test service capable of manipulating journey state
  lazy val service = new EncryptedSessionCache[Int, String] {

    override val journeyKey: String = "TestJourney"

    override lazy val actorSystem: ActorSystem = app.injector.instanceOf[ActorSystem]
    override lazy val cacheRepository = SessionStateServiceISpecSetup.this.cacheRepository
    lazy val keyProvider: KeyProvider = KeyProvider(app.injector.instanceOf[Config])
    override lazy val keyProviderFromContext: String => KeyProvider = _ => keyProvider
    override val stateFormats: Format[Int] = SessionStateServiceISpecSetup.this.stateFormats
    override def getJourneyId(journeyId: String): Option[String] = Option(journeyId)
    override val default: Int = 0

    override def updateBreadcrumbs(
      newState: Int,
      currentState: Int,
      currentBreadcrumbs: List[Int]
    ): List[Int] =
      if (newState == currentState)
        currentBreadcrumbs
      else if (currentBreadcrumbs.nonEmpty && currentBreadcrumbs.head == newState)
        currentBreadcrumbs.tail
      else currentState :: currentBreadcrumbs

    def apply(f: Int => Int)(implicit rc: String): Future[Int] = super.updateSessionState(modify(f)).map(_._1)
    def get(implicit rc: String): Future[Option[(Int, List[Int])]] = currentSessionState
    def set(stateAndBreadcrumbs: (Int, List[Int]))(implicit rc: String): Future[(Int, List[Int])] =
      super.save(stateAndBreadcrumbs)
    def getState(implicit rc: String): Future[Option[Int]] = currentSessionState.map(_.map(_._1))
    def setState(state: Int)(implicit rc: String): Future[Int] = super.save((state, Nil)).map(_._1)

  }

  object SimpleDecimalFormat {

    def apply[A](from: BigDecimal => A, to: A => BigDecimal): Format[A] =
      Format(
        Reads {
          case JsNumber(value) if value >= 0 => JsSuccess(from(value))
          case json => JsError(s"Expected non-negative json number but got ${json.getClass.getSimpleName}")
        },
        Writes.apply(entity => JsNumber(to(entity)))
      )

  }

}
