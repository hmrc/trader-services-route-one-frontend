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

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.cache.repository.{CacheMongoRepository, CacheRepository}
import uk.gov.hmrc.crypto.json.{JsonDecryptor, JsonEncryptor}
import uk.gov.hmrc.crypto.{ApplicationCrypto, CompositeSymmetricCrypto, Protected}
import uk.gov.hmrc.play.fsm.PersistentJourneyService

import scala.concurrent.{ExecutionContext, Future}

/**
  * Journey persistence service mixin,
  * stores encrypted serialized state using SessionCache.
  */
trait MongoDBCachedJourneyService[RequestContext] extends PersistentJourneyService[RequestContext] {

  val cacheMongoRepository: CacheMongoRepository
  val applicationCrypto: ApplicationCrypto
  val stateFormats: Format[model.State]
  def getJourneyId(context: RequestContext): Option[String]

  case class PersistentState(state: model.State, breadcrumbs: List[model.State])

  implicit lazy val crypto: CompositeSymmetricCrypto = applicationCrypto.JsonCrypto

  implicit lazy val formats1: Format[model.State] = stateFormats
  implicit lazy val formats2: Format[PersistentState] = Json.format[PersistentState]

  implicit lazy val encryptionFormat: JsonEncryptor[PersistentState] = new JsonEncryptor()
  implicit lazy val decryptionFormat: JsonDecryptor[PersistentState] = new JsonDecryptor()

  final val cache = new SessionCache[Protected[PersistentState], RequestContext] {

    override lazy val sessionName: String = journeyKey
    override lazy val cacheRepository: CacheRepository = cacheMongoRepository

    // uses journeyId as a sessionId to persist state and breadcrumbs
    override def getSessionId(implicit requestContext: RequestContext): Option[String] =
      getJourneyId(requestContext)
  }

  override protected def fetch(implicit
    requestContext: RequestContext,
    ec: ExecutionContext
  ): Future[Option[StateAndBreadcrumbs]] =
    cache.fetch
      .map(_.map { protectedEntry =>
        val entry = protectedEntry.decryptedValue
        (entry.state, entry.breadcrumbs)
      })

  override protected def save(
    state: StateAndBreadcrumbs
  )(implicit requestContext: RequestContext, ec: ExecutionContext): Future[StateAndBreadcrumbs] = {
    val entry = PersistentState(state._1, state._2)
    val protectedEntry = Protected(entry)
    cache
      .save(protectedEntry)
      .map { _ =>
        /* println("-" * 32)
        println(state._1) */
        state
      }
  }

  override def clear(implicit requestContext: RequestContext, ec: ExecutionContext): Future[Unit] =
    cache.delete().map(_ => ())

}
