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
import play.api.Logger
import play.api.libs.json.{Format, JsString, JsValue, Json}
import uk.gov.hmrc.traderservices.journeys.Transition
import uk.gov.hmrc.traderservices.repository.CacheRepository
import uk.gov.hmrc.traderservices.utils.IdentityUtils

import scala.concurrent.{ExecutionContext, Future}
import scala.io.AnsiColor

/** Session state persistence service mixin, stores encrypted serialized state using [[JourneyCache]].
  */
trait EncryptedSessionCache[A, C] {

  val journeyKey: String
  val actorSystem: ActorSystem
  val cacheRepository: CacheRepository
  val stateFormats: Format[A]
  def getJourneyId(context: C): Option[String]
  val keyProviderFromContext: C => KeyProvider
  val default: A

  def updateBreadcrumbs(
    newState: A,
    currentState: A,
    currentBreadcrumbs: List[A]
  ): List[A]

  val trace: Boolean = false

  private val self = this

  case class PersistentState(state: A, breadcrumbs: List[A])

  implicit lazy val formats1: Format[A] = stateFormats
  implicit lazy val formats2: Format[PersistentState] = Json.format[PersistentState]

  private val logger = Logger.apply(this.getClass)

  final val cache = new JourneyCache[String, C] {

    override lazy val actorSystem: ActorSystem = self.actorSystem
    override lazy val journeyKey: String = self.journeyKey
    override lazy val cacheRepository: CacheRepository = self.cacheRepository
    override lazy val format: Format[String] = implicitly[Format[String]]

    override def getJourneyId(implicit context: C): Option[String] =
      self.getJourneyId(context)
  }

  final def encrypt(state: A, breadcrumbs: List[A])(implicit context: C): JsValue =
    JsString(Encryption.encrypt(PersistentState(state, breadcrumbs), keyProviderFromContext(context)))

  final def currentSessionState(implicit
    context: C,
    ec: ExecutionContext
  ): Future[Option[(A, List[A])]] =
    fetch

  final def updateSessionState(
    transition: Transition[A]
  )(implicit context: C, ec: ExecutionContext): Future[(A, List[A])] = {
    val keyProvider = keyProviderFromContext(context)
    val defaultValue = Encryption.encrypt(PersistentState(default, Nil), keyProvider)
    cache
      .modify(defaultValue) { encrypted =>
        val entry = Encryption.decrypt[PersistentState](encrypted, keyProvider)
        val (state, breadcrumbs) = (entry.state, entry.breadcrumbs)
        transition.apply
          .applyOrElse(
            state,
            (_: A) => Future.successful(state)
          )
          .map { endState =>
            Encryption.encrypt(
              PersistentState(
                endState,
                updateBreadcrumbs(endState, state, breadcrumbs)
              ),
              keyProvider
            )
          }
      }
      .map { encrypted =>
        val entry = Encryption.decrypt[PersistentState](encrypted, keyProvider)
        val stateAndBreadcrumbs = (entry.state, entry.breadcrumbs)
        if (trace) {
          logger.debug("-" + stateAndBreadcrumbs._2.length + "-" * 32)
          logger.debug(
            s"${AnsiColor.CYAN}Current state: ${Json
                .prettyPrint(Json.toJson(stateAndBreadcrumbs._1.asInstanceOf[A]))}${AnsiColor.RESET}"
          )
          logger.debug(
            s"${AnsiColor.BLUE}Breadcrumbs: ${stateAndBreadcrumbs._2.map(IdentityUtils.identityOf)}${AnsiColor.RESET}"
          )
        }
        stateAndBreadcrumbs
      }
  }

  final def fetch(implicit
    context: C,
    ec: ExecutionContext
  ): Future[Option[(A, List[A])]] = {
    val keyProvider = keyProviderFromContext(context)
    cache.fetch
      .map(_.map { encrypted =>
        val entry = Encryption.decrypt[PersistentState](encrypted, keyProvider)
        (entry.state, entry.breadcrumbs)
      })
  }

  final def save(
    stateAndBreadcrumbs: (A, List[A])
  )(implicit context: C, ec: ExecutionContext): Future[(A, List[A])] = {
    val keyProvider = keyProviderFromContext(context)
    val entry = PersistentState(stateAndBreadcrumbs._1, stateAndBreadcrumbs._2)
    val encrypted = Encryption.encrypt(entry, keyProvider)
    cache
      .save(encrypted)
      .map { _ =>
        if (trace) {
          logger.debug("-" + stateAndBreadcrumbs._2.length + "-" * 32)
          logger.debug(s"${AnsiColor.CYAN}Current state: ${Json
              .prettyPrint(Json.toJson(stateAndBreadcrumbs._1.asInstanceOf[A]))}${AnsiColor.RESET}")
          logger.debug(
            s"${AnsiColor.BLUE}Breadcrumbs: ${stateAndBreadcrumbs._2.map(IdentityUtils.identityOf)}${AnsiColor.RESET}"
          )
        }
        stateAndBreadcrumbs
      }
  }

  final def clear(implicit context: C, ec: ExecutionContext): Future[Unit] =
    cache.clear()

  final def cleanBreadcrumbs(implicit context: C, ec: ExecutionContext): Future[List[A]] =
    for {
      stateAndBreadcrumbsOpt <- fetch
      breadcrumbs <- stateAndBreadcrumbsOpt match {
                       case None => Future.successful(Nil)
                       case Some((state, breadcrumbs)) =>
                         save((state, Nil)).map(_ => breadcrumbs)
                     }
    } yield breadcrumbs

}
