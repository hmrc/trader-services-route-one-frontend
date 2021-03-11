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

/**
  * Generic short-term session store based on hmrc-mongo cache.
  */
trait SessionCache[T, C] {

  val sessionName: String
  val cacheRepository: CacheRepository

  def getSessionId(implicit requestContext: C): Option[String]

  final def fetch(implicit requestContext: C, reads: Reads[T], ec: ExecutionContext): Future[Option[T]] =
    get.flatMap {
      case Right(cache) => cache
      case Left(error) =>
        Logger(getClass).warn(error)
        Future.failed(new RuntimeException(error))
    }

  final def save(input: T)(implicit requestContext: C, writes: Writes[T], ec: ExecutionContext): Future[T] =
    store(input).flatMap {
      case Right(_) => input
      case Left(error) =>
        Logger(getClass).warn(error)
        Future.failed(new RuntimeException(error))
    }

  implicit final def toFuture[A](a: A): Future[A] = Future.successful(a)

  private def get(implicit
    reads: Reads[T],
    requestContext: C,
    ec: ExecutionContext
  ): Future[Either[String, Option[T]]] =
    getSessionId match {
      case Some(sessionId) ⇒
        cacheRepository
          .findById(sessionId)
          .flatMap {
            case Some(cacheItem) =>
              (cacheItem.data \ sessionName).asOpt[JsValue] match {
                case None => Right(None)
                case Some(obj: JsValue) =>
                  obj.validate[T] match {
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
            case e ⇒
              Left(e.getMessage)
          }

      case None ⇒
        Right(None)
    }

  private def store(
    newSession: T
  )(implicit writes: Writes[T], requestContext: C, ec: ExecutionContext): Future[Either[String, Unit]] =
    getSessionId match {
      case Some(sessionId) ⇒
        cacheRepository
          .put[T](sessionId)(DataKey(sessionName), newSession)
          .map(ci => Right(()))

      case None ⇒
        Left(s"no cacheId provided in order to cache in mongo")
    }

  final def delete()(implicit requestContext: C, ec: ExecutionContext): Future[Either[String, Unit]] =
    getSessionId match {
      case Some(sessionId) ⇒
        cacheRepository
          .deleteEntity(sessionId)
          .map(ci => Right(()))

      case None ⇒
        Right(())
    }
}
