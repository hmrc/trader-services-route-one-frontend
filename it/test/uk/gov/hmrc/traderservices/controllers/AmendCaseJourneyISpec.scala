/*
 * Copyright 2024 HM Revenue & Customs
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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.util.ByteString
import com.github.tomakehurst.wiremock.client.WireMock
import com.typesafe.config.Config
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json._
import play.api.libs.ws.{DefaultWSCookie, StandaloneWSRequest}
import play.api.mvc._
import play.api.test.FakeRequest
import uk.gov.hmrc.traderservices.stubs.{PdfGeneratorStubs, TraderServicesApiStubs, UpscanInitiateStubs}
import uk.gov.hmrc.traderservices.support
import uk.gov.hmrc.traderservices.support.{ServerISpec, StateMatchers, TestData, TestJourneyService}
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.http.SessionKeys.authToken
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, SessionId, SessionKeys}
import uk.gov.hmrc.traderservices.connectors.{FileTransferResult, TraderServicesResult}
import uk.gov.hmrc.traderservices.controllers.{AmendCaseJourneyController, routes}
import uk.gov.hmrc.traderservices.journeys.{AmendCaseJourneyStateFormats, State}
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.repository.CacheRepository
import uk.gov.hmrc.traderservices.services.{AmendCaseJourneyService, EncryptedSessionCache, KeyProvider, MongoDBCachedAmendCaseJourneyService}
import uk.gov.hmrc.traderservices.utils.SHA256
import uk.gov.hmrc.traderservices.views.CommonUtilsHelper._

import java.time.{LocalDateTime, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

class AmendCaseJourneyISpec
    extends AmendCaseJourneyISpecSetup with TraderServicesApiStubs with UpscanInitiateStubs with PdfGeneratorStubs {

  import journey.model.FileUploadState._
  import journey.model.State._

  val dateTime = LocalDateTime.now()

  override def uploadMultipleFilesFeature: Boolean = false
  override def requireEnrolmentFeature: Boolean = true
  override def requireOptionalTransportFeature: Boolean = false

  "AmendCaseJourneyController" when {

    "user not enrolled for HMRC-XYZ" should {
      "be redirected to the subscription journey" in {
        journey.setState(Start)
        givenAuthorisedWithoutEnrolments()
        givenDummySubscriptionUrl
        val result = await(request("/").get())
        result.status shouldBe 200
        verifyAuthoriseAttempt()
        verifySubscriptionAttempt()
      }
    }

    "preferUploadMultipleFiles" should {
      "return false when jsenabled cookie NOT set" in {
        controller.preferUploadMultipleFiles(FakeRequest()) shouldBe false
      }

      "return false when jsenabled cookie set but uploadMultipleFilesFeature flag NOT set" in {
        controller.preferUploadMultipleFiles(
          FakeRequest().withCookies(Cookie(controller.COOKIE_JSENABLED, "true"))
        ) shouldBe false
      }
    }

    "successRedirect" should {
      "return /file-verification when jsenabled cookie NOT set" in {
        controller.successRedirect(journeyId.value)(FakeRequest()) should endWith(
          "/send-documents-for-customs-check/add/file-verification"
        )
      }

      "return /journey/:journeyId/file-verification when jsenabled cookie set" in {
        controller.successRedirect(journeyId.value)(
          fakeRequest(Cookie(controller.COOKIE_JSENABLED, "true"))
        ) should endWith(
          s"/send-documents-for-customs-check/add/journey/${journeyId.value}/file-verification"
        )
      }
    }

    "errorRedirect" should {
      "return /file-rejected when jsenabled cookie NOT set" in {
        controller.errorRedirect(journeyId.value)(FakeRequest()) should endWith(
          "/send-documents-for-customs-check/add/file-rejected"
        )
      }

      "return /journey/:journeyId/file-rejected when jsenabled cookie set" in {
        controller.errorRedirect(journeyId.value)(
          fakeRequest(Cookie(controller.COOKIE_JSENABLED, "true"))
        ) should endWith(
          s"/send-documents-for-customs-check/add/journey/${journeyId.value}/file-rejected"
        )
      }
    }

    "GET /add/case-reference-number" should {
      "show enter case reference number page" in {
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/add/case-reference-number").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.case-reference-number.title"))
        result.body should include(htmlEscapedMessage("view.case-reference-number.heading"))
        journey.getState shouldBe EnterCaseReferenceNumber()
      }
    }

    "POST /add/case-reference-number" should {
      "sumbit case reference number and show next page" in {
        journey.setState(EnterCaseReferenceNumber())
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "caseReferenceNumber" -> "PC12010081330XGBNZJO04"
        )

        val result = await(request("/add/case-reference-number").post(payload))

        result.status shouldBe 200
        journey.getState shouldBe SelectTypeOfAmendment(
          AmendCaseModel(caseReferenceNumber = Some("PC12010081330XGBNZJO04"))
        )
      }
    }

    "GET /add/type-of-amendment" should {
      "show select type of amendment page" in {
        val state = SelectTypeOfAmendment(
          AmendCaseModel(caseReferenceNumber = Some("PC12010081330XGBNZJO04"))
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/add/type-of-amendment").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.type-of-amendment.title"))
        result.body should include(htmlEscapedMessage("view.type-of-amendment.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /add/type-of-amendment" should {
      "submit type of amendment choice and show next page" in {
        journey.setState(
          SelectTypeOfAmendment(
            AmendCaseModel(caseReferenceNumber = Some("PC12010081330XGBNZJO04"))
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val payload = Map(
          "typeOfAmendment" -> "WriteResponse"
        )

        val result = await(request("/add/type-of-amendment").post(payload))

        result.status shouldBe 200
        journey.getState shouldBe EnterResponseText(
          AmendCaseModel(
            caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
            typeOfAmendment = Some(TypeOfAmendment.WriteResponse)
          )
        )
      }
    }

    "GET /add/write-response" should {
      "show write response page" in {
        val state = EnterResponseText(
          AmendCaseModel(
            caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
            typeOfAmendment = Some(TypeOfAmendment.WriteResponse)
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/add/write-response").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.write-response-text.title"))
        result.body should include(htmlEscapedMessage("view.write-response-text.heading"))
        journey.getState shouldBe state
      }
    }

    "POST /add/write-response" should {
      "submit type of amendment choice and show next page" in {
        val model = AmendCaseModel(
          caseReferenceNumber = Some("PC12010081330XGBNZJO05"),
          typeOfAmendment = Some(TypeOfAmendment.WriteResponse)
        )
        journey.setState(
          EnterResponseText(
            model
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val text = Random.alphanumeric.take(1000).mkString

        val payload = Map(
          "responseText" -> text
        )

        val result = await(request("/add/write-response").post(payload))

        result.status shouldBe 200
        journey.getState shouldBe AmendCaseSummary(model.copy(responseText = Some(text)))
      }
    }

    "POST /add/amend-case" should {
      "call the backend API to amend the case when answers are complete" in {
        val bytes = Array.ofDim[Byte](1024 * 1024)
        Random.nextBytes(bytes)
        val upscanUrl = stubForFileDownload(200, bytes, "test.pdf")
        val text = Random.alphanumeric.take(1000).mkString
        val fullAmendCaseStateModel = AmendCaseModel(
          caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
          typeOfAmendment = Some(TypeOfAmendment.WriteResponseAndUploadDocuments),
          responseText = Some(text),
          fileUploads = Some(
            FileUploads(files =
              Seq(
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c"),
                FileUpload.Accepted(
                  Nonce.Any,
                  Timestamp.Any,
                  "f029444f-415c-4dec-9cf2-36774ec63ab8",
                  upscanUrl,
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                )
              )
            )
          )
        )
        journey.setState(AmendCaseSummary(fullAmendCaseStateModel))
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        givenUpdateCaseApiRequestSucceeds(
          "PC12010081330XGBNZJO04",
          "WriteResponseAndUploadDocuments",
          text
        )

        val result = await(request("/add/amend-case").post(""))

        result.status shouldBe 200
        journey.getState should beState(
          AmendCaseConfirmation(
            Seq(
              UploadedFile(
                "f029444f-415c-4dec-9cf2-36774ec63ab8",
                upscanUrl,
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              )
            ),
            fullAmendCaseStateModel,
            TraderServicesResult(
              "PC12010081330XGBNZJO04",
              generatedAt,
              List(
                FileTransferResult(
                  "foo1",
                  true,
                  201,
                  LocalDateTime.parse("2021-04-18T12:07:36")
                )
              )
            )
          )
        )
      }

      "display missing information view if answers are incomplete" in {
        val text = Random.alphanumeric.take(1000).mkString
        val incompleteAmendCaseStateModel = AmendCaseModel(
          caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
          typeOfAmendment = Some(TypeOfAmendment.WriteResponseAndUploadDocuments),
          responseText = Some(text),
          fileUploads = None
        )
        journey.setState(AmendCaseSummary(incompleteAmendCaseStateModel))
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        givenUpdateCaseApiRequestSucceeds(
          "PC12010081330XGBNZJO04",
          "WriteResponseAndUploadDocuments",
          text
        )

        val result = await(request("/add/amend-case").post(""))

        result.status shouldBe 200
        journey.getState should beState(
          AmendCaseMissingInformationError(incompleteAmendCaseStateModel)
        )
      }

      "display case already submitted page if the amend case was already submitted" in {
        val state = AmendCaseAlreadySubmitted
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/add/case-already-submitted").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedMessage("view.case-already-submitted.heading"))

      }
    }

    "GET /add/confirmation/receipt" should {
      "download the confirmation receipt" in {
        val state = AmendCaseConfirmation(
          Seq(
            UploadedFile(
              "foo-bar-ref-1",
              "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
              ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              "test.pdf",
              "application/pdf",
              Some(4567890)
            )
          ),
          AmendCaseModel(
            caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
            responseText = Some("foo bar"),
            typeOfAmendment = Some(TypeOfAmendment.WriteResponseAndUploadDocuments)
          ),
          TraderServicesResult("PC12010081330XGBNZJO04", generatedAt)
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        WireMock.stubFor(
          WireMock
            .get(WireMock.urlEqualTo("/send-documents-for-customs-check/assets/stylesheets/download-receipt.css"))
            .willReturn(WireMock.aResponse.withBody(""))
        )

        val result = await(request("/add/confirmation/receipt").get)

        result.status shouldBe 200
        result.header("Content-Disposition") shouldBe Some(
          """attachment; filename="Document_receipt_PC12010081330XGBNZJO04.html""""
        )

        result.body should include(htmlEscapedMessage("view.amend-case-confirmation.heading"))
        result.body should include(
          s"${htmlEscapedMessage("receipt.documentsReceivedOn", generatedAt.ddMMYYYYAtTimeFormat)}"
        )

        journey.getState shouldBe state
      }
    }

    "GET /add/confirmation/receipt/pdf/test.pdf" should {
      "download the confirmation receipt as pdf" in {
        val state = AmendCaseConfirmation(
          Seq(
            UploadedFile(
              "foo-bar-ref-1",
              "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
              ZonedDateTime.parse("2018-04-24T09:30:00Z"),
              "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
              "test.pdf",
              "application/pdf",
              Some(4567890)
            )
          ),
          AmendCaseModel(
            caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
            responseText = Some("foo bar"),
            typeOfAmendment = Some(TypeOfAmendment.WriteResponseAndUploadDocuments)
          ),
          TraderServicesResult("PC12010081330XGBNZJO04", generatedAt)
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        WireMock.stubFor(
          WireMock
            .get(WireMock.urlEqualTo("/send-documents-for-customs-check/assets/stylesheets/download-receipt.css"))
            .willReturn(WireMock.aResponse.withBody(""))
        )

        val pdfContent = Array.ofDim[Byte](7777)
        Random.nextBytes(pdfContent)
        givenPdfGenerationSucceeds(pdfContent)

        val result = await(request("/add/confirmation/receipt/pdf/test.pdf").get)

        result.status shouldBe 200
        result.header("Content-Type") shouldBe Some("application/pdf")
        result.header("Content-Disposition") shouldBe Some(
          """attachment; filename="Document_receipt_PC12010081330XGBNZJO04.pdf""""
        )

        result.bodyAsBytes.toArray shouldBe pdfContent

        journey.getState shouldBe state
      }
    }

    "GET /add/check-your-answers" should {
      "show the amendment review page with both uploaded files and additional information section from WriteResponseAndUploadDocuments mode" in {
        val bytes = Array.ofDim[Byte](1024 * 1024)

        val upscanUrl = stubForFileDownload(200, bytes, "test1.png")

        val fullAmendCaseStateModel = AmendCaseModel(
          caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
          typeOfAmendment = Some(TypeOfAmendment.WriteResponseAndUploadDocuments),
          responseText = Some(Random.alphanumeric.take(1000).mkString),
          fileUploads = Some(
            FileUploads(files =
              Seq(
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c"),
                FileUpload.Accepted(
                  Nonce.Any,
                  Timestamp.Any,
                  "f029444f-415c-4dec-9cf2-36774ec63ab8",
                  upscanUrl,
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                )
              )
            )
          )
        )
        val state = AmendCaseSummary(fullAmendCaseStateModel)
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/add/check-your-answers").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.amend-case.summary.title"))
        result.body should include(htmlEscapedMessage("view.amend-case.summary.caseReferenceNumber"))
        result.body should include(htmlEscapedMessage("view.amend-case.summary.additionalInfo.message"))
        result.body should include(htmlEscapedMessage("view.amend-case.summary.documents.heading"))
        journey.getState shouldBe state
      }
      "show the amendment review page with only additional information section from WriteResponse mode" in {
        val model = AmendCaseModel(
          caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
          typeOfAmendment = Some(TypeOfAmendment.WriteResponse),
          responseText = Some(Random.alphanumeric.take(1000).mkString),
          fileUploads = None
        )
        val state = AmendCaseSummary(model)
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/add/check-your-answers").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.amend-case.summary.title"))
        result.body should include(htmlEscapedMessage("view.amend-case.summary.caseReferenceNumber"))
        result.body should include(htmlEscapedMessage("view.amend-case.summary.additionalInfo.message"))
        result.body should not include (htmlEscapedMessage("view.amend-case.summary.documents.heading"))
        journey.getState shouldBe state
      }
      "show the amendment review page with only uploaded files section from UploadDocuments mode" in {
        val bytes = Array.ofDim[Byte](1024 * 1024)

        val upscanUrl = stubForFileDownload(200, bytes, "test1.png")

        val model = AmendCaseModel(
          caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
          typeOfAmendment = Some(TypeOfAmendment.UploadDocuments),
          responseText = None,
          fileUploads = Some(
            FileUploads(files =
              Seq(
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c"),
                FileUpload.Accepted(
                  Nonce.Any,
                  Timestamp.Any,
                  "f029444f-415c-4dec-9cf2-36774ec63ab8",
                  upscanUrl,
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf",
                  Some(4567890)
                )
              )
            )
          )
        )
        val state = AmendCaseSummary(model)
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/add/check-your-answers").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.amend-case.summary.title"))
        result.body should include(htmlEscapedMessage("view.amend-case.summary.caseReferenceNumber"))
        result.body should not include (htmlEscapedMessage("view.amend-case.summary.additionalInfo.message"))
        result.body should include(htmlEscapedMessage("view.amend-case.summary.documents.heading"))
        journey.getState shouldBe state
      }
    }

    "GET /add/upload-files" should {
      "show the upload multiple files page" in {

        val state = UploadMultipleFiles(
          exampleAmendCaseModel,
          fileUploads = FileUploads()
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/add/upload-files").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-multiple-files.title"))
        result.body should include(htmlEscapedMessage("view.upload-multiple-files.heading"))
        journey.getState shouldBe state
      }

      "retreat from summary to the upload multiple files page" in {

        val state = AmendCaseSummary(exampleAmendCaseModel)
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/add/upload-files").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-multiple-files.title"))
        result.body should include(htmlEscapedMessage("view.upload-multiple-files.heading"))
        journey.getState shouldBe UploadMultipleFiles(
          exampleAmendCaseModel,
          fileUploads = FileUploads()
        )
      }

    }

    "POST /add/upload-files/initialise/:uploadId" should {
      "initialise first file upload" in {

        val state = UploadMultipleFiles(
          exampleAmendCaseModel,
          fileUploads = FileUploads()
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val callbackUrl =
          appConfig.baseInternalCallbackUrl + s"/internal/callback-from-upscan/add/journey/${SHA256
              .compute(journeyId.value)}"
        givenUpscanInitiateSucceeds(callbackUrl)

        val result = await(request("/add/upload-files/initialise/001").post(""))

        result.status shouldBe 200
        val json = result.body[JsValue]
        (json \ "upscanReference").as[String] shouldBe "11370e18-6e24-453e-b45a-76d3e32ea33d"
        (json \ "uploadId").as[String] shouldBe "001"
        (json \ "uploadRequest").as[JsObject] shouldBe Json.obj(
          "href" -> "https://bucketName.s3.eu-west-2.amazonaws.com",
          "fields" -> Json.obj(
            "Content-Type"            -> "application/xml",
            "acl"                     -> "private",
            "key"                     -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
            "policy"                  -> "xxxxxxxx==",
            "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
            "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
            "x-amz-date"              -> "yyyyMMddThhmmssZ",
            "x-amz-meta-callback-url" -> callbackUrl,
            "x-amz-signature"         -> "xxxx",
            "success_action_redirect" -> "https://myservice.com/nextPage",
            "error_action_redirect"   -> "https://myservice.com/errorPage"
          )
        )

        journey.getState shouldBe
          UploadMultipleFiles(
            exampleAmendCaseModel,
            fileUploads = FileUploads(files =
              Seq(
                FileUpload.Initiated(
                  Nonce.Any,
                  Timestamp.Any,
                  "11370e18-6e24-453e-b45a-76d3e32ea33d",
                  uploadId = Some("001"),
                  uploadRequest = Some(
                    UploadRequest(
                      href = "https://bucketName.s3.eu-west-2.amazonaws.com",
                      fields = Map(
                        "Content-Type"            -> "application/xml",
                        "acl"                     -> "private",
                        "key"                     -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
                        "policy"                  -> "xxxxxxxx==",
                        "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
                        "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
                        "x-amz-date"              -> "yyyyMMddThhmmssZ",
                        "x-amz-meta-callback-url" -> callbackUrl,
                        "x-amz-signature"         -> "xxxx",
                        "success_action_redirect" -> "https://myservice.com/nextPage",
                        "error_action_redirect"   -> "https://myservice.com/errorPage"
                      )
                    )
                  )
                )
              )
            )
          )
      }

      "initialise next file upload" in {
        val state = UploadMultipleFiles(
          exampleAmendCaseModel,
          fileUploads = FileUploads(
            Seq(FileUpload.Posted(Nonce.Any, Timestamp.Any, "23370e18-6e24-453e-b45a-76d3e32ea389"))
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val callbackUrl =
          appConfig.baseInternalCallbackUrl + s"/internal/callback-from-upscan/add/journey/${SHA256
              .compute(journeyId.value)}"
        givenUpscanInitiateSucceeds(callbackUrl)

        val result = await(request("/add/upload-files/initialise/002").post(""))

        result.status shouldBe 200
        val json = result.body[JsValue]
        (json \ "upscanReference").as[String] shouldBe "11370e18-6e24-453e-b45a-76d3e32ea33d"
        (json \ "uploadId").as[String] shouldBe "002"
        (json \ "uploadRequest").as[JsObject] shouldBe Json.obj(
          "href" -> "https://bucketName.s3.eu-west-2.amazonaws.com",
          "fields" -> Json.obj(
            "Content-Type"            -> "application/xml",
            "acl"                     -> "private",
            "key"                     -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
            "policy"                  -> "xxxxxxxx==",
            "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
            "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
            "x-amz-date"              -> "yyyyMMddThhmmssZ",
            "x-amz-meta-callback-url" -> callbackUrl,
            "x-amz-signature"         -> "xxxx",
            "success_action_redirect" -> "https://myservice.com/nextPage",
            "error_action_redirect"   -> "https://myservice.com/errorPage"
          )
        )

        journey.getState shouldBe
          UploadMultipleFiles(
            exampleAmendCaseModel,
            fileUploads = FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "23370e18-6e24-453e-b45a-76d3e32ea389"),
                FileUpload.Initiated(
                  Nonce.Any,
                  Timestamp.Any,
                  "11370e18-6e24-453e-b45a-76d3e32ea33d",
                  uploadId = Some("002"),
                  uploadRequest = Some(
                    UploadRequest(
                      href = "https://bucketName.s3.eu-west-2.amazonaws.com",
                      fields = Map(
                        "Content-Type"            -> "application/xml",
                        "acl"                     -> "private",
                        "key"                     -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
                        "policy"                  -> "xxxxxxxx==",
                        "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
                        "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
                        "x-amz-date"              -> "yyyyMMddThhmmssZ",
                        "x-amz-meta-callback-url" -> callbackUrl,
                        "x-amz-signature"         -> "xxxx",
                        "success_action_redirect" -> "https://myservice.com/nextPage",
                        "error_action_redirect"   -> "https://myservice.com/errorPage"
                      )
                    )
                  )
                )
              )
            )
          )
      }
    }

    "GET /add/file-upload" should {
      "show the upload first document page" in {
        val state = AmendCaseSummary(exampleAmendCaseModel)
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val callbackUrl =
          appConfig.baseInternalCallbackUrl + s"/internal/callback-from-upscan/add/journey/${SHA256
              .compute(journeyId.value)}"
        givenUpscanInitiateSucceeds(callbackUrl)

        val result = await(request("/add/file-upload").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-file.first.title"))
        result.body should include(htmlEscapedMessage("view.upload-file.first.heading"))
        journey.getState shouldBe UploadFile(
          exampleAmendCaseModel,
          reference = "11370e18-6e24-453e-b45a-76d3e32ea33d",
          uploadRequest = UploadRequest(
            href = "https://bucketName.s3.eu-west-2.amazonaws.com",
            fields = Map(
              "Content-Type"            -> "application/xml",
              "acl"                     -> "private",
              "key"                     -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
              "policy"                  -> "xxxxxxxx==",
              "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
              "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
              "x-amz-date"              -> "yyyyMMddThhmmssZ",
              "x-amz-meta-callback-url" -> callbackUrl,
              "x-amz-signature"         -> "xxxx",
              "success_action_redirect" -> "https://myservice.com/nextPage",
              "error_action_redirect"   -> "https://myservice.com/errorPage"
            )
          ),
          fileUploads = FileUploads(files =
            Seq(FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"))
          )
        )
      }
    }

    "GET /add/file-verification" should {
      "display waiting for file verification page" in {
        journey.setState(
          UploadFile(
            exampleAmendCaseModel,
            "2b72fe99-8adf-4edb-865e-622ae710f77c",
            UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            FileUploads(files =
              Seq(
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/add/file-verification").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-file.waiting"))
        result.body should include(htmlEscapedMessage("view.upload-file.waiting"))

        journey.getState shouldBe (
          WaitingForFileVerification(
            exampleAmendCaseModel,
            "2b72fe99-8adf-4edb-865e-622ae710f77c",
            UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c"),
            FileUploads(files =
              Seq(
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
      }
    }

    "GET /add/journey/:journeyId/file-rejected" should {
      "set current file upload status as rejected and return 204 NoContent" in {
        journey.setState(
          UploadFile(
            exampleAmendCaseModel,
            "11370e18-6e24-453e-b45a-76d3e32ea33d",
            UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            FileUploads(files =
              Seq(
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result1 =
          await(
            requestWithoutJourneyId(
              s"/add/journey/${SHA256.compute(journeyId.value)}/file-rejected?key=11370e18-6e24-453e-b45a-76d3e32ea33d&errorCode=ABC123&errorMessage=ABC+123"
            ).get()
          )

        result1.status shouldBe 204
        result1.body.isEmpty shouldBe true
        journey.getState shouldBe (
          UploadFile(
            exampleAmendCaseModel,
            "11370e18-6e24-453e-b45a-76d3e32ea33d",
            UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            FileUploads(files =
              Seq(
                FileUpload.Rejected(
                  Nonce.Any,
                  Timestamp.Any,
                  "11370e18-6e24-453e-b45a-76d3e32ea33d",
                  S3UploadError(
                    key = "11370e18-6e24-453e-b45a-76d3e32ea33d",
                    errorCode = "ABC123",
                    errorMessage = "ABC 123"
                  )
                ),
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            ),
            Some(
              FileTransmissionFailed(
                S3UploadError("11370e18-6e24-453e-b45a-76d3e32ea33d", "ABC123", "ABC 123", None, None)
              )
            )
          )
        )
      }
    }

    "GET /add/journey/:journeyId/file-verification" should {
      "set current file upload status as posted and return 204 NoContent" in {
        journey.setState(
          UploadFile(
            exampleAmendCaseModel,
            "11370e18-6e24-453e-b45a-76d3e32ea33d",
            UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            FileUploads(files =
              Seq(
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result1 =
          await(requestWithoutJourneyId(s"/add/journey/${SHA256.compute(journeyId.value)}/file-verification").get())

        result1.status shouldBe 202
        result1.body.isEmpty shouldBe true
        journey.getState shouldBe (
          WaitingForFileVerification(
            exampleAmendCaseModel,
            "11370e18-6e24-453e-b45a-76d3e32ea33d",
            UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            FileUpload.Posted(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
      }
    }

    "GET /add/file-verification/:reference/status" should {
      "return file verification status" in {
        val state = FileUploaded(
          exampleAmendCaseModel,
          FileUploads(files =
            Seq(
              FileUpload.Initiated(
                Nonce.Any,
                Timestamp.Any,
                "11370e18-6e24-453e-b45a-76d3e32ea33d",
                uploadRequest =
                  Some(UploadRequest(href = "https://s3.amazonaws.com/bucket/123abc", fields = Map("foo1" -> "bar1")))
              ),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c"),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "f029444f-415c-4dec-9cf2-36774ec63ab8",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              ),
              FileUpload.Failed(
                Nonce.Any,
                Timestamp.Any,
                "4b1e15a4-4152-4328-9448-4924d9aee6e2",
                UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
              ),
              FileUpload.Rejected(
                Nonce.Any,
                Timestamp.Any,
                "4b1e15a4-4152-4328-9448-4924d9aee6e3",
                details = S3UploadError("key", "errorCode", "Invalid file type.")
              ),
              FileUpload.Duplicate(
                Nonce.Any,
                Timestamp.Any,
                "4b1e15a4-4152-4328-9448-4924d9aee6e4",
                checksum = "0" * 64,
                existingFileName = "test.pdf",
                duplicateFileName = "test1.png"
              )
            )
          ),
          acknowledged = false
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result1 =
          await(
            request("/add/file-verification/11370e18-6e24-453e-b45a-76d3e32ea33d/status")
              .get()
          )
        result1.status shouldBe 200
        result1.body shouldBe """{"reference":"11370e18-6e24-453e-b45a-76d3e32ea33d","fileStatus":"NOT_UPLOADED","uploadRequest":{"href":"https://s3.amazonaws.com/bucket/123abc","fields":{"foo1":"bar1"}}}"""
        journey.getState shouldBe state

        val result2 =
          await(request("/add/file-verification/2b72fe99-8adf-4edb-865e-622ae710f77c/status").get())
        result2.status shouldBe 200
        result2.body shouldBe """{"reference":"2b72fe99-8adf-4edb-865e-622ae710f77c","fileStatus":"WAITING"}"""
        journey.getState shouldBe state

        val result3 =
          await(request("/add/file-verification/f029444f-415c-4dec-9cf2-36774ec63ab8/status").get())
        result3.status shouldBe 200
        result3.body shouldBe """{"reference":"f029444f-415c-4dec-9cf2-36774ec63ab8","fileStatus":"ACCEPTED","fileMimeType":"application/pdf","fileName":"test.pdf","fileSize":4567890,"previewUrl":"/send-documents-for-customs-check/add/file-uploaded/f029444f-415c-4dec-9cf2-36774ec63ab8/test.pdf"}"""
        journey.getState shouldBe state

        val result4 =
          await(request("/add/file-verification/4b1e15a4-4152-4328-9448-4924d9aee6e2/status").get())
        result4.status shouldBe 200
        result4.body shouldBe """{"reference":"4b1e15a4-4152-4328-9448-4924d9aee6e2","fileStatus":"FAILED","errorMessage":"The selected file contains a virus - upload a different one"}"""
        journey.getState shouldBe state

        val result5 =
          await(request("/add/file-verification/f0e317f5-d394-42cc-93f8-e89f4fc0114c/status").get())
        result5.status shouldBe 404
        journey.getState shouldBe state

        val result6 =
          await(request("/add/file-verification/4b1e15a4-4152-4328-9448-4924d9aee6e3/status").get())
        result6.status shouldBe 200
        result6.body shouldBe """{"reference":"4b1e15a4-4152-4328-9448-4924d9aee6e3","fileStatus":"REJECTED","errorMessage":"The selected file could not be uploaded"}"""
        journey.getState shouldBe state

        val result7 =
          await(request("/add/file-verification/4b1e15a4-4152-4328-9448-4924d9aee6e4/status").get())
        result7.status shouldBe 200
        result7.body shouldBe """{"reference":"4b1e15a4-4152-4328-9448-4924d9aee6e4","fileStatus":"DUPLICATE","errorMessage":"The selected file has already been uploaded"}"""
        journey.getState shouldBe state
      }
    }

    "GET /add/file-uploaded" should {
      "show uploaded singular file view" in {
        val state = FileUploaded(
          exampleAmendCaseModel,
          fileUploads = FileUploads(files =
            Seq(
              TestData.acceptedFileUpload
            )
          ),
          true
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/add/file-uploaded").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.file-uploaded.singular.title", "1"))
        result.body should include(htmlEscapedMessage("view.file-uploaded.singular.heading", "1"))
        journey.getState shouldBe state
      }

      "show uploaded plural file view" in {
        val state = FileUploaded(
          exampleAmendCaseModel,
          fileUploads = FileUploads(files =
            Seq(
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "11370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test2.pdf",
                "application/pdf",
                Some(5234567)
              ),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "22370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test1.png",
                "image/png",
                Some(4567890)
              )
            )
          ),
          true
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/add/file-uploaded").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.file-uploaded.plural.title", "2"))
        result.body should include(htmlEscapedMessage("view.file-uploaded.plural.heading", "2"))
        journey.getState shouldBe state
      }

      "show file upload summary view" in {
        val state = FileUploaded(
          exampleAmendCaseModel,
          fileUploads = FileUploads(files = for (i <- 1 to 10) yield TestData.acceptedFileUpload),
          true
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/add/file-uploaded").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.file-uploaded.plural.title", "10"))
        result.body should include(htmlEscapedMessage("view.file-uploaded.plural.heading", "10"))
        journey.getState shouldBe state
      }
    }

    "POST /add/file-uploaded" should {

      val FILES_LIMIT = 10

      "show upload a file view when yes and number of files below the limit" in {
        val fileUploads = FileUploads(files = for (i <- 1 until FILES_LIMIT) yield TestData.acceptedFileUpload)
        val state = FileUploaded(
          exampleAmendCaseModel,
          fileUploads
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val callbackUrl =
          appConfig.baseInternalCallbackUrl + s"/internal/callback-from-upscan/add/journey/${SHA256
              .compute(journeyId.value)}"
        givenUpscanInitiateSucceeds(callbackUrl)

        val result = await(
          request("/add/file-uploaded")
            .post(Map("uploadAnotherFile" -> "yes"))
        )

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-file.next.title"))
        result.body should include(htmlEscapedMessage("view.upload-file.next.heading"))
        journey.getState shouldBe UploadFile(
          exampleAmendCaseModel,
          reference = "11370e18-6e24-453e-b45a-76d3e32ea33d",
          uploadRequest = UploadRequest(
            href = "https://bucketName.s3.eu-west-2.amazonaws.com",
            fields = Map(
              "Content-Type"            -> "application/xml",
              "acl"                     -> "private",
              "key"                     -> "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
              "policy"                  -> "xxxxxxxx==",
              "x-amz-algorithm"         -> "AWS4-HMAC-SHA256",
              "x-amz-credential"        -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
              "x-amz-date"              -> "yyyyMMddThhmmssZ",
              "x-amz-meta-callback-url" -> callbackUrl,
              "x-amz-signature"         -> "xxxx",
              "success_action_redirect" -> "https://myservice.com/nextPage",
              "error_action_redirect"   -> "https://myservice.com/errorPage"
            )
          ),
          fileUploads = FileUploads(files =
            fileUploads.files ++
              Seq(FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"))
          )
        )
      }

      "show check-your-anwers page when yes and files number limit has been reached" in {
        val fileUploads = FileUploads(files = for (i <- 1 to FILES_LIMIT) yield TestData.acceptedFileUpload)
        val state = FileUploaded(
          exampleAmendCaseModel,
          fileUploads
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(
          request("/add/file-uploaded")
            .post(Map("uploadAnotherFile" -> "yes"))
        )

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.amend-case.summary.title"))
        result.body should include(htmlEscapedMessage("view.amend-case.summary.documents.heading"))
        result.body should include(routes.AmendCaseJourneyController.showFileUpload.url)
        journey.getState shouldBe AmendCaseSummary(exampleAmendCaseModel.copy(fileUploads = Some(fileUploads)))
      }

      "show check-your-anwers page when no and files number below the limit" in {
        val fileUploads = FileUploads(files = for (i <- 1 until FILES_LIMIT) yield TestData.acceptedFileUpload)
        val state = FileUploaded(
          exampleAmendCaseModel,
          fileUploads
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(
          request("/add/file-uploaded")
            .post(Map("uploadAnotherFile" -> "no"))
        )

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.amend-case.summary.title"))
        result.body should include(htmlEscapedMessage("view.amend-case.summary.documents.heading"))
        result.body should include(routes.AmendCaseJourneyController.showFileUpload.url)
        journey.getState shouldBe AmendCaseSummary(exampleAmendCaseModel.copy(fileUploads = Some(fileUploads)))
      }

      "show check-your-anwers page when no and files number above the limit" in {
        val fileUploads = FileUploads(files = for (i <- 1 to FILES_LIMIT) yield TestData.acceptedFileUpload)
        val state = FileUploaded(
          exampleAmendCaseModel,
          fileUploads
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(
          request("/add/file-uploaded")
            .post(Map("uploadAnotherFile" -> "no"))
        )

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.amend-case.summary.title"))
        result.body should include(htmlEscapedMessage("view.amend-case.summary.documents.heading"))
        result.body should include(routes.AmendCaseJourneyController.showFileUpload.url)
        journey.getState shouldBe AmendCaseSummary(exampleAmendCaseModel.copy(fileUploads = Some(fileUploads)))
      }
    }

    "GET /add/file-rejected" should {
      "show upload document again" in {
        journey.setState(
          UploadFile(
            exampleAmendCaseModel,
            "2b72fe99-8adf-4edb-865e-622ae710f77c",
            UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            FileUploads(files =
              Seq(
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(
          request(
            "/add/file-rejected?key=2b72fe99-8adf-4edb-865e-622ae710f77c&errorCode=EntityTooLarge&errorMessage=Entity+Too+Large"
          ).get()
        )

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-file.first.title"))
        result.body should include(htmlEscapedMessage("view.upload-file.first.heading"))
        journey.getState shouldBe UploadFile(
          exampleAmendCaseModel,
          "2b72fe99-8adf-4edb-865e-622ae710f77c",
          UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
            )
          ),
          Some(
            FileTransmissionFailed(
              S3UploadError("2b72fe99-8adf-4edb-865e-622ae710f77c", "EntityTooLarge", "Entity Too Large")
            )
          )
        )
      }
    }

    "POST /add/file-rejected" should {
      "mark file upload as rejected" in {
        journey.setState(
          UploadMultipleFiles(
            exampleAmendCaseModel,
            FileUploads(files =
              Seq(
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(
          request("/add/file-rejected").post(
            Json.obj(
              "key"          -> "2b72fe99-8adf-4edb-865e-622ae710f77c",
              "errorCode"    -> "EntityTooLarge",
              "errorMessage" -> "Entity Too Large"
            )
          )
        )

        result.status shouldBe 201

        journey.getState shouldBe UploadMultipleFiles(
          exampleAmendCaseModel,
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Rejected(
                Nonce.Any,
                Timestamp.Any,
                "2b72fe99-8adf-4edb-865e-622ae710f77c",
                S3UploadError("2b72fe99-8adf-4edb-865e-622ae710f77c", "EntityTooLarge", "Entity Too Large")
              )
            )
          )
        )
      }
    }

    "GET /add/file-uploaded/:reference/remove" should {
      "remove file from upload list by reference" in {
        val state = FileUploaded(
          exampleAmendCaseModel,
          fileUploads = FileUploads(files =
            Seq(
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "11370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test2.pdf",
                "application/pdf",
                Some(5234567)
              ),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "22370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test1.png",
                "image/png",
                Some(4567890)
              )
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/add/file-uploaded/11370e18-6e24-453e-b45a-76d3e32ea33d/remove").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.file-uploaded.singular.title", "1"))
        result.body should include(htmlEscapedMessage("view.file-uploaded.singular.heading", "1"))
        journey.getState shouldBe FileUploaded(
          exampleAmendCaseModel,
          fileUploads = FileUploads(files =
            Seq(
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "22370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test1.png",
                "image/png",
                Some(4567890)
              )
            )
          )
        )
      }
    }

    "POST /add/file-uploaded/:reference/remove" should {
      "remove file from upload list by reference" in {
        val state = UploadMultipleFiles(
          exampleAmendCaseModel,
          fileUploads = FileUploads(files =
            Seq(
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "11370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test2.pdf",
                "application/pdf",
                Some(5234567)
              ),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "22370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test1.png",
                "image/png",
                Some(4567890)
              )
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/add/file-uploaded/11370e18-6e24-453e-b45a-76d3e32ea33d/remove").post(""))

        result.status shouldBe 204

        journey.getState shouldBe UploadMultipleFiles(
          exampleAmendCaseModel,
          fileUploads = FileUploads(files =
            Seq(
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "22370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test1.png",
                "image/png",
                Some(4567890)
              )
            )
          )
        )
      }
    }

    "GET /add/file-uploaded/:reference" should {
      "stream the uploaded file content back if exists" in {
        val bytes = Array.ofDim[Byte](1024 * 1024)
        Random.nextBytes(bytes)
        val upscanUrl = stubForFileDownload(200, bytes, "test.pdf")

        val state = FileUploaded(
          exampleAmendCaseModel,
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c"),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "f029444f-415c-4dec-9cf2-36774ec63ab8",
                upscanUrl,
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              ),
              FileUpload.Failed(
                Nonce.Any,
                Timestamp.Any,
                "4b1e15a4-4152-4328-9448-4924d9aee6e2",
                UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
              )
            )
          ),
          acknowledged = false
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result =
          await(
            request("/add/file-uploaded/f029444f-415c-4dec-9cf2-36774ec63ab8/test.pdf")
              .get()
          )
        result.status shouldBe 200
        result.header("Content-Type") shouldBe Some("application/pdf")
        result.header("Content-Length") shouldBe Some(s"${bytes.length}")
        result.header("Content-Disposition") shouldBe Some("""inline; filename="test.pdf"; filename*=utf-8''test.pdf""")
        result.bodyAsBytes.toArray[Byte] shouldBe bytes
        journey.getState shouldBe state
      }

      "return error page if file does not exist" in {
        val upscanUrl = stubForFileDownloadFailure(404, "test.pdf")

        val state = FileUploaded(
          exampleAmendCaseModel,
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c"),
              FileUpload.Accepted(
                Nonce.Any,
                Timestamp.Any,
                "f029444f-415c-4dec-9cf2-36774ec63ab8",
                upscanUrl,
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf",
                Some(4567890)
              ),
              FileUpload.Failed(
                Nonce.Any,
                Timestamp.Any,
                "4b1e15a4-4152-4328-9448-4924d9aee6e2",
                UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
              )
            )
          ),
          acknowledged = false
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result =
          await(
            request("/add/file-uploaded/f029444f-415c-4dec-9cf2-36774ec63ab8/test.pdf")
              .get()
          )
        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("global.error.500.title"))
        result.body should include(htmlEscapedMessage("global.error.500.heading"))
        journey.getState shouldBe state
      }
    }

    "GET /add/journey/:journeyId/file-posted" should {
      "set current file upload status as posted and return 201 Created" in {
        journey.setState(
          UploadMultipleFiles(
            exampleAmendCaseModel,
            FileUploads(files =
              Seq(
                FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result =
          await(
            requestWithoutJourneyId(
              s"/add/journey/${SHA256.compute(journeyId.value)}/file-posted?key=11370e18-6e24-453e-b45a-76d3e32ea33d&bucket=foo"
            ).get()
          )

        result.status shouldBe 201
        result.body.isEmpty shouldBe true
        result.headerValues(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN) shouldBe Seq("*")
        journey.getState should beState(
          UploadMultipleFiles(
            exampleAmendCaseModel,
            FileUploads(files =
              Seq(
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
      }
    }

    "OPTIONS /add/journey/:journeyId/file-rejected" should {
      "return 201 with access control header" in {
        val result =
          await(
            request(s"/add/journey/${journeyId.value}/file-rejected")
              .options()
          )
        result.status shouldBe 201
        result.body.isEmpty shouldBe true
        result.headerValues(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN) shouldBe Seq("*")
      }
    }

    "OPTIONS /add/journey/:journeyId/file-posted" should {
      "return 201 with access control header" in {
        val result =
          await(
            request(s"/add/journey/${journeyId.value}/file-posted")
              .options()
          )
        result.status shouldBe 201
        result.body.isEmpty shouldBe true
        result.headerValues(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN) shouldBe Seq("*")
      }
    }

  }
}

trait AmendCaseJourneyISpecSetup extends ServerISpec with StateMatchers {

  implicit val journeyId: JourneyId = JourneyId()

  val exampleAmendCaseModel = AmendCaseModel(
    caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
    typeOfAmendment = Some(TypeOfAmendment.UploadDocuments)
  )

  implicit val hc: HeaderCarrier =
    HeaderCarrier(authorization = Some(Authorization(authToken)), sessionId = Some(SessionId(journeyId.value)))

  import play.api.i18n._
  implicit val messages: Messages = MessagesImpl(Lang("en"), app.injector.instanceOf[MessagesApi])

  lazy val controller = app.injector.instanceOf[AmendCaseJourneyController]

  trait TestMongoDBCachedAmendCaseJourneyService extends MongoDBCachedAmendCaseJourneyService

  // define test service capable of manipulating journey state
  lazy val journey = new support.TestJourneyService with AmendCaseJourneyService
  with EncryptedSessionCache[State, HeaderCarrier] {

    override lazy val actorSystem: ActorSystem = app.injector.instanceOf[ActorSystem]
    override lazy val cacheRepository = app.injector.instanceOf[CacheRepository]
    lazy val keyProvider: KeyProvider = KeyProvider(app.injector.instanceOf[Config])

    override lazy val keyProviderFromContext: HeaderCarrier => KeyProvider =
      hc => KeyProvider(keyProvider, None)

    override def getJourneyId(hc: HeaderCarrier): Option[String] = hc.sessionId.map(_.value).map(SHA256.compute)

    override val stateFormats: Format[State] = AmendCaseJourneyStateFormats.formats
    override val root: State = model.root
    override val default: State = root
  }

  final def fakeRequest(cookies: Cookie*)(implicit
    journeyId: JourneyId
  ): Request[AnyContent] =
    fakeRequest("GET", "/", cookies: _*)

  final def fakeRequest(method: String, path: String, cookies: Cookie*)(implicit
    journeyId: JourneyId
  ): Request[AnyContent] =
    FakeRequest(Call(method, path))
      .withCookies(cookies: _*)
      .withSession(journey.journeyKey -> journeyId.value, SessionKeys.authToken -> "Bearer XYZ")

  final def request(path: String, isInternalUrl: Boolean = false)(implicit
    journeyId: JourneyId
  ): StandaloneWSRequest = {
    val sessionCookie =
      sessionCookieBaker
        .encodeAsCookie(Session(Map(SessionKeys.sessionId -> journeyId.value, SessionKeys.authToken -> "Bearer XYZ")))

    val baseUrlPath = if (isInternalUrl) baseInternalUrl else baseUrl

    wsClient
      .url(s"$baseUrlPath$path")
      .withCookies(
        Seq(
          DefaultWSCookie(
            sessionCookie.name,
            sessionCookieCrypto.crypto.encrypt(PlainText(sessionCookie.value)).value
          ),
          DefaultWSCookie(SessionKeys.sessionId, journeyId.value)
        ): _*
      )
  }

  final def requestWithCookies(path: String, cookies: (String, String)*)(implicit
    journeyId: JourneyId
  ): StandaloneWSRequest = {
    val sessionCookie =
      sessionCookieBaker
        .encodeAsCookie(Session(Map(SessionKeys.sessionId -> journeyId.value, SessionKeys.authToken -> "Bearer XYZ")))

    wsClient
      .url(s"$baseUrl$path")
      .withCookies(
        (cookies.map(c => DefaultWSCookie(c._1, c._2)) :+ DefaultWSCookie(
          sessionCookie.name,
          sessionCookieCrypto.crypto.encrypt(PlainText(sessionCookie.value)).value
        )): _*
      )
  }
}
