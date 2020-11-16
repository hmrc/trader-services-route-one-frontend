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

package uk.gov.hmrc.traderservices.journey

import java.time.LocalDate

import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.State._
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.Transitions._
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.Rules._
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.{Merger, State, Transition, TransitionNotAllowed}
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.services.AmendCaseJourneyService
import uk.gov.hmrc.traderservices.support.{InMemoryStore, StateMatchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag
import scala.concurrent.Future

class AmendCaseJourneyModelSpec extends UnitSpec with StateMatchers[State] with TestData {

  import scala.concurrent.duration._
  override implicit val defaultTimeout: FiniteDuration = 60 seconds

  // dummy journey context
  case class DummyContext()
  implicit val dummyContext: DummyContext = DummyContext()

  "AmendCaseJourneyModel" when {
    "at state EnterCaseReferenceNumber" should {
      "stay at EnterCaseReferenceNumber when enterCaseReferenceNumber" in {
        given(EnterCaseReferenceNumber) when enterCaseReferenceNumber(eoriNumber) should thenGo(
          EnterCaseReferenceNumber
        )
      }

      "go to SelectAmendScenario when " in {
        given(EnterCaseReferenceNumber) when submitedCaseReferenceNumber(eoriNumber)(
          "PC12010081330XGBNZJO04"
        ) should thenGo(
          SelectAmendScenario("PC12010081330XGBNZJO04")
        )
      }
    }
  }

  case class given[S <: State: ClassTag](initialState: S)
      extends AmendCaseJourneyService[DummyContext] with InMemoryStore[(State, List[State]), DummyContext] {

    await(save((initialState, Nil)))

    def withBreadcrumbs(breadcrumbs: State*): this.type = {
      val (state, _) = await(fetch).getOrElse((EnterCaseReferenceNumber, Nil))
      await(save((state, breadcrumbs.toList)))
      this
    }

    def when(transition: Transition): (State, List[State]) =
      await(super.apply(transition))

    def when(merger: Merger[S], state: State): (State, List[State]) =
      await(super.modify { s: S => merger.apply((s, state)) })
  }
}
