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

package uk.gov.hmrc.traderservices.connectors

import play.api.Application
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.http._
import uk.gov.hmrc.traderservices.stubs.TraderServicesApiStubs
import uk.gov.hmrc.traderservices.support.{AppISpec, TestData}

import scala.concurrent.ExecutionContext.Implicits.global
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class TraderServicesApiConnectorISpec extends TraderServicesApiConnectorISpecSetup {

  "TraderServicesApiConnector" when {
    "createCase" should {
      "return case reference id if success" in {
        givenCreateCaseApiRequestSucceeds()

        val result: TraderServicesCaseResponse =
          await(connector.createCase(createCaseRequest))

        result.result shouldBe Some(TraderServicesResult("A1234567890", generatedAt, fileTransferResults))
        result.error shouldBe None

        verifyCreateCaseRequestHappened(1)
      }

      "return error code and message if failure" in {
        givenCreateCaseApiStub(400, validRequestOfCreateCaseApi(), createCaseApiErrorResponseBody("555", "Foo Bar"))

        val result: TraderServicesCaseResponse =
          await(connector.createCase(createCaseRequest))

        result.result shouldBe None
        result.error shouldBe Some(ApiError("555", Some("Foo Bar")))

        verifyCreateCaseRequestHappened(3)
      }

      "throw exception if returns 500" in {
        givenCreateCaseApiStub(500, validRequestOfCreateCaseApi(), "")

        an[TraderServicesApiError] shouldBe thrownBy {
          await(connector.createCase(createCaseRequest))
        }

        verifyCreateCaseRequestHappened(3)
      }
    }

    "updateCase" should {
      "return same case reference id if success" in {
        givenUpdateCaseApiRequestSucceeds()

        val result: TraderServicesCaseResponse =
          await(connector.updateCase(updateCaseRequest))

        result.result shouldBe Some(TraderServicesResult("A1234567890", generatedAt, fileTransferResults))
        result.error shouldBe None
      }

      "return error code and message if failure" in {
        givenUpdateCaseApiStub(400, validRequestOfUpdateCaseApi(), createCaseApiErrorResponseBody("555", "Foo Bar"))

        val result: TraderServicesCaseResponse =
          await(connector.updateCase(updateCaseRequest))

        result.result shouldBe None
        result.error shouldBe Some(ApiError("555", Some("Foo Bar")))
      }

      "throw exception if returns 500" in {
        givenUpdateCaseApiStub(500, validRequestOfUpdateCaseApi(), "")

        an[TraderServicesAmendApiError] shouldBe thrownBy {
          await(connector.updateCase(updateCaseRequest))
        }
      }
    }
  }

}

trait TraderServicesApiConnectorISpecSetup extends AppISpec with TraderServicesApiStubs {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def fakeApplication: Application = appBuilder.build()

  lazy val connector: TraderServicesApiConnector =
    app.injector.instanceOf[TraderServicesApiConnector]

  def createCaseRequest = {
    val dateTimeOfArrival = LocalDateTime.now.plusDays(1).truncatedTo(ChronoUnit.MINUTES)
    TraderServicesCreateCaseRequest(
      entryDetails = TestData.exportEntryDetails,
      questionsAnswers = TestData.fullExportQuestions(dateTimeOfArrival),
      uploadedFiles = Seq(
        UploadedFile(
          "foo-1234567890",
          "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
          ZonedDateTime.ofLocal(dateTimeOfArrival, ZoneId.of("GMT"), ZoneOffset.ofHours(0)),
          "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
          "test.pdf",
          "application/pdf",
          Some(4567890)
        )
      ),
      eori = Some("GB123456789012345")
    )
  }

  def updateCaseRequest = {
    val dateTimeOfArrival = LocalDateTime.now.plusDays(1).truncatedTo(ChronoUnit.MINUTES)
    TraderServicesUpdateCaseRequest(
      caseReferenceNumber = "A1234567890",
      typeOfAmendment = TypeOfAmendment.WriteResponseAndUploadDocuments,
      responseText = Some("An example description."),
      uploadedFiles = Seq(
        UploadedFile(
          "foo-0123456789",
          "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
          ZonedDateTime.ofLocal(dateTimeOfArrival, ZoneId.of("GMT"), ZoneOffset.ofHours(0)),
          "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
          "test.pdf",
          "application/pdf",
          Some(4567890)
        )
      ),
      eori = Some("GB123456789012345")
    )
  }

  // {"upscanReference":"foo1","success":true,"httpStatus":201,"transferredAt":"2021-04-18T12:07:36"}
  val fileTransferResults = List(
    FileTransferResult(
      upscanReference = "foo1",
      success = true,
      httpStatus = 201,
      transferredAt = LocalDateTime.parse("2021-04-18T12:07:36")
    )
  )
}
