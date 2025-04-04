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

import play.api.Logger
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.traderservices.repository.CacheRepository
import uk.gov.hmrc.mongo.cache.DataKey
import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.Props
import org.apache.pekko.actor.ActorRef

import java.util.concurrent.TimeUnit
import org.apache.pekko.actor.Stash

import scala.concurrent.duration.Duration
import org.apache.pekko.actor.PoisonPill
import org.apache.pekko.pattern.{ExplicitAskSupport, pipe}
import org.apache.pekko.util.Timeout

import java.util.UUID

/** Generic short-term journey state store based on hmrc-mongo cache. Internally employs an actor to make writes and
  * reads sequential per each journeyId.
  */
trait JourneyCache[T, C] extends ExplicitAskSupport {

  val actorSystem: ActorSystem
  val journeyKey: String
  val cacheRepository: CacheRepository
  val format: Format[T]

  val maxWorkerLifespanMinutes: Int = 30
  val timeoutSeconds: Int = 300

  def getJourneyId(implicit requestContext: C): Option[String]

  import JourneyCache._

  private val managerUuid = UUID.randomUUID().toString().takeRight(8)

  lazy val stateCacheActor: ActorRef =
    actorSystem.actorOf(Props(new StateCacheManagerActor), s"SessionCache-$managerUuid-Manager-$journeyKey")

  final implicit val timeout: Timeout =
    Timeout.apply(timeoutSeconds, TimeUnit.SECONDS)

  implicit final def toFuture[A](a: A): Future[A] = Future.successful(a)

  /** Applies atomic transition (modification) of the state. */
  final def modify(
    default: T
  )(modification: T => Future[T])(implicit requestContext: C, ec: ExecutionContext): Future[T] =
    getJourneyId match {
      case Some(journeyId) =>
        stateCacheActor
          .ask(replyTo => (journeyId, Modify(modification, default), replyTo))
          .flatMap {
            case Right(entity: Any) =>
              Future.successful(entity.asInstanceOf[T])

            case Left(JsResultException(jsonErrors)) =>
              val error =
                s"Encountered an issue with de-serialising JSON state from cache: ${jsonErrors
                    .map { case (p, s) =>
                      s"${if (p.toString().isEmpty()) "" else s"$p -> "}${s.map(_.message).mkString(", ")}"
                    }
                    .mkString(", ")}. \nCheck if all your states have relevant entries declared in the *JourneyStateFormats.serializeStateProperties and *JourneyStateFormats.deserializeState functions."
              Logger(getClass).error(error)
              Future.failed(new Exception(error))

            case Left(error: Throwable) =>
              Future.failed(error)

            case Left(e) =>
              Future.failed(new RuntimeException(s"Unknow error $e"))
          }

