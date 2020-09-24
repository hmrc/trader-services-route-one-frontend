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

import uk.gov.hmrc.traderservices.journeys.TraderServicesFrontendJourneyModel.State._
import uk.gov.hmrc.traderservices.journeys.TraderServicesFrontendJourneyModel.Transitions._
import uk.gov.hmrc.traderservices.journeys.TraderServicesFrontendJourneyModel.{State, Transition, TransitionNotAllowed}
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.services.TraderServicesFrontendJourneyService
import uk.gov.hmrc.traderservices.support.{InMemoryStore, StateMatchers}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TraderServicesFrontendModelSpec extends UnitSpec with StateMatchers[State] with TestData {

  // dummy journey context
  case class DummyContext()
  implicit val dummyContext: DummyContext = DummyContext()

  "TraderServicesFrontendModel" when {

    "at state Start" should {

      "stay at Start when start" in {
        given(Start) when start(eoriNumber) should thenGo(Start)
      }

      "goto EnterConsignmentDetails when enterConsignmentDetails" in {
        given(Start) when enterConsignmentDetails(eoriNumber) should thenGo(EnterConsignmentDetails(None))
      }

      "raise exception if any other transition requested" in {
        an[TransitionNotAllowed] shouldBe thrownBy {
          given(Start) when submittedConsignmentDetails(eoriNumber)(
            consignmentDetails
          )
        }
      }
    }

    "at state EnterConsignmentDetails" should {

      "goto WorkInProgressDeadEnd when submittedConsignmentDetails" in {
        given(EnterConsignmentDetails(None)) when submittedConsignmentDetails(eoriNumber)(
          consignmentDetails
        ) should thenGo(WorkInProgressDeadEnd)
      }

    }

  }

  case class given(initialState: State)
      extends TraderServicesFrontendJourneyService[DummyContext]
      with InMemoryStore[(State, List[State]), DummyContext] {

    await(save((initialState, Nil)))

    def withBreadcrumbs(breadcrumbs: State*): this.type = {
      val (state, _) = await(fetch).getOrElse((Start, Nil))
      await(save((state, breadcrumbs.toList)))
      this
    }

    def when(transition: Transition): (State, List[State]) =
      await(super.apply(transition))
  }
}

trait TestData {

  val eoriNumber = "foo"
  val correlationId = "123"

  val consignmentDetails = ConsignmentDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-09-23"))

}
