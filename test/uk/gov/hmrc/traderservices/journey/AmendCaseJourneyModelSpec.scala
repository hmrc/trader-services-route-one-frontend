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

import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.State._
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.Transitions._
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.{Merger, State, Transition}
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.services.AmendCaseJourneyService
import uk.gov.hmrc.traderservices.support.{InMemoryStore, StateMatchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag
import scala.concurrent.Future
import scala.util.Random
import scala.util.Try

class AmendCaseJourneyModelSpec extends UnitSpec with StateMatchers[State] with TestData {

  import scala.concurrent.duration._
  override implicit val defaultTimeout: FiniteDuration = 60 seconds

  // dummy journey context
  case class DummyContext()
  implicit val dummyContext: DummyContext = DummyContext()

  "AmendCaseJourneyModel" when {
    "at state EnterCaseReferenceNumber" should {
      "stay at EnterCaseReferenceNumber when enterCaseReferenceNumber" in {
        given(EnterCaseReferenceNumber()) when enterCaseReferenceNumber(eoriNumber) should thenGo(
          EnterCaseReferenceNumber()
        )
      }

      "go to SelectTypeOfAmendment when sumbited case reference number" in {
        given(EnterCaseReferenceNumber()) when submitedCaseReferenceNumber(eoriNumber)(
          "PC12010081330XGBNZJO04"
        ) should thenGo(
          SelectTypeOfAmendment(AmendCaseStateModel(caseReferenceNumber = Some("PC12010081330XGBNZJO04")))
        )
      }
    }

    "at state SelectTypeOfAmendment" should {
      "go to EnterResponseText when sumbited type of amendment WriteResponse" in {
        given(
          SelectTypeOfAmendment(AmendCaseStateModel(caseReferenceNumber = Some("PC12010081330XGBNZJO04")))
        ) when submitedTypeOfAmendment(eoriNumber)(
          TypeOfAmendment.WriteResponse
        ) should thenGo(
          EnterResponseText(
            AmendCaseStateModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.WriteResponse)
            )
          )
        )
      }

      "go to EnterResponseText when sumbited type of amendment WriteResponseAndUploadDocuments" in {
        given(
          SelectTypeOfAmendment(AmendCaseStateModel(caseReferenceNumber = Some("PC12010081330XGBNZJO04")))
        ) when submitedTypeOfAmendment(eoriNumber)(
          TypeOfAmendment.WriteResponseAndUploadDocuments
        ) should thenGo(
          EnterResponseText(
            AmendCaseStateModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.WriteResponseAndUploadDocuments)
            )
          )
        )
      }

      "go to ??? when sumbited type of amendment UploadDocuments" in {
        given(
          SelectTypeOfAmendment(AmendCaseStateModel(caseReferenceNumber = Some("PC12010081330XGBNZJO04")))
        ) when submitedTypeOfAmendment(eoriNumber)(
          TypeOfAmendment.UploadDocuments
        ) should thenGo(
          WorkInProgressDeadEnd
        )
      }

      "retreat to EnterCaseReferenceNumber when enterCaseReferenceNumber" in {
        val model = AmendCaseStateModel(caseReferenceNumber = Some("PC12010081330XGBNZJO04"))
        given(
          SelectTypeOfAmendment(model)
        ) when enterCaseReferenceNumber(eoriNumber) should thenGo(
          EnterCaseReferenceNumber(model)
        )
      }
    }

    "at state EnterResponseText" should {
      "goto AmendCaseConfirmation when submited response text in WriteResponse mode" in {
        val responseText = Random.alphanumeric.take(1000).mkString
        given(
          EnterResponseText(
            AmendCaseStateModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.WriteResponse)
            )
          )
        ) when submitedResponseText(eoriNumber)(
          responseText
        ) should thenGo(
          AmendCaseConfirmation(
            AmendCaseStateModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.WriteResponse),
              responseText = Some(responseText)
            )
          )
        )
      }

      "goto ??? when submited response text in WriteResponseAndUploadDocuments mode" in {
        val responseText = Random.alphanumeric.take(1000).mkString
        given(
          EnterResponseText(
            AmendCaseStateModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.WriteResponseAndUploadDocuments)
            )
          )
        ) when submitedResponseText(eoriNumber)(
          responseText
        ) should thenGo(
          WorkInProgressDeadEnd
        )
      }

      "fail when submited response text in UploadDocuments mode" in {
        val responseText = Random.alphanumeric.take(1000).mkString
        given(
          EnterResponseText(
            AmendCaseStateModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.UploadDocuments)
            )
          )
        ) shouldFailWhen submitedResponseText(eoriNumber)(responseText)
      }

      "retreat to SelectTypeOfAmendment when backToSelectTypeOfAmendment" in {
        val model = AmendCaseStateModel(caseReferenceNumber = Some("PC12010081330XGBNZJO04"))
        given(
          EnterResponseText(model)
        ) when backToSelectTypeOfAmendment(eoriNumber) should thenGo(
          SelectTypeOfAmendment(model)
        )
      }
    }
  }

  case class given[S <: State: ClassTag](initialState: S)
      extends AmendCaseJourneyService[DummyContext] with InMemoryStore[(State, List[State]), DummyContext] {

    await(save((initialState, Nil)))

    def withBreadcrumbs(breadcrumbs: State*): this.type = {
      val (state, _) = await(fetch).getOrElse((EnterCaseReferenceNumber(), Nil))
      await(save((state, breadcrumbs.toList)))
      this
    }

    def when(transition: Transition): (State, List[State]) =
      await(super.apply(transition))

    def shouldFailWhen(transition: Transition) =
      Try(await(super.apply(transition))).isSuccess shouldBe false

    def when(merger: Merger[S], state: State): (State, List[State]) =
      await(super.modify { s: S => merger.apply((s, state)) })
  }
}