      case None =>
        val error = s"No journeyId provided in order to cache state in mongo."
        Logger(getClass).warn(error)
        Future.failed(new RuntimeException(error))
    }

  /** Retrieves current state for the journeyId/journeyKey or None. */
  final def fetch(implicit requestContext: C, ec: ExecutionContext): Future[Option[T]] =
    getJourneyId(requestContext) match {
      case Some(journeyId) =>
        stateCacheActor
          .ask(replyTo => (journeyId, Get, replyTo))
          .flatMap {
            case Right(entityOpt: Option[T]) =>
              Future.successful(entityOpt)

            case Left(error: String) =>
              Logger(getClass).warn(error)
              Future.failed(new RuntimeException(error))

            case Left(e) =>
              Future.failed(new RuntimeException(s"Unknow error $e"))
          }

      case None =>
        Future.successful(None)
    }

  /** Saves provided state under the journeyId/journeyKey */
  final def save(input: T)(implicit requestContext: C, ec: ExecutionContext): Future[T] =
    getJourneyId(requestContext) match {
      case Some(journeyId) =>
        stateCacheActor
          .ask(replyTo => (journeyId, Store(input), replyTo))
          .flatMap {
            case Right(_) => input
            case Left(error: String) =>
              Logger(getClass).warn(error)
              Future.failed(new RuntimeException(error))
            case Left(e) =>
              Future.failed(new RuntimeException(s"Unknow error $e"))
          }

      case None =>
        val error = s"No journeyId provided in order to cache state in mongo."
        Logger(getClass).warn(error)
        Future.failed(new RuntimeException(error))
    }

  /** Removes journeyId/journeyKey */
  final def clear()(implicit requestContext: C, ec: ExecutionContext): Future[Unit] =
    getJourneyId(requestContext) match {
      case Some(journeyId) =>
        stateCacheActor
          .ask(replyTo => (journeyId, Delete, replyTo))
          .map(_ => ())

      case None =>
        Future.successful(())
    }

  /** An actor managing a pool of separate workers for each journeyId. */
  final class StateCacheManagerActor extends Actor {

    private val workers = collection.mutable.Map[String, ActorRef]()

    private def getWorker(journeyId: String): ActorRef =
      workers.getOrElseUpdate(journeyId, createWorker(journeyId))

    private def createWorker(journeyId: String): ActorRef = {
      val workerUuid = UUID.randomUUID().toString().takeRight(8)
      Logger(s"uk.gov.hmrc.traderservices.cache.$journeyKey.$managerUuid")
        .debug(s"Creating new cache worker $workerUuid for the journey $journeyId")
      context.system.scheduler
        .scheduleOnce(Duration(maxWorkerLifespanMinutes, TimeUnit.MINUTES), context.self, (journeyId, LifeEnd))(
          context.system.dispatcher
        )
      context.actorOf(
        Props(new StateCacheWorkerActor(journeyId)),
        s"SessionCache-$managerUuid-Worker-$workerUuid-$journeyKey-$journeyId"
      )
    }

    private def removeWorker(journeyId: String): Option[ActorRef] =
      workers.remove(journeyId).map { worker =>
        Logger(s"uk.gov.hmrc.traderservices.cache.$journeyKey.$managerUuid")
          .debug(s"Removing existing cache worker of the journey $journeyId")
        worker
      }

    override def receive: Receive = {

      case m @ (journeyId: String, Modify(modification, default), replyTo: ActorRef) =>
        getWorker(journeyId) ! m

      case m @ (journeyId: String, Get, replyTo: ActorRef) =>
        getWorker(journeyId) ! m

      case m @ (journeyId: String, store: Store, replyTo: ActorRef) =>
        getWorker(journeyId) ! m

      case m @ (journeyId: String, Delete, replyTo: ActorRef) =>
        getWorker(journeyId)
          .ask(ref => (journeyId, Delete, ref))
          .map { value =>
            replyTo ! value
            self ! ((journeyId, LifeEnd))
          }(context.system.dispatcher)

      case (journeyId: String, LifeEnd) =>
        removeWorker(journeyId).foreach(_ ! PoisonPill)
    }
  }

  /** A worker actor asserting sequential writes and reads for some journeyId. */
  final class StateCacheWorkerActor(journeyId: String) extends Actor with Stash {

    implicit final val ec: ExecutionContext = context.system.dispatcher

    private var busy: Boolean = false
    private var timestamp: Long = System.currentTimeMillis()
    private def tryAcquire(): Boolean =
      if (
        busy &&
        (System.currentTimeMillis() - timestamp < (timeoutSeconds * 1000))
      ) false
      else {
        busy = true
        timestamp = System.currentTimeMillis()
        true
      }
    private def release(): Unit = busy = false

    final override def receive: Receive = {
      case Result(value: Any, replyTo: ActorRef) =>
        replyTo ! value
        release()
        unstashAll()

      case (`journeyId`, Modify(modification, default), replyTo: ActorRef) =>
        if (tryAcquire())
          cacheRepository
            .findById(journeyId)
            .map {
              case Some(cacheItem) =>
                (cacheItem.data \ journeyKey).asOpt[T](format) match {
                  case None        => default
                  case Some(value) => value
                }
              case None => default
            }
            .flatMap { entity =>
              modification
                .apply(entity.asInstanceOf[T])
                .flatMap { newEntity =>
                  cacheRepository
                    .put[T](journeyId)(DataKey(journeyKey), newEntity.asInstanceOf[T])(format)
                    .map(_ => Right(newEntity))
                }
            }
            .recover { case e => Left(e) }
            .map(Result(_, replyTo))
            .pipeTo(context.self)
        else
          stash()

      case (`journeyId`, Store(entity), replyTo: ActorRef) =>
        if (tryAcquire())
          cacheRepository
            .put[T](journeyId)(DataKey(journeyKey), entity.asInstanceOf[T])(format)
            .map(ci => Result(Right(()), replyTo))
            .recover { case e =>
              Result(Left(e.getMessage), replyTo)
            }
            .pipeTo(context.self)
        else
          stash()

      case (`journeyId`, Delete, replyTo: ActorRef) =>
        if (tryAcquire())
          cacheRepository
            .deleteEntity(journeyId)
            .map(ci => Result(Right(()), replyTo))
            .recover { case e =>
              Result(Left(e.getMessage), replyTo)
            }
            .pipeTo(context.self)
        else
          stash()

      case (`journeyId`, Get, replyTo: ActorRef) =>
        if (tryAcquire())
          cacheRepository
            .findById(journeyId)
            .map {
              case Some(cacheItem) =>
                Right((cacheItem.data \ journeyKey).asOpt[T](format))

              case None => Right(None)
            }
            .recover {
              case JsResultException(_) =>
                Left(
                  "Encountered issue with de-serialising JSON state from cache. Check if all your states have relevant entries declared in the *JourneyStateFormats.serializeStateProperties and *JourneyStateFormats.deserializeState functions."
                )
              case e =>
                Left(e.getMessage)
            }
            .map(Result(_, replyTo))
            .pipeTo(context.self)
        else
          stash()
    }
  }
}

object JourneyCache {
  case object Get
  case object Delete
  case class Store(entity: Any)
  case class Modify[T](modification: T => Future[T], default: T)
  case class Result(value: Any, replyTo: ActorRef)
  case object Ready
  case object LifeEnd
}
