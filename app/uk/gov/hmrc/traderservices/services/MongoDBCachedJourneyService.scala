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

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.crypto.json.{JsonDecryptor, JsonEncryptor}
import uk.gov.hmrc.crypto.{ApplicationCrypto, CompositeSymmetricCrypto, Protected}
import uk.gov.hmrc.play.fsm.PersistentJourneyService

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.traderservices.repository.CacheRepository
import akka.actor.ActorSystem
import play.api.libs.json.JsValue
import scala.io.AnsiColor

/**
  * Journey persistence service mixin,
  * stores encrypted serialized state using [[JourneyCache]].
  */
trait MongoDBCachedJourneyService[RequestContext] extends PersistentJourneyService[RequestContext] {

  val actorSystem: ActorSystem
  val cacheRepository: CacheRepository
  val applicationCrypto: ApplicationCrypto
  val stateFormats: Format[model.State]
  def getJourneyId(context: RequestContext): Option[String]
  val traceFSM: Boolean = false

  private val self = this

  case class PersistentState(state: model.State, breadcrumbs: List[model.State])

  implicit lazy val crypto: CompositeSymmetricCrypto = applicationCrypto.JsonCrypto

  implicit lazy val formats1: Format[model.State] = stateFormats
  implicit lazy val formats2: Format[PersistentState] = Json.format[PersistentState]

  implicit lazy val encryptionFormat: JsonEncryptor[PersistentState] = new JsonEncryptor()
  implicit lazy val decryptionFormat: JsonDecryptor[PersistentState] = new JsonDecryptor()

  final val cache = new JourneyCache[Protected[PersistentState], RequestContext] {

    override lazy val actorSystem: ActorSystem = self.actorSystem
    override lazy val journeyKey: String = self.journeyKey
    override lazy val cacheRepository: CacheRepository = self.cacheRepository
    override lazy val format: Format[Protected[PersistentState]] = implicitly[Format[Protected[PersistentState]]]

    override def getJourneyId(implicit requestContext: RequestContext): Option[String] =
      self.getJourneyId(requestContext)
  }

  def encrypt(state: model.State, breadcrumbs: List[model.State]): JsValue =
    encryptionFormat.writes(Protected(PersistentState(state, breadcrumbs)))

  final override def apply(
    transition: model.Transition
  )(implicit rc: RequestContext, ec: ExecutionContext): Future[StateAndBreadcrumbs] =
    cache
      .modify(Protected(PersistentState(model.root, Nil))) { protectedEntry =>
        val entry = protectedEntry.decryptedValue
        val (state, breadcrumbs) = (entry.state, entry.breadcrumbs)
        transition.apply
          .applyOrElse(
            state,
            (_: model.State) => model.fail(model.TransitionNotAllowed(state, breadcrumbs, transition))
          )
          .map { endState =>
            Protected(
              PersistentState(
                endState,
                if (endState == state) breadcrumbs
                else state :: breadcrumbsRetentionStrategy(breadcrumbs)
              )
            )
          }
      }
      .map { protectedEntry =>
        val entry = protectedEntry.decryptedValue
        val stateAndBreadcrumbs = (entry.state, entry.breadcrumbs)
        if (traceFSM) {
          println("-" + stateAndBreadcrumbs._2.length + "-" * 32)
          println(s"${AnsiColor.CYAN}Current state: ${AnsiColor.RESET}${stateAndBreadcrumbs._1}")
          println(
            s"${AnsiColor.BLUE}Breadcrumbs: ${AnsiColor.RESET}${stateAndBreadcrumbs._2
              .map(nameOf)}"
          )
        }
        stateAndBreadcrumbs
      }

  final override protected def fetch(implicit
    requestContext: RequestContext,
    ec: ExecutionContext
  ): Future[Option[StateAndBreadcrumbs]] =
    cache.fetch
      .map(_.map { protectedEntry =>
        val entry = protectedEntry.decryptedValue
        (entry.state, entry.breadcrumbs)
      })

  final override protected def save(
    stateAndBreadcrumbs: StateAndBreadcrumbs
  )(implicit requestContext: RequestContext, ec: ExecutionContext): Future[StateAndBreadcrumbs] = {
    val entry = PersistentState(stateAndBreadcrumbs._1, stateAndBreadcrumbs._2)
    val protectedEntry = Protected(entry)
    cache
      .save(protectedEntry)
      .map { _ =>
        if (traceFSM) {
          println("-" + stateAndBreadcrumbs._2.length + "-" * 32)
          println(s"${AnsiColor.CYAN}Current state: ${AnsiColor.RESET}${stateAndBreadcrumbs._1}")
          println(
            s"${AnsiColor.BLUE}Breadcrumbs: ${AnsiColor.RESET}${stateAndBreadcrumbs._2
              .map(nameOf)}"
          )
        }
        stateAndBreadcrumbs
      }
  }

  final override def clear(implicit requestContext: RequestContext, ec: ExecutionContext): Future[Unit] =
    cache.clear()

  private def nameOf(state: model.State): String = {
    val className = state.getClass.getName
    val lastDot = className.lastIndexOf('.')
    val typeName = {
      val s = if (lastDot < 0) className else className.substring(lastDot + 1)
      if (s.last == '$') s.init else s
    }
    val lastDollar = typeName.lastIndexOf('$')
    if (lastDollar < 0) typeName else typeName.substring(lastDollar + 1)
  }

}
