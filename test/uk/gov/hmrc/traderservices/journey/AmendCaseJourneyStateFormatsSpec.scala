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

import play.api.libs.json.{Format, JsResultException, Json}
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.State
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyStateFormats
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.traderservices.support.JsonFormatTest
import java.time.LocalTime
import java.time.ZonedDateTime
import scala.util.Random

class AmendCaseJourneyStateFormatsSpec extends UnitSpec {

  implicit val formats: Format[State] = AmendCaseJourneyStateFormats.formats

  "AmendCaseJourneyStateFormats" should {
    "serialize and deserialize state" in new JsonFormatTest[State](info) {
      validateJsonFormat(
        """{"state":"EnterCaseReferenceNumber","properties":{"model":{}}}""",
        State.EnterCaseReferenceNumber()
      )
      validateJsonFormat(
        """{"state":"EnterCaseReferenceNumber","properties":{"model":{"caseReferenceNumber":"PC12010081330XGBNZJO04"}}}""",
        State.EnterCaseReferenceNumber(AmendCaseStateModel(Some("PC12010081330XGBNZJO04")))
      )
      validateJsonFormat(
        """{"state":"SelectTypeOfAmendment","properties":{"model":{"caseReferenceNumber":"PC12010081330XGBNZJO04"}}}""",
        State.SelectTypeOfAmendment(AmendCaseStateModel(Some("PC12010081330XGBNZJO04")))
      )
      validateJsonFormat(
        """{"state":"EnterResponseText","properties":{"model":{"caseReferenceNumber":"PC12010081330XGBNZJO04","typeOfAmendment":"WriteResponse"}}}""",
        State.EnterResponseText(
          AmendCaseStateModel(Some("PC12010081330XGBNZJO04"), Some(TypeOfAmendment.WriteResponse))
        )
      )
      val text = Random.alphanumeric.take(1000).mkString
      validateJsonFormat(
        s"""{"state":"AmendCaseConfirmation","properties":{"model":{"caseReferenceNumber":"PC12010081330XGBNZJO04","typeOfAmendment":"WriteResponse","responseText":"$text"}}}""",
        State.AmendCaseConfirmation(
          AmendCaseStateModel(Some("PC12010081330XGBNZJO04"), Some(TypeOfAmendment.WriteResponse), Some(text))
        )
      )
    }

    "throw an exception when unknown state" in {
      val json = Json.parse("""{"state":"StrangeState","properties":{}}""")
      an[JsResultException] shouldBe thrownBy {
        json.as[State]
      }
    }

  }
}
