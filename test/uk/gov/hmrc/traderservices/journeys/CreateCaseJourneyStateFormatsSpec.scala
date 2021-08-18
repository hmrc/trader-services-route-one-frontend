/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.traderservices.journeys

import java.time.LocalDate
import play.api.libs.json.{Format, JsResultException, Json}
import uk.gov.hmrc.traderservices.connectors.TraderServicesResult
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.State
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.FileUploadState
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.support.UnitSpec
import uk.gov.hmrc.traderservices.support.JsonFormatTest

import java.time.LocalTime
import java.time.ZonedDateTime
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.FileUploadHostData

class CreateCaseJourneyStateFormatsSpec extends UnitSpec {

  implicit val formats: Format[State] = CreateCaseJourneyStateFormats.formats
  val generatedAt = java.time.LocalDateTime.of(2018, 12, 11, 10, 20, 30)

  "CreateCaseJourneyStateFormats" should {
    "serialize and deserialize state" in new JsonFormatTest[State](info) {
      validateJsonFormat("""{"state":"Start"}""", State.Start)
      validateJsonFormat(
        """{"state":"TurnToAmendCaseJourney","properties":{"continueAmendCaseJourney":true}}""",
        State.TurnToAmendCaseJourney(true)
      )
      validateJsonFormat(
        """{"state":"ChooseNewOrExistingCase","properties":{"newOrExistingCaseOpt":"New","entryDetailsOpt":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},
          |"importQuestionsAnswersOpt":{"requestType":"New","routeType":"Route3","hasPriorityGoods":true,"priorityGoods":"LiveAnimals","hasALVS":true,"freightType":"RORO",
          |"vesselDetails":{"vesselName":"Foo Bar","dateOfArrival":"2020-10-19","timeOfArrival":"00:00:00"},
          |"contactInfo":{"contactName":"Full Name","contactEmail":"name@somewhere.com","contactNumber":"012345678910"}},"continueAmendCaseJourney":true}}""".stripMargin,
        State.ChooseNewOrExistingCase(
          Some(NewOrExistingCase.New),
          Some(EntryDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05"))),
          importQuestionsAnswersOpt = Some(
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
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
                  contactName = Some("Full Name"),
                  contactEmail = "name@somewhere.com",
                  contactNumber = Some("012345678910")
                )
              )
            )
          ),
          continueAmendCaseJourney = true
        )
      )
      validateJsonFormat(
        """{"state":"EnterEntryDetails","properties":{"entryDetailsOpt":{"epu":"123","entryNumber":"100000Z","entryDate":"2000-01-01"}}}""",
        State.EnterEntryDetails(
          Some(EntryDetails(EPU(123), EntryNumber("100000Z"), LocalDate.parse("2000-01-01")))
        )
      )
      validateJsonFormat(
        """{"state":"AnswerExportQuestionsRequestType","properties":{"model":{"entryDetails":{"epu":"123","entryNumber":"Z00000Z","entryDate":"2020-10-05"},"exportQuestionsAnswers":{}}}}""",
        State.AnswerExportQuestionsRequestType(
          ExportQuestionsStateModel(
            EntryDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions()
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerExportQuestionsRouteType","properties":{"model":{"entryDetails":{"epu":"123","entryNumber":"Z00000Z","entryDate":"2020-10-05"},"exportQuestionsAnswers":{"requestType":"New"}}}}""",
        State.AnswerExportQuestionsRouteType(
          ExportQuestionsStateModel(
            EntryDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(requestType = Some(ExportRequestType.New))
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerExportQuestionsHasPriorityGoods","properties":{"model":{"entryDetails":{"epu":"123","entryNumber":"Z00000Z","entryDate":"2020-10-05"},"exportQuestionsAnswers":{"requestType":"New","routeType":"Route6"}}}}""",
        State.AnswerExportQuestionsHasPriorityGoods(
          ExportQuestionsStateModel(
            EntryDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(requestType = Some(ExportRequestType.New), routeType = Some(ExportRouteType.Route6))
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerExportQuestionsWhichPriorityGoods","properties":{"model":{"entryDetails":{"epu":"123","entryNumber":"Z00000Z","entryDate":"2020-10-05"},"exportQuestionsAnswers":{"requestType":"New","routeType":"Route1"}}}}""",
        State.AnswerExportQuestionsWhichPriorityGoods(
          ExportQuestionsStateModel(
            EntryDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(requestType = Some(ExportRequestType.New), routeType = Some(ExportRouteType.Route1))
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerExportQuestionsFreightType","properties":{"model":{"entryDetails":{"epu":"123","entryNumber":"Z00000Z","entryDate":"2020-10-05"},"exportQuestionsAnswers":{"requestType":"New","routeType":"Route1","priorityGoods":"LiveAnimals"}}}}""",
        State.AnswerExportQuestionsFreightType(
          ExportQuestionsStateModel(
            EntryDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(
              requestType = Some(ExportRequestType.New),
              routeType = Some(ExportRouteType.Route1),
              priorityGoods = Some(ExportPriorityGoods.LiveAnimals)
            )
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerExportQuestionsFreightType","properties":{"model":{"entryDetails":{"epu":"123","entryNumber":"Z00000Z","entryDate":"2020-10-05"},"exportQuestionsAnswers":{"requestType":"C1601","routeType":"Route3","priorityGoods":"LiveAnimals"}}}}""",
        State.AnswerExportQuestionsFreightType(
          ExportQuestionsStateModel(
            EntryDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(
              routeType = Some(ExportRouteType.Route3),
              requestType = Some(ExportRequestType.C1601),
              priorityGoods = Some(ExportPriorityGoods.LiveAnimals)
            )
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerExportQuestionsMandatoryVesselInfo","properties":{"model":{"entryDetails":{"epu":"123","entryNumber":"Z00000Z","entryDate":"2020-10-05"},"exportQuestionsAnswers":{"requestType":"C1601","routeType":"Route3","priorityGoods":"LiveAnimals","freightType":"RORO"}}}}""",
        State.AnswerExportQuestionsMandatoryVesselInfo(
          ExportQuestionsStateModel(
            EntryDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1601),
              routeType = Some(ExportRouteType.Route3),
              priorityGoods = Some(ExportPriorityGoods.LiveAnimals),
              freightType = Some(ExportFreightType.RORO)
            )
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerExportQuestionsOptionalVesselInfo","properties":{"model":{"entryDetails":{"epu":"123","entryNumber":"Z00000Z","entryDate":"2020-10-05"},"exportQuestionsAnswers":{"requestType":"C1601","routeType":"Route3","priorityGoods":"LiveAnimals","freightType":"RORO"}}}}""",
        State.AnswerExportQuestionsOptionalVesselInfo(
          ExportQuestionsStateModel(
            EntryDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1601),
              routeType = Some(ExportRouteType.Route3),
              priorityGoods = Some(ExportPriorityGoods.LiveAnimals),
              freightType = Some(ExportFreightType.RORO)
            )
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerExportQuestionsContactInfo","properties":{"model":{"entryDetails":{"epu":"123","entryNumber":"Z00000Z","entryDate":"2020-10-05"},
          |"exportQuestionsAnswers":{"requestType":"C1601","routeType":"Route3","priorityGoods":"LiveAnimals","freightType":"RORO",
          |"contactInfo":{"contactName":"Full Name","contactEmail":"name@somewhere.com","contactNumber":"012345678910"}}}}}""".stripMargin,
        State.AnswerExportQuestionsContactInfo(
          ExportQuestionsStateModel(
            EntryDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1601),
              routeType = Some(ExportRouteType.Route3),
              priorityGoods = Some(ExportPriorityGoods.LiveAnimals),
              freightType = Some(ExportFreightType.RORO),
              contactInfo = Some(
                ExportContactInfo(
                  contactName = Some("Full Name"),
                  contactEmail = "name@somewhere.com",
                  contactNumber = Some("012345678910")
                )
              )
            )
          )
        )
      )
      validateJsonFormat(
        """{"state":"ExportQuestionsSummary","properties":{"model":{
          |"entryDetails":{"epu":"123","entryNumber":"Z00000Z","entryDate":"2020-10-05"},
          |"exportQuestionsAnswers":{"requestType":"C1601","routeType":"Route2","hasPriorityGoods":true,"priorityGoods":"LiveAnimals","freightType":"RORO",
          |"vesselDetails":{"vesselName":"Foo Bar","dateOfArrival":"2020-10-19","timeOfArrival":"00:00:00"},
          |"contactInfo":{"contactName":"Full Name","contactEmail":"name@somewhere.com","contactNumber":"012345678910"}}}}}""".stripMargin,
        State.ExportQuestionsSummary(
          ExportQuestionsStateModel(
            EntryDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1601),
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
                  contactName = Some("Full Name"),
                  contactEmail = "name@somewhere.com",
                  contactNumber = Some("012345678910")
                )
              )
            )
          )
        )
      )
      validateJsonFormat(
        """{"state":"ExportQuestionsMissingInformationError","properties":{"model":{
          |"entryDetails":{"epu":"123","entryNumber":"Z00000Z","entryDate":"2020-10-05"},
          |"exportQuestionsAnswers":{"requestType":"C1601","routeType":"Route2","hasPriorityGoods":true,"priorityGoods":"LiveAnimals","freightType":"RORO",
          |"vesselDetails":{"vesselName":"Foo Bar","dateOfArrival":"2020-10-19","timeOfArrival":"00:00:00"},
          |"contactInfo":{"contactName":"Full Name","contactEmail":"name@somewhere.com","contactNumber":"012345678910"}}}}}""".stripMargin,
        State.ExportQuestionsMissingInformationError(
          ExportQuestionsStateModel(
            EntryDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-10-05")),
            ExportQuestions(
              requestType = Some(ExportRequestType.C1601),
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
                  contactName = Some("Full Name"),
                  contactEmail = "name@somewhere.com",
                  contactNumber = Some("012345678910")
                )
              )
            )
          )
        )
      )

      validateJsonFormat(
        """{"state":"AnswerImportQuestionsRequestType","properties":{"model":{"entryDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},"importQuestionsAnswers":{}}}}""",
        State.AnswerImportQuestionsRequestType(
          ImportQuestionsStateModel(
            EntryDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
            ImportQuestions()
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerImportQuestionsRouteType","properties":{"model":{"entryDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},"importQuestionsAnswers":{"requestType":"New"}}}}""",
        State.AnswerImportQuestionsRouteType(
          ImportQuestionsStateModel(
            EntryDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New)
            )
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerImportQuestionsHasPriorityGoods","properties":{"model":{"entryDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},"importQuestionsAnswers":{"requestType":"New","routeType":"Route1Cap"}}}}""",
        State.AnswerImportQuestionsHasPriorityGoods(
          ImportQuestionsStateModel(
            EntryDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route1Cap)
            )
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerImportQuestionsWhichPriorityGoods","properties":{"model":{"entryDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},"importQuestionsAnswers":{"requestType":"New","routeType":"Route1Cap","priorityGoods":"LiveAnimals"}}}}""",
        State.AnswerImportQuestionsWhichPriorityGoods(
          ImportQuestionsStateModel(
            EntryDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route1Cap),
              priorityGoods = Some(ImportPriorityGoods.LiveAnimals)
            )
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerImportQuestionsALVS","properties":{"model":{"entryDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},"importQuestionsAnswers":{"requestType":"New","routeType":"Route3","priorityGoods":"LiveAnimals","hasALVS":true}}}}""",
        State.AnswerImportQuestionsALVS(
          ImportQuestionsStateModel(
            EntryDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route3),
              priorityGoods = Some(ImportPriorityGoods.LiveAnimals),
              hasALVS = Some(true)
            )
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerImportQuestionsFreightType","properties":{"model":{"entryDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},"importQuestionsAnswers":{"requestType":"New","routeType":"Route3","priorityGoods":"LiveAnimals","hasALVS":true,"freightType":"RORO"}}}}""",
        State.AnswerImportQuestionsFreightType(
          ImportQuestionsStateModel(
            EntryDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route3),
              priorityGoods = Some(ImportPriorityGoods.LiveAnimals),
              hasALVS = Some(true),
              freightType = Some(ImportFreightType.RORO)
            )
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerImportQuestionsMandatoryVesselInfo","properties":{"model":{"entryDetails":{"epu":"123","entryNumber":"100000Z","entryDate":"2020-10-05"},"importQuestionsAnswers":{"requestType":"New","routeType":"Hold","priorityGoods":"LiveAnimals","freightType":"RORO"}}}}""",
        State.AnswerImportQuestionsMandatoryVesselInfo(
          ImportQuestionsStateModel(
            EntryDetails(EPU(123), EntryNumber("100000Z"), LocalDate.parse("2020-10-05")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Hold),
              priorityGoods = Some(ImportPriorityGoods.LiveAnimals),
              freightType = Some(ImportFreightType.RORO)
            )
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerImportQuestionsOptionalVesselInfo","properties":{"model":{"entryDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},"importQuestionsAnswers":{"requestType":"New","routeType":"Route1","priorityGoods":"LiveAnimals","hasALVS":true,"freightType":"RORO"}}}}""",
        State.AnswerImportQuestionsOptionalVesselInfo(
          ImportQuestionsStateModel(
            EntryDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route1),
              priorityGoods = Some(ImportPriorityGoods.LiveAnimals),
              hasALVS = Some(true),
              freightType = Some(ImportFreightType.RORO)
            )
          )
        )
      )
      validateJsonFormat(
        """{"state":"AnswerImportQuestionsContactInfo","properties":{"model":{"entryDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},
          |"importQuestionsAnswers":{"requestType":"New","routeType":"Route3","priorityGoods":"LiveAnimals","hasALVS":true,"freightType":"RORO",
          |"contactInfo":{"contactName":"Full Name","contactEmail":"name@somewhere.com","contactNumber":"012345678910"}}}}}""".stripMargin,
        State.AnswerImportQuestionsContactInfo(
          ImportQuestionsStateModel(
            EntryDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
              routeType = Some(ImportRouteType.Route3),
              priorityGoods = Some(ImportPriorityGoods.LiveAnimals),
              hasALVS = Some(true),
              freightType = Some(ImportFreightType.RORO),
              contactInfo = Some(
                ImportContactInfo(
                  contactName = Some("Full Name"),
                  contactEmail = "name@somewhere.com",
                  contactNumber = Some("012345678910")
                )
              )
            )
          )
        )
      )
      validateJsonFormat(
        """{"state":"ImportQuestionsSummary","properties":{"model":{"entryDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},
          |"importQuestionsAnswers":{"requestType":"New","routeType":"Route3","hasPriorityGoods":true,"priorityGoods":"LiveAnimals","hasALVS":true,"freightType":"RORO",
          |"vesselDetails":{"vesselName":"Foo Bar","dateOfArrival":"2020-10-19","timeOfArrival":"00:00:00"},
          |"contactInfo":{"contactName":"Full Name","contactEmail":"name@somewhere.com","contactNumber":"012345678910"}}}}}""".stripMargin,
        State.ImportQuestionsSummary(
          ImportQuestionsStateModel(
            EntryDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
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
                  contactName = Some("Full Name"),
                  contactEmail = "name@somewhere.com",
                  contactNumber = Some("012345678910")
                )
              )
            )
          )
        )
      )
      validateJsonFormat(
        """{"state":"ImportQuestionsMissingInformationError","properties":{"model":{"entryDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},
          |"importQuestionsAnswers":{"requestType":"New","routeType":"Route3","hasPriorityGoods":true,"priorityGoods":"LiveAnimals","hasALVS":true,"freightType":"RORO",
          |"vesselDetails":{"vesselName":"Foo Bar","dateOfArrival":"2020-10-19","timeOfArrival":"00:00:00"},
          |"contactInfo":{"contactName":"Full Name","contactEmail":"name@somewhere.com","contactNumber":"012345678910"}}}}}""".stripMargin,
        State.ImportQuestionsMissingInformationError(
          ImportQuestionsStateModel(
            EntryDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
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
                  contactName = Some("Full Name"),
                  contactEmail = "name@somewhere.com",
                  contactNumber = Some("012345678910")
                )
              )
            )
          )
        )
      )
      validateJsonFormat(
        """{"state":"UploadFile","properties":{"hostData":{"entryDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},
          |"questionsAnswers":{"import":{"requestType":"New","routeType":"Route3","hasPriorityGoods":true,"priorityGoods":"LiveAnimals","hasALVS":true,"freightType":"RORO",
          |"vesselDetails":{"vesselName":"Foo Bar","dateOfArrival":"2020-10-19","timeOfArrival":"00:00:00"},
          |"contactInfo":{"contactName":"Bob","contactEmail":"name@somewhere.com","contactNumber":"012345678910"}}}},
          |"reference":"foo-bar-ref",
          |"uploadRequest":{"href":"https://foo.bar","fields":{}},
          |"fileUploads":{"files":[
          |{"Initiated":{"nonce":0,"timestamp":0,"reference":"foo1"}},
          |{"Posted":{"nonce":0,"timestamp":0,"reference":"foo3"}},
          |{"Accepted":{"nonce":0,"timestamp":0,"reference":"foo4","url":"https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
          |"uploadTimestamp":"2018-04-24T09:30:00Z","checksum":"396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100","fileName":"test.pdf","fileMimeType":"application/pdf","fileSize":4567890}},
          |{"Failed":{"nonce":0,"timestamp":0,"reference":"foo2","details":{"failureReason":"QUARANTINE","message":"some reason"}}}
          |]},"maybeUploadError":{"FileVerificationFailed":{"details":{"failureReason":"QUARANTINE","message":"some reason"}}}}}""".stripMargin,
        FileUploadState.UploadFile(
          FileUploadHostData(
            EntryDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
            ImportQuestions(
              requestType = Some(ImportRequestType.New),
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
                  contactName = Some("Bob"),
                  contactEmail = "name@somewhere.com",
                  contactNumber = Some("012345678910")
                )
              )
            )
          ),
          "foo-bar-ref",
          UploadRequest(href = "https://foo.bar", fields = Map.empty),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo1"),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo3"),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "foo4",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              ),
              FileUpload
                .Failed(
                  Nonce.Any,
                  Timestamp.Any,
                  "foo2",
                  UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
                )
            )
          ),
          Some(FileVerificationFailed(UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")))
        )
      )
      validateJsonFormat(
        """{"state":"UploadFile","properties":{"hostData":{"entryDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},
          |"questionsAnswers":{"export":{"requestType":"New","routeType":"Route2","hasPriorityGoods":false,"freightType":"Air",
          |"vesselDetails":{"vesselName":"Foo Bar","dateOfArrival":"2020-10-19","timeOfArrival":"10:09:00"},
          |"contactInfo":{"contactName":"Bob","contactEmail":"name@somewhere.com","contactNumber":"012345678910"}}}},
          |"reference":"foo-bar-ref-2",
          |"uploadRequest":{"href":"https://foo.bar","fields":{"amz":"123"}},
          |"fileUploads":{"files":[
          |{"Initiated":{"nonce":0,"timestamp":0,"reference":"foo1"}},
          |{"Accepted":{"nonce":0,"timestamp":0,"reference":"foo4","url":"https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
          |"uploadTimestamp":"2018-04-24T09:30:00Z","checksum":"396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100","fileName":"test.pdf","fileMimeType":"application/pdf","fileSize":4567890}},
          |{"Failed":{"nonce":0,"timestamp":0,"reference":"foo2","details":{"failureReason":"QUARANTINE","message":"some reason"}}},
          |{"Posted":{"nonce":0,"timestamp":0,"reference":"foo3"}}
          |]}}}""".stripMargin,
        FileUploadState.UploadFile(
          FileUploadHostData(
            EntryDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
            exportQuestions
          ),
          "foo-bar-ref-2",
          UploadRequest(href = "https://foo.bar", fields = Map("amz" -> "123")),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo1"),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "foo4",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              ),
              FileUpload
                .Failed(
                  Nonce.Any,
                  Timestamp.Any,
                  "foo2",
                  UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
                ),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo3")
            )
          )
        )
      )
      validateJsonFormat(
        """{"state":"WaitingForFileVerification","properties":{"hostData":{"entryDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},
          |"questionsAnswers":{"export":{"requestType":"New","routeType":"Route2","hasPriorityGoods":false,"freightType":"Air",
          |"vesselDetails":{"vesselName":"Foo Bar","dateOfArrival":"2020-10-19","timeOfArrival":"10:09:00"},
          |"contactInfo":{"contactName":"Bob","contactEmail":"name@somewhere.com","contactNumber":"012345678910"}}}},
          |"reference":"foo-bar-ref-2",
          |"uploadRequest":{"href":"https://foo.bar","fields":{"amz":"123"}},
          |"currentFileUpload":{"Posted":{"nonce":0,"timestamp":0,"reference":"foo3"}},
          |"fileUploads":{"files":[
          |{"Initiated":{"nonce":0,"timestamp":0,"reference":"foo1"}},
          |{"Accepted":{"nonce":0,"timestamp":0,"reference":"foo4","url":"https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
          |"uploadTimestamp":"2018-04-24T09:30:00Z","checksum":"396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100","fileName":"test.pdf","fileMimeType":"application/pdf","fileSize":4567890}},
          |{"Failed":{"nonce":0,"timestamp":0,"reference":"foo2","details":{"failureReason":"QUARANTINE","message":"some reason"}}},
          |{"Posted":{"nonce":0,"timestamp":0,"reference":"foo3"}}
          |]}}}""".stripMargin,
        FileUploadState.WaitingForFileVerification(
          FileUploadHostData(
            EntryDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
            exportQuestions
          ),
          "foo-bar-ref-2",
          UploadRequest(href = "https://foo.bar", fields = Map("amz" -> "123")),
          FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo3"),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo1"),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "foo4",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              ),
              FileUpload
                .Failed(
                  Nonce.Any,
                  Timestamp.Any,
                  "foo2",
                  UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
                ),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo3")
            )
          )
        )
      )

      validateJsonFormat(
        """{"state":"FileUploaded","properties":{"hostData":{"entryDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},
          |"questionsAnswers":{"export":{"requestType":"New","routeType":"Route2","hasPriorityGoods":false,"freightType":"Air",
          |"vesselDetails":{"vesselName":"Foo Bar","dateOfArrival":"2020-10-19","timeOfArrival":"10:09:00"},
          |"contactInfo":{"contactName":"Bob","contactEmail":"name@somewhere.com","contactNumber":"012345678910"}}}},
          |"fileUploads":{"files":[
          |{"Initiated":{"nonce":0,"timestamp":0,"reference":"foo1"}},
          |{"Accepted":{"nonce":0,"timestamp":0,"reference":"foo4","url":"https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
          |"uploadTimestamp":"2018-04-24T09:30:00Z","checksum":"396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100","fileName":"test.pdf","fileMimeType":"application/pdf","fileSize":4567890}},
          |{"Failed":{"nonce":0,"timestamp":0,"reference":"foo2","details":{"failureReason":"QUARANTINE","message":"some reason"}}},
          |{"Posted":{"nonce":0,"timestamp":0,"reference":"foo3"}}
          |]},
          |"acknowledged":false}}""".stripMargin,
        FileUploadState.FileUploaded(
          FileUploadHostData(
            EntryDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
            exportQuestions
          ),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo1"),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "foo4",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              ),
              FileUpload
                .Failed(
                  Nonce.Any,
                  Timestamp.Any,
                  "foo2",
                  UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
                ),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo3")
            )
          )
        )
      )

      validateJsonFormat(
        """{"state":"UploadMultipleFiles","properties":{"hostData":{"entryDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},
          |"questionsAnswers":{"export":{"requestType":"New","routeType":"Route2","hasPriorityGoods":false,"freightType":"Air",
          |"vesselDetails":{"vesselName":"Foo Bar","dateOfArrival":"2020-10-19","timeOfArrival":"10:09:00"},
          |"contactInfo":{"contactName":"Bob","contactEmail":"name@somewhere.com","contactNumber":"012345678910"}}}},
          |"fileUploads":{"files":[
          |{"Initiated":{"nonce":0,"timestamp":0,"reference":"foo1","uploadRequest":{"href":"https://foo.bar","fields":{"amz":"123"}},"uploadId":"001"}},
          |{"Accepted":{"nonce":0,"timestamp":0,"reference":"foo4","url":"https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
          |"uploadTimestamp":"2018-04-24T09:30:00Z","checksum":"396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100","fileName":"test.pdf","fileMimeType":"application/pdf","fileSize":4567890}},
          |{"Failed":{"nonce":0,"timestamp":0,"reference":"foo2","details":{"failureReason":"QUARANTINE","message":"some reason"}}},
          |{"Posted":{"nonce":0,"timestamp":0,"reference":"foo3"}}
          |]}}}""".stripMargin,
        FileUploadState.UploadMultipleFiles(
          FileUploadHostData(
            EntryDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
            exportQuestions
          ),
          FileUploads(files =
            Seq(
              FileUpload
                .Initiated(
                  Nonce.Any,
                  Timestamp.Any,
                  reference = "foo1",
                  uploadRequest = Some(UploadRequest(href = "https://foo.bar", fields = Map("amz" -> "123"))),
                  uploadId = Some("001")
                ),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "foo4",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              ),
              FileUpload
                .Failed(
                  Nonce.Any,
                  Timestamp.Any,
                  "foo2",
                  UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
                ),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo3")
            )
          )
        )
      )

      validateJsonFormat(
        s"""{"state":"CreateCaseConfirmation","properties":{"entryDetails":{"epu":"123","entryNumber":"000000Z","entryDate":"2020-10-05"},
           |"questionsAnswers":{"export":{"requestType":"New","routeType":"Route2","hasPriorityGoods":false,"freightType":"Air",
           |"vesselDetails":{"vesselName":"Foo Bar","dateOfArrival":"2020-10-19","timeOfArrival":"10:09:00"},
           |"contactInfo":{"contactName":"Bob","contactEmail":"name@somewhere.com","contactNumber":"012345678910"}}},
           |"uploadedFiles":[
           |{"upscanReference":"foo","downloadUrl":"https://bucketName.s3.eu-west-2.amazonaws.com?1235676","uploadTimestamp":"2018-04-24T09:30:00Z","checksum":"396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100","fileName":"test.pdf","fileMimeType":"application/pdf","fileSize":4567890}
           |],
           |"result":{"caseId":"7w7e7wq87ABDFD78wq7e87","generatedAt":"${generatedAt.toString}","fileTransferResults":[]},
           |"caseSLA":{"dateTime":"${generatedAt.plusHours(2)}"}}}""".stripMargin,
        State.CreateCaseConfirmation(
          EntryDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-10-05")),
          exportQuestions,
          Seq(
            UploadedFile(
              "foo",
              "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
              ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              "test.pdf",
              "application/pdf",
              Some(4567890)
            )
          ),
          TraderServicesResult("7w7e7wq87ABDFD78wq7e87", generatedAt),
          CaseSLA(Some(generatedAt.plusHours(2)))
        )
      )
      validateJsonFormat(
        """{"state":"CaseAlreadyExists","properties":{"caseReferenceId":"7w7e7wq87ABDFD78wq7e87"}}""".stripMargin,
        State.CaseAlreadyExists("7w7e7wq87ABDFD78wq7e87")
      )
      validateJsonFormat(
        """{"state":"CaseAlreadySubmitted"}""".stripMargin,
        State.CaseAlreadySubmitted
      )
    }

    "throw an exception when unknown state" in {
      val json = Json.parse("""{"state":"StrangeState","properties":{}}""")
      an[JsResultException] shouldBe thrownBy {
        json.as[State]
      }
    }

  }

  val exportQuestions = ExportQuestions(
    requestType = Some(ExportRequestType.New),
    routeType = Some(ExportRouteType.Route2),
    hasPriorityGoods = Some(false),
    freightType = Some(ExportFreightType.Air),
    vesselDetails = Some(
      VesselDetails(
        vesselName = Some("Foo Bar"),
        dateOfArrival = Some(LocalDate.parse("2020-10-19")),
        timeOfArrival = Some(LocalTime.parse("10:09"))
      )
    ),
    contactInfo = Some(
      ExportContactInfo(
        contactName = Some("Bob"),
        contactEmail = "name@somewhere.com",
        contactNumber = Some("012345678910")
      )
    )
  )
}
