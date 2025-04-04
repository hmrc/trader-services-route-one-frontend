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

package uk.gov.hmrc.traderservices.controllers

import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.CreateCaseJourneyState.Start
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.root

import scala.concurrent.ExecutionContext.Implicits.global

class LanguageSwitchControllerISpec extends CreateCaseJourneyISpecSetup {

  override def uploadMultipleFilesFeature: Boolean = false
  override def requireEnrolmentFeature: Boolean = true
  override def requireOptionalTransportFeature: Boolean = false

  "LanguageSwitchController" when {

    "GET /language/cy" should {
      "show change language to cymraeg" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(root)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val result = await(request("/language/cymraeg").get())
        result.status shouldBe 200
        journey.getState shouldBe Start
        result.body should include("Change the language to English")
      }
    }

    "GET /language/engligh" should {
      "show change language to english" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(root)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val result = await(request("/language/englisg").get())
        result.status shouldBe 200
        journey.getState shouldBe Start
        result.body should include("Newid yr iaith ir Gymraeg")
      }
    }

    "GET /language/xxx" should {
      "show change language to default English if unknown" in {
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(root)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val result = await(request("/language/xxx").get())
        result.status shouldBe 200
        journey.getState shouldBe Start
        result.body should include("Newid yr iaith ir Gymraeg")
      }
    }
  }
}
