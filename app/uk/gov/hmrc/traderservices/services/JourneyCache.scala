/*
 * Copyright 2021 HM Revenue & Customs
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
import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.ActorRef
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import akka.actor.Stash
import scala.concurrent.duration.Duration
import akka.actor.PoisonPill
import java.util.UUID
import akka.pattern.ExplicitAskSupport

/**
  * Generic short-term journey state store based on hmrc-mongo cache.
  * Internally employs an actor to make writes and reads sequential per each journeyId.
  */
trait JourneyCache[T, C] extends ExplicitAskSupport {

  val actorSystem: ActorSystem
  val journeyKey: String
  val cacheRepository: CacheRepository
  val format: Format[T]

  val maxWorkerLifespanMinutes: Int = 30
  val timeoutSeconds: Int = 15

  def getJourneyId(implicit requestContext: C): Option[String]

  private val managerUuid = UUID.randomUUID().toString().takeRight(8)

  lazy val stateCacheActor: ActorRef =
    actorSystem.actorOf(Props(new StateCacheManagerActor), s"SessionCache-$managerUuid-Manager-$journeyKey")

  final implicit val timeout: Timeout =
    Timeout.apply(timeoutSeconds, TimeUnit.SECONDS)

  final def fetch(implicit requestContext: C, ec: ExecutionContext): Future[Option[T]] =
    get.flatMap {
      case Right(cache) => cache
      case Left(error) =>
        Logger(getClass).warn(error)
        Future.failed(new RuntimeException(error))
    }

  final def save(input: T)(implicit requestContext: C, ec: ExecutionContext): Future[T] =
    store(input).flatMap {
      case Right(_) => input
      case Left(error) =>
        Logger(getClass).warn(error)
        Future.failed(new RuntimeException(error))
    }

  implicit final def toFuture[A](a: A): Future[A] = Future.successful(a)

  import JourneyCache._

  private def get(implicit
    requestContext: C,
    ec: ExecutionContext
  ): Future[Either[String, Option[T]]] =
    getJourneyId match {
      case Some(journeyId) =>
        (
          stateCacheActor
            .ask(replyTo => (journeyId, Get, replyTo))
          )
          .map(_.asInstanceOf[Either[String, Option[T]]])

      case None =>
        Right(None)
    }

  private def store(
    journeyState: T
  )(implicit requestContext: C, ec: ExecutionContext): Future[Either[String, Unit]] =
    getJourneyId match {
      case Some(journeyId) =>
        (
          stateCacheActor
            .ask(replyTo => (journeyId, Store(journeyState), replyTo))
          )
          .map(_.asInstanceOf[Either[String, Unit]])

      case None =>
        Left(s"no cacheId provided in order to cache in mongo")
    }

  final def delete()(implicit requestContext: C, ec: ExecutionContext): Future[Either[String, Unit]] =
    getJourneyId match {
      case Some(journeyId) =>
        (
          stateCacheActor
            .ask(replyTo => (journeyId, Delete, replyTo))
          )
          .map(_.asInstanceOf[Either[String, Unit]])

      case None =>
        Right(())
    }

  /** An actor managing a pool of separate workers for each journeyId. */
  final class StateCacheManagerActor extends Actor {

    private val workers = collection.mutable.Map[String, ActorRef]()

    private def getWorker(journeyId: String): ActorRef =
      workers.getOrElseUpdate(journeyId, createWorker(journeyId))

    private def createWorker(journeyId: String): ActorRef = {
      val workerUuid = UUID.randomUUID().toString().takeRight(8)
      Logger(s"uk.gov.hmrc.traderservices.cache.$journeyKey.$managerUuid")
        .info(s"Creating new cache worker $workerUuid for journey $journeyId")
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
          .info(s"Removing existing cache worker for journey $journeyId")
        worker
      }

    override def receive: Receive = {
      case m @ (journeyId: String, Get, replyTo) =>
        getWorker(journeyId) ! m

      case m @ (journeyId: String, store: Store, replyTo) =>
        getWorker(journeyId) ! m

      case m @ (journeyId: String, Delete, replyTo) =>
        getWorker(journeyId) ! m
        removeWorker(journeyId).foreach(_ ! PoisonPill)

      case (journeyId: String, LifeEnd) =>
        removeWorker(journeyId).foreach(_ ! PoisonPill)
    }
  }

  /** A worker actor asserting sequential writes and reads for some journeyId. */
  final class StateCacheWorkerActor(journeyId: String) extends Actor with Stash {
    import akka.pattern.pipe

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

      case (`journeyId`, Store(entity), replyTo: ActorRef) =>
        if (tryAcquire())
          cacheRepository
            .put[T](journeyId)(DataKey(journeyKey), entity.asInstanceOf[T])(format)
            .map(ci => Result(Right(()), replyTo))
            .recover {
              case e => Result(Left(e.getMessage), replyTo)
            }
            .pipeTo(context.self)
        else
          stash()

      case (`journeyId`, Delete, replyTo: ActorRef) =>
        if (tryAcquire())
          cacheRepository
            .deleteEntity(journeyId)
            .map(ci => Result(Right(()), replyTo))
            .recover {
              case e => Result(Left(e.getMessage), replyTo)
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
                (cacheItem.data \ journeyKey).asOpt[JsValue] match {
                  case None => Right(None)
                  case Some(obj: JsValue) =>
                    obj.validate[T](format) match {
                      case JsSuccess(p, _) => Right(Some(p))
                      case JsError(errors) =>
                        val allErrors = errors.map(_._2.map(_.message).mkString(",")).mkString(",")
                        Left(allErrors)
                    }
                }
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
  case class Result(value: Any, replyTo: ActorRef)
  case object Ready
  case object LifeEnd
}
