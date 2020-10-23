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
import uk.gov.hmrc.traderservices.journeys.TraderServicesFrontendJourneyModel.State
import uk.gov.hmrc.traderservices.journeys.TraderServicesFrontendJourneyStateFormats
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.traderservices.support.JsonFormatTest
import java.time.LocalTime

class TraderServicesFrontendFormatSpec extends UnitSpec {

  implicit val formats: Format[State] = TraderServicesFrontendJourneyStateFormats.formats

  "TraderServicesFrontendJourneyStateFormats" should {
    "serialize and deserialize state" in new JsonFormatTest[State](info) {
      validateJsonFormat("""{"state":"Start"}""", State.Start)
      validateJsonFormat(
        """{"state":"EnterDeclarationDetails","properties":{"declarationDetailsOpt":{"epu":"123","entryNumber":"100000Z","entryDate":"2000-01-01"}}}""",
        State.EnterDeclarationDetails(
          Some(DeclarationDetails(EPU(123), EntryNumber("100000Z"), LocalDate.parse("2000-01-01")))
        )
      )
      validateJsonFormat(
        """{"state":"AnswerExportQuestionsRequestType","properties":{"declarationDetails":{"epu":"123","entryNumber":"Z00000Z","entryDate":"2020-10-05"},"exportQuestionsAnswers":{}}}""",
        State.AnswerExportQuestionsRequestType(
          DeclarationDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-10-05")),
          ExportQuestions()
        )
      )
      validateJsonFormat(
        """{"state":"AnswerExportQuestionsRouteType","properties":{"declarationDetails":{"epu":"123","entryNumber":"Z00000Z","entryDate":"2020-10-05"},"exportQuestionsAnswers":{"requestType":"New"}}}""",
        State.AnswerExportQuestionsRouteType(
          DeclarationDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-10-05")),
          ExportQuestions(requestType = Some(ExportRequestType.New))
        )
      )
      validateJsonFormat(
        """{"state":"AnswerExportQuestionsHasPriorityGoods","properties":{"declarationDetails":{"epu":"123","entryNumber":"Z00000Z","entryDate":"2020-10-05"},"exportQuestionsAnswers":{"requestType":"New","routeType":"Route6"}}}""",
        State.AnswerExportQuestionsHasPriorityGoods(
          DeclarationDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-10-05")),
          ExportQuestions(requestType = Some(ExportRequestType.New), routeType = Some(ExportRouteType.Route6))
        )
      )
      validateJsonFormat(
        """{"state":"AnswerExportQuestionsWhichPriorityGoods","properties":{"declarationDetails":{"epu":"123","entryNumber":"Z00000Z","entryDate":"2020-10-05"},"exportQuestionsAnswers":{"requestType":"New","routeType":"Route1"}}}""",
        State.AnswerExportQuestionsWhichPriorityGoods(
          DeclarationDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-10-05")),
          ExportQuestions(requestType = Some(ExportRequestType.New), routeType = Some(ExportRouteType.Route1))
        )
      )
      validateJsonFormat(
        """{"state":"AnswerExportQuestionsFreightType","properties":{"declarationDetails":{"epu":"123","entryNumber":"Z00000Z","entryDate":"2020-10-05"},"exportQuestionsAnswers":{"requestType":"New","routeType":"Route1","priorityGoods":"LiveAnimals"}}}""",
        State.AnswerExportQuestionsFreightType(
          DeclarationDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-10-05")),
          ExportQuestions(
            requestType = Some(ExportRequestType.New),
            routeType = Some(ExportRouteType.Route1),
            priorityGoods = Some(ExportPriorityGoods.LiveAnimals)
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerExportQuestionsFreightType","properties":{"declarationDetails":{"epu":"123","entryNumber":"Z00000Z","entryDate":"2020-10-05"},"exportQuestionsAnswers":{"requestType":"Hold","priorityGoods":"LiveAnimals"}}}""",
        State.AnswerExportQuestionsFreightType(
          DeclarationDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-10-05")),
          ExportQuestions(
            requestType = Some(ExportRequestType.Hold),
            priorityGoods = Some(ExportPriorityGoods.LiveAnimals)
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerExportQuestionsMandatoryVesselInfo","properties":{"declarationDetails":{"epu":"123","entryNumber":"Z00000Z","entryDate":"2020-10-05"},"exportQuestionsAnswers":{"requestType":"C1601","priorityGoods":"LiveAnimals","freightType":"RORO"}}}""",
        State.AnswerExportQuestionsMandatoryVesselInfo(
          DeclarationDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-10-05")),
          ExportQuestions(
            requestType = Some(ExportRequestType.C1601),
            priorityGoods = Some(ExportPriorityGoods.LiveAnimals),
            freightType = Some(ExportFreightType.RORO)
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerExportQuestionsOptionalVesselInfo","properties":{"declarationDetails":{"epu":"123","entryNumber":"Z00000Z","entryDate":"2020-10-05"},"exportQuestionsAnswers":{"requestType":"C1601","priorityGoods":"LiveAnimals","freightType":"RORO"}}}""",
        State.AnswerExportQuestionsOptionalVesselInfo(
          DeclarationDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-10-05")),
          ExportQuestions(
            requestType = Some(ExportRequestType.C1601),
            priorityGoods = Some(ExportPriorityGoods.LiveAnimals),
            freightType = Some(ExportFreightType.RORO)
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerExportQuestionsContactInfo","properties":{"declarationDetails":{"epu":"123","entryNumber":"Z00000Z","entryDate":"2020-10-05"},
          |"exportQuestionsAnswers":{"requestType":"Hold","priorityGoods":"LiveAnimals","freightType":"RORO",
          |"contactInfo":{"contactName":"Full Name","contactEmail":"name@somewhere.com","contactNumber":"012345678910"}}}}""".stripMargin,
        State.AnswerExportQuestionsContactInfo(
          DeclarationDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-10-05")),
          ExportQuestions(
            requestType = Some(ExportRequestType.Hold),
            priorityGoods = Some(ExportPriorityGoods.LiveAnimals),
            freightType = Some(ExportFreightType.RORO),
            contactInfo = Some(
              ExportContactInfo(
                contactName = "Full Name",
                contactEmail = "name@somewhere.com",
                contactNumber = Some("012345678910")
              )
            )
          )
        )
      )
      validateJsonFormat(
        """{"state":"ExportQuestionsSummary","properties":{
          |"declarationDetails":{"epu":"123","entryNumber":"Z00000Z","entryDate":"2020-10-05"},
          |"exportQuestionsAnswers":{"requestType":"Hold","routeType":"Route2","hasPriorityGoods":true,"priorityGoods":"LiveAnimals","freightType":"RORO",
          |"vesselDetails":{"vesselName":"Foo Bar","dateOfArrival":"2020-10-19","timeOfArrival":"00:00:00"},
          |"contactInfo":{"contactName":"Full Name","contactEmail":"name@somewhere.com","contactNumber":"012345678910"}}}}""".stripMargin,
        State.ExportQuestionsSummary(
          DeclarationDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-10-05")),
          ExportQuestions(
            requestType = Some(ExportRequestType.Hold),
            routeType = Some(ExportRouteType.Route2),
            hasPriorityGoods = Some(true),
            priorityGoods = Some(ExportPriorityGoods.LiveAnimals),
            freightType = Some(ExportFreightType.RORO),
            vesselDetails = Some(
              VesselDetails(
                vesselName = Some("Foo Bar"),
                dateOfArrival = Some(LocalDate.parse("2020-10-19")),
                timeOfArrival = Some(LocalTime.parse("00:00"))
              )
            ),
            contactInfo = Some(
              ExportContactInfo(
                contactName = "Full Name",
                contactEmail = "name@somewhere.com",
                contactNumber = Some("012345678910")
              )
            )
          )
        )
      )

      validateJsonFormat(
        """{"state":"AnswerImportQuestionsRequestType","properties":{"declarationDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},"importQuestionsAnswers":{}}}""",
        State.AnswerImportQuestionsRequestType(
          DeclarationDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
          ImportQuestions()
        )
      )
      validateJsonFormat(
        """{"state":"AnswerImportQuestionsRouteType","properties":{"declarationDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},"importQuestionsAnswers":{"requestType":"Hold"}}}""",
        State.AnswerImportQuestionsRouteType(
          DeclarationDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
          ImportQuestions(
            requestType = Some(ImportRequestType.Hold)
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerImportQuestionsHasPriorityGoods","properties":{"declarationDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},"importQuestionsAnswers":{"requestType":"Hold","routeType":"Route1Cap"}}}""",
        State.AnswerImportQuestionsHasPriorityGoods(
          DeclarationDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
          ImportQuestions(
            requestType = Some(ImportRequestType.Hold),
            routeType = Some(ImportRouteType.Route1Cap)
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerImportQuestionsWhichPriorityGoods","properties":{"declarationDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},"importQuestionsAnswers":{"requestType":"Hold","routeType":"Route1Cap","priorityGoods":"LiveAnimals"}}}""",
        State.AnswerImportQuestionsWhichPriorityGoods(
          DeclarationDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
          ImportQuestions(
            requestType = Some(ImportRequestType.Hold),
            routeType = Some(ImportRouteType.Route1Cap),
            priorityGoods = Some(ImportPriorityGoods.LiveAnimals)
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerImportQuestionsALVS","properties":{"declarationDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},"importQuestionsAnswers":{"requestType":"Hold","priorityGoods":"LiveAnimals","hasALVS":true}}}""",
        State.AnswerImportQuestionsALVS(
          DeclarationDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
          ImportQuestions(
            requestType = Some(ImportRequestType.Hold),
            priorityGoods = Some(ImportPriorityGoods.LiveAnimals),
            hasALVS = Some(true)
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerImportQuestionsFreightType","properties":{"declarationDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},"importQuestionsAnswers":{"requestType":"Hold","priorityGoods":"LiveAnimals","hasALVS":true,"freightType":"RORO"}}}""",
        State.AnswerImportQuestionsFreightType(
          DeclarationDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
          ImportQuestions(
            requestType = Some(ImportRequestType.Hold),
            priorityGoods = Some(ImportPriorityGoods.LiveAnimals),
            hasALVS = Some(true),
            freightType = Some(ImportFreightType.RORO)
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerImportQuestionsMandatoryVesselInfo","properties":{"declarationDetails":{"epu":"123","entryNumber":"100000Z","entryDate":"2020-10-05"},"importQuestionsAnswers":{"requestType":"Hold","priorityGoods":"LiveAnimals","freightType":"RORO"}}}""",
        State.AnswerImportQuestionsMandatoryVesselInfo(
          DeclarationDetails(EPU(123), EntryNumber("100000Z"), LocalDate.parse("2020-10-05")),
          ImportQuestions(
            requestType = Some(ImportRequestType.Hold),
            priorityGoods = Some(ImportPriorityGoods.LiveAnimals),
            freightType = Some(ImportFreightType.RORO)
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerImportQuestionsOptionalVesselInfo","properties":{"declarationDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},"importQuestionsAnswers":{"requestType":"New","priorityGoods":"LiveAnimals","hasALVS":true,"freightType":"RORO"}}}""",
        State.AnswerImportQuestionsOptionalVesselInfo(
          DeclarationDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
          ImportQuestions(
            requestType = Some(ImportRequestType.New),
            priorityGoods = Some(ImportPriorityGoods.LiveAnimals),
            hasALVS = Some(true),
            freightType = Some(ImportFreightType.RORO)
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerImportQuestionsContactInfo","properties":{"declarationDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},
          |"importQuestionsAnswers":{"requestType":"Hold","priorityGoods":"LiveAnimals","hasALVS":true,"freightType":"RORO",
          |"contactInfo":{"contactName":"Full Name","contactEmail":"name@somewhere.com","contactNumber":"012345678910"}}}}""".stripMargin,
        State.AnswerImportQuestionsContactInfo(
          DeclarationDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
          ImportQuestions(
            requestType = Some(ImportRequestType.Hold),
            priorityGoods = Some(ImportPriorityGoods.LiveAnimals),
            hasALVS = Some(true),
            freightType = Some(ImportFreightType.RORO),
            contactInfo = Some(
              ImportContactInfo(
                contactName = "Full Name",
                contactEmail = "name@somewhere.com",
                contactNumber = Some("012345678910")
              )
            )
          )
        )
      )
      validateJsonFormat(
        """{"state":"ImportQuestionsSummary","properties":{"declarationDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},
          |"importQuestionsAnswers":{"requestType":"Hold","routeType":"Route3","hasPriorityGoods":true,"priorityGoods":"LiveAnimals","hasALVS":true,"freightType":"RORO",
          |"vesselDetails":{"vesselName":"Foo Bar","dateOfArrival":"2020-10-19","timeOfArrival":"00:00:00"},
          |"contactInfo":{"contactName":"Full Name","contactEmail":"name@somewhere.com","contactNumber":"012345678910"}}}}""".stripMargin,
        State.ImportQuestionsSummary(
          DeclarationDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
          ImportQuestions(
            requestType = Some(ImportRequestType.Hold),
            routeType = Some(ImportRouteType.Route3),
            hasPriorityGoods = Some(true),
            priorityGoods = Some(ImportPriorityGoods.LiveAnimals),
            hasALVS = Some(true),
            freightType = Some(ImportFreightType.RORO),
            vesselDetails = Some(
              VesselDetails(
                vesselName = Some("Foo Bar"),
                dateOfArrival = Some(LocalDate.parse("2020-10-19")),
                timeOfArrival = Some(LocalTime.parse("00:00"))
              )
            ),
            contactInfo = Some(
              ImportContactInfo(
                contactName = "Full Name",
                contactEmail = "name@somewhere.com",
                contactNumber = Some("012345678910")
              )
            )
          )
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
