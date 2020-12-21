/*
 * Copyright 2020 HM Revenue & Customs
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
import uk.gov.hmrc.cache.model.Id
import uk.gov.hmrc.cache.repository.CacheRepository

import scala.concurrent.{ExecutionContext, Future}

/**
  * Generic short-term session store based on mongo-caching.
  */
trait SessionCache[T, C] {

  val sessionName: String
  val cacheRepository: CacheRepository

  def getSessionId(implicit requestContext: C): Option[String]

  def fetch(implicit requestContext: C, reads: Reads[T], ec: ExecutionContext): Future[Option[T]] =
    get.flatMap {
      case Right(cache) => cache
      case Left(error) =>
        Logger(getClass).warn(error)
        Future.failed(new RuntimeException(error))
    }

  def save(input: T)(implicit requestContext: C, writes: Writes[T], ec: ExecutionContext): Future[T] =
    store(input).flatMap {
      case Right(_) => input
      case Left(error) =>
        Logger(getClass).warn(error)
        Future.failed(new RuntimeException(error))
    }

  implicit def toFuture[A](a: A): Future[A] = Future.successful(a)

  private def get(implicit
    reads: Reads[T],
    requestContext: C,
    ec: ExecutionContext
  ): Future[Either[String, Option[T]]] =
    getSessionId match {
      case Some(sessionId) ⇒
        cacheRepository
          .findById(Id(sessionId))
          .flatMap(_.flatMap(_.data))
          .flatMap {
            case Some(cache) =>
              (cache \ sessionName).asOpt[JsValue] match {
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
          .createOrUpdate(Id(sessionId), sessionName, Json.toJson(newSession))
          .map[Either[String, Unit]] { dbUpdate ⇒
            if (dbUpdate.writeResult.inError)
              Left(dbUpdate.writeResult.errmsg.getOrElse("unknown error during inserting session data in mongo"))
            else
              Right(())
          }
          .recover {
            case e ⇒
              Left(e.getMessage)
          }

      case None ⇒
        Left(s"no sessionId found in the HeaderCarrier to store in mongo")
    }

  def delete()(implicit requestContext: C, ec: ExecutionContext): Future[Either[String, Unit]] =
    getSessionId match {
      case Some(sessionId) ⇒
        cacheRepository
          .removeById(Id(sessionId))
          .map[Either[String, Unit]] { dbUpdate ⇒
            if (dbUpdate.writeErrors.nonEmpty)
              Left(dbUpdate.writeErrors.map(_.errmsg).mkString(","))
            else
              Right(())
          }
          .recover {
            case e ⇒
              Left(e.getMessage)
          }

      case None ⇒
        Right(())
    }
}
