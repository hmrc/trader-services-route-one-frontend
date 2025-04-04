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

import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.CreateCaseJourneyState.Start
import uk.gov.hmrc.traderservices.journeys.{FileUploadJourneyModelMixin, State, Transition}
import uk.gov.hmrc.traderservices.services.Breadcrumbs

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

trait TestJourneyService extends FileUploadJourneyModelMixin {

  val journeyKey: String = "TestJourney"

  private val counter: AtomicInteger = new AtomicInteger(0)

  val storage = new InMemoryStore[(State, List[State])] {}

  def apply(
    transition: Transition[State]
  )(implicit ec: ExecutionContext): Future[(State, List[State])] =
    storage.fetch
      .map { case (state, breadcrumbs) =>
        transition.apply
          .applyOrElse(
            state,
            (_: State) => Future.successful(state)
          )
          .map { endState =>
            storage.save(
              (
                endState,
                Breadcrumbs
                  .updateBreadcrumbs(endState, state, breadcrumbs, (s: State) => s.isInstanceOf[IsTransient])
              )
            )
          }
      }
      .getOrElse(Future.successful((Start, Nil)))

  def set(state: State, breadcrumbs: List[State]): Unit =
    storage.save((state, breadcrumbs))

  def get: Option[(State, List[State])] =
    storage.fetch

  def clear: Future[Unit] =
    Future.successful(storage.clear())

  def getCounter(): Int = counter.get()

}
