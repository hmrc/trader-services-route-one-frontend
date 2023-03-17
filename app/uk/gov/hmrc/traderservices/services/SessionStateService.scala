/*
 * Copyright 2023 HM Revenue & Customs
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

import akka.actor.{ActorSystem, Scheduler}
import akka.pattern.FutureTimeoutSupport
import com.typesafe.config.Config
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.traderservices.journeys.{JourneyModel, State, Transition}
import uk.gov.hmrc.traderservices.repository.CacheRepository
import uk.gov.hmrc.traderservices.wiring.AppConfig

import javax.inject.Inject
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

trait SessionStateService extends JourneyModel {

  val journeyKey: String
  type Breadcrumbs = List[State]
  type StateAndBreadcrumbs = (State, Breadcrumbs)

  def currentSessionState(implicit rc: HeaderCarrier, ec: ExecutionContext): Future[Option[(State, List[State])]]

  /** Applies transition to the current state and returns new state or error */
  def updateSessionState(
    transition: Transition[State]
  )(implicit rc: HeaderCarrier, ec: ExecutionContext): Future[(State, List[State])]

  def cleanBreadcrumbs(implicit rc: HeaderCarrier, ec: ExecutionContext): Future[List[State]]

  /** Return the current state if matches expected type or apply the transition. */
  final def getCurrentOrUpdateSessionState[S <: State: ClassTag](
    transition: Transition[State]
  )(implicit rc: HeaderCarrier, ec: ExecutionContext): Future[(State, List[State])] =
    currentSessionState.flatMap {
      case Some(sb @ (state, _)) if is[State](state) => Future.successful(sb)
      case _ => updateSessionState(transition)
    }

  /** Modify [[show]] behaviour: Try first rollback to the most recent state of type S and display if found, otherwise
    * redirect back to the root state.
    *
    * @note
    *   to alter behaviour follow with [[orApply]] or [[orApplyWithRequest]]
    */

  final def rollback[S <: State: ClassTag]()(implicit
    rc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[(State, List[State])] =
    currentSessionState.flatMap {
      case Some(sb @ (state, _)) if is[State](state) => Future.successful(sb)
      case _                                         => Future.successful((root, List()))
    }

  /** Wait for state until timeout. */
  final def waitForSessionState[S <: State: ClassTag](intervalInMiliseconds: Long, timeoutNanoTime: Long)(
    ifTimeout: => Future[(State, List[State])]
  )(implicit rc: HeaderCarrier, scheduler: Scheduler, ec: ExecutionContext): Future[(State, List[State])] =
    currentSessionState.flatMap {
      case Some(sb @ (state: State, _)) if is[S](state) =>
        Future.successful(sb)
      case _ =>
        if (System.nanoTime() > timeoutNanoTime) {
          ifTimeout
        } else
          ScheduleAfter(intervalInMiliseconds) {
            waitForSessionState[S](intervalInMiliseconds * 2, timeoutNanoTime)(ifTimeout)
          }
    }

  def is[S <: State: ClassTag](state: State): Boolean =
    implicitly[ClassTag[S]].runtimeClass.isAssignableFrom(state.getClass)

  def updateBreadcrumbs(newState: State, currentState: State, currentBreadcrumbs: List[State]): List[State]

  val breadcrumbsRetentionStrategy: Breadcrumbs => Breadcrumbs = identity

}

object ScheduleAfter extends FutureTimeoutSupport {

  /** Delay execution of the future by given miliseconds */
  def apply[T](
    delayInMiliseconds: Long
  )(body: => Future[T])(implicit scheduler: Scheduler, ec: ExecutionContext): Future[T] =
    after(duration = FiniteDuration(delayInMiliseconds, "ms"), using = scheduler)(body)
}
