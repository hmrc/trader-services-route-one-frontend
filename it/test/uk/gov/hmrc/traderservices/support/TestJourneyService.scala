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

package uk.gov.hmrc.traderservices.support

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.traderservices.journeys.State
import uk.gov.hmrc.traderservices.services.SessionStateService

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

/** Intended to be mixed-in when binding a real service for integration testing, exposes protected service's methods to
  * the test.
  */
trait TestJourneyService {

  def set(state: State, breadcrumbs: List[State])(implicit
    hc: HeaderCarrier,
    timeout: Duration,
    ec: ExecutionContext
  ): Unit = Await.result(save((state, breadcrumbs)), timeout)

  def setState(
    state: State
  )(implicit hc: HeaderCarrier, timeout: Duration, ec: ExecutionContext): Unit =
    Await.result(save((state, Nil)), timeout)

  def get(implicit
    hc: HeaderCarrier,
    timeout: Duration,
    ec: ExecutionContext
  ): Option[(State, List[State])] = Await.result(fetch, timeout)

  def getState(implicit hc: HeaderCarrier, timeout: Duration, ec: ExecutionContext): State =
    get.get._1

  def getBreadcrumbs(implicit
    hc: HeaderCarrier,
    timeout: Duration,
    ec: ExecutionContext
  ): List[State] = get.get._2

  def fetch(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[(State, List[State])]]

  def save(
    s: (State, List[State])
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[(State, List[State])]
}
