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

import uk.gov.hmrc.traderservices.support.AppISpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.traderservices.models.AmendCaseModel
import uk.gov.hmrc.traderservices.services.MongoDBCachedAmendCaseJourneyService

import java.util.UUID

class MongoDBCachedAmendCaseJourneyServiceSpec extends AppISpec {

  lazy val service: MongoDBCachedAmendCaseJourneyService =
    app.injector.instanceOf[MongoDBCachedAmendCaseJourneyService]

  import service.model.State

  implicit val hc: HeaderCarrier =
    HeaderCarrier()
      .withExtraHeaders("AmendCaseJourney" -> UUID.randomUUID.toString)

  "MongoDBCachedAmendCaseJourneyService" should {

    "keep breadcrumbs when no change in state" in {
      service.updateBreadcrumbs(State.Start, State.Start, Nil) shouldBe Nil
    }

    "update breadcrumbs when new state" in {
      service.updateBreadcrumbs(State.EnterCaseReferenceNumber(), State.Start, Nil) shouldBe List(
        State.Start
      )

      service.updateBreadcrumbs(
        State.SelectTypeOfAmendment(AmendCaseModel()),
        State.EnterCaseReferenceNumber(),
        List(State.Start)
      ) shouldBe List(State.EnterCaseReferenceNumber(), State.Start)
    }

    "trim breadcrumbs when returning back to the previous state" in {
      service.updateBreadcrumbs(
        State.EnterCaseReferenceNumber(),
        State.SelectTypeOfAmendment(AmendCaseModel()),
        List(State.EnterCaseReferenceNumber(), State.Start)
      ) shouldBe List(State.Start)
    }
  }

}
