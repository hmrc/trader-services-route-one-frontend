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
import uk.gov.hmrc.traderservices.services.MongoDBCachedCreateCaseJourneyService

import java.util.UUID

class MongoDBCachedCreateCaseJourneyServiceSpec extends AppISpec {

  lazy val service: MongoDBCachedCreateCaseJourneyService =
    app.injector.instanceOf[MongoDBCachedCreateCaseJourneyService]

  import service.model.CreateCaseJourneyState

  implicit val hc: HeaderCarrier =
    HeaderCarrier()
      .withExtraHeaders("CreateCaseJourney" -> UUID.randomUUID.toString)

  "MongoDBCachedCreateCaseJourneyService" should {

    "keep breadcrumbs when no change in state" in {
      service.updateBreadcrumbs(CreateCaseJourneyState.Start, CreateCaseJourneyState.Start, Nil) shouldBe Nil
    }

    "update breadcrumbs when new state" in {
      service.updateBreadcrumbs(
        CreateCaseJourneyState.EnterEntryDetails(),
        CreateCaseJourneyState.Start,
        Nil
      ) shouldBe List(
        CreateCaseJourneyState.Start
      )

      service.updateBreadcrumbs(
        CreateCaseJourneyState.EnterEntryDetails(),
        CreateCaseJourneyState.ChooseNewOrExistingCase(),
        List(CreateCaseJourneyState.Start)
      ) shouldBe List(CreateCaseJourneyState.ChooseNewOrExistingCase(), CreateCaseJourneyState.Start)
    }

    "trim breadcrumbs when returning back to the previous state" in {
      service.updateBreadcrumbs(
        CreateCaseJourneyState.ChooseNewOrExistingCase(),
        CreateCaseJourneyState.EnterEntryDetails(),
        List(CreateCaseJourneyState.ChooseNewOrExistingCase(), CreateCaseJourneyState.Start)
      ) shouldBe List(CreateCaseJourneyState.Start)
    }
  }

}
