package uk.gov.hmrc.traderservices.controllers

import play.api.libs.json.Format
import play.api.libs.ws.DefaultWSCookie
import play.api.mvc.Session
import uk.gov.hmrc.cache.repository.CacheMongoRepository
import uk.gov.hmrc.crypto.{ApplicationCrypto, PlainText}
import uk.gov.hmrc.traderservices.connectors.TraderServicesResult
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyStateFormats
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.services.{AmendCaseJourneyService, MongoDBCachedJourneyService}
import uk.gov.hmrc.traderservices.stubs.{TraderServicesApiStubs, UpscanInitiateStubs}
import uk.gov.hmrc.traderservices.support.{ServerISpec, TestJourneyService}
import uk.gov.hmrc.traderservices.views.CommonUtilsHelper.DateTimeUtilities

import java.time.{LocalDateTime, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import play.api.libs.json.JsValue
import play.api.libs.json.JsObject
import play.api.libs.json.Json

class AmendCaseJourneyISpec extends AmendCaseJourneyISpecSetup with TraderServicesApiStubs with UpscanInitiateStubs {

  import journey.model.FileUploadState._
  import journey.model.State._

  val dateTime = LocalDateTime.now()

  "AmendCaseJourneyController" when {

    "GET /send-documents-for-customs-check/add/case-reference-number" should {
      "show enter case reference number page" in {
        implicit val journeyId: JourneyId = JourneyId()
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/add/case-reference-number").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.case-reference-number.title"))
        result.body should include(htmlEscapedMessage("view.case-reference-number.heading"))
        journey.getState shouldBe EnterCaseReferenceNumber()
      }
    }

    "POST /send-documents-for-customs-check/add/case-reference-number" should {
      "sumbit case reference number and show next page" in {
        implicit val journeyId: JourneyId = JourneyId()
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

    "GET /send-documents-for-customs-check/add/type-of-amendment" should {
      "show select type of amendment page" in {
        implicit val journeyId: JourneyId = JourneyId()
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

    "POST /send-documents-for-customs-check/add/type-of-amendment" should {
      "submit type of amendment choice and show next page" in {
        implicit val journeyId: JourneyId = JourneyId()
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

    "GET /send-documents-for-customs-check/add/write-response" should {
      "show write response page" in {
        implicit val journeyId: JourneyId = JourneyId()
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

    "POST /send-documents-for-customs-check/add/write-response" should {
      "submit type of amendment choice and show next page" in {
        implicit val journeyId: JourneyId = JourneyId()
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

    "GET /add/upload-files" should {
      "show the upload multiple files page when in UploadDocuments mode" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = UploadMultipleFiles(
          AmendCaseModel(
            caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
            typeOfAmendment = Some(TypeOfAmendment.UploadDocuments)
          ),
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

      "show the upload multiple files page when in WriteResponseAndUploadDocuments mode" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = UploadMultipleFiles(
          AmendCaseModel(
            caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
            typeOfAmendment = Some(TypeOfAmendment.WriteResponseAndUploadDocuments),
            responseText = Some("abc")
          ),
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

      "retreat from summary to the upload multiple files when in UploadDocuments mode" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AmendCaseSummary(
          AmendCaseModel(
            caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
            typeOfAmendment = Some(TypeOfAmendment.UploadDocuments)
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/add/upload-files").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-multiple-files.title"))
        result.body should include(htmlEscapedMessage("view.upload-multiple-files.heading"))
        journey.getState shouldBe UploadMultipleFiles(
          AmendCaseModel(
            caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
            typeOfAmendment = Some(TypeOfAmendment.UploadDocuments)
          ),
          fileUploads = FileUploads()
        )
      }

      "retreat from summary to the upload multiple files when in WriteResponseAndUploadDocuments mode" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AmendCaseSummary(
          AmendCaseModel(
            caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
            typeOfAmendment = Some(TypeOfAmendment.WriteResponseAndUploadDocuments),
            responseText = Some("abc")
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/add/upload-files").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-multiple-files.title"))
        result.body should include(htmlEscapedMessage("view.upload-multiple-files.heading"))
        journey.getState shouldBe UploadMultipleFiles(
          AmendCaseModel(
            caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
            typeOfAmendment = Some(TypeOfAmendment.WriteResponseAndUploadDocuments),
            responseText = Some("abc")
          ),
          fileUploads = FileUploads()
        )
      }
    }

    "PUT /add/upload-files/initialise/:uploadId" should {
      "initialise first file upload" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = UploadMultipleFiles(
          AmendCaseModel(
            caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
            typeOfAmendment = Some(TypeOfAmendment.UploadDocuments),
            fileUploads = Some(FileUploads())
          ),
          fileUploads = FileUploads()
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val callbackUrl =
          appConfig.baseInternalCallbackUrl + s"/send-documents-for-customs-check/add/journey/${journeyId.value}/callback-from-upscan"
        givenUpscanInitiateSucceeds(callbackUrl)

        val result = await(request("/add/upload-files/initialise/001").put(""))

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
            AmendCaseModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.UploadDocuments),
              fileUploads = Some(FileUploads())
            ),
            fileUploads = FileUploads(files =
              Seq(
                FileUpload.Initiated(
                  1,
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
        implicit val journeyId: JourneyId = JourneyId()
        val state = UploadMultipleFiles(
          AmendCaseModel(
            caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
            typeOfAmendment = Some(TypeOfAmendment.UploadDocuments),
            fileUploads = Some(FileUploads(Seq(FileUpload.Posted(1, "23370e18-6e24-453e-b45a-76d3e32ea389"))))
          ),
          fileUploads = FileUploads(
            Seq(FileUpload.Posted(1, "23370e18-6e24-453e-b45a-76d3e32ea389"))
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val callbackUrl =
          appConfig.baseInternalCallbackUrl + s"/send-documents-for-customs-check/add/journey/${journeyId.value}/callback-from-upscan"
        givenUpscanInitiateSucceeds(callbackUrl)

        val result = await(request("/add/upload-files/initialise/002").put(""))

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
            AmendCaseModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.UploadDocuments),
              fileUploads = Some(FileUploads(Seq(FileUpload.Posted(1, "23370e18-6e24-453e-b45a-76d3e32ea389"))))
            ),
            fileUploads = FileUploads(files =
              Seq(
                FileUpload.Posted(1, "23370e18-6e24-453e-b45a-76d3e32ea389"),
                FileUpload.Initiated(
                  2,
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
        implicit val journeyId: JourneyId = JourneyId()
        val callbackUrl =
          appConfig.baseInternalCallbackUrl + s"/send-documents-for-customs-check/add/journey/${journeyId.value}/callback-from-upscan"
        val state = UploadFile(
          AmendCaseModel(
            caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
            typeOfAmendment = Some(TypeOfAmendment.UploadDocuments)
          ),
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
          fileUploads = FileUploads(files = Seq(FileUpload.Initiated(1, "11370e18-6e24-453e-b45a-76d3e32ea33d")))
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        givenUpscanInitiateSucceeds(callbackUrl)

        val result = await(request("/add/file-upload").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.upload-file.first.title"))
        result.body should include(htmlEscapedMessage("view.upload-file.first.heading"))
        journey.getState shouldBe state
      }
    }

    "GET /add/journey/:journeyId/file-rejected" should {
      "set current file upload status as rejected and return 204 NoContent" in {
        implicit val journeyId: JourneyId = JourneyId()

        journey.setState(
          UploadFile(
            AmendCaseModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.WriteResponse)
            ),
            "11370e18-6e24-453e-b45a-76d3e32ea33d",
            UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            FileUploads(files =
              Seq(
                FileUpload.Initiated(1, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(2, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result1 =
          await(
            requestWithoutJourneyId(
              s"/add/journey/${journeyId.value}/file-rejected?key=11370e18-6e24-453e-b45a-76d3e32ea33d&errorCode=ABC123&errorMessage=ABC+123"
            ).get()
          )

        result1.status shouldBe 204
        result1.body.isEmpty shouldBe true
        journey.getState shouldBe (
          UploadFile(
            AmendCaseModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.WriteResponse)
            ),
            "11370e18-6e24-453e-b45a-76d3e32ea33d",
            UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            FileUploads(files =
              Seq(
                FileUpload.Rejected(
                  1,
                  "11370e18-6e24-453e-b45a-76d3e32ea33d",
                  S3UploadError(
                    key = "11370e18-6e24-453e-b45a-76d3e32ea33d",
                    errorCode = "ABC123",
                    errorMessage = "ABC 123"
                  )
                ),
                FileUpload.Posted(2, "2b72fe99-8adf-4edb-865e-622ae710f77c")
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
        implicit val journeyId: JourneyId = JourneyId()
        journey.setState(
          UploadFile(
            AmendCaseModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.WriteResponse)
            ),
            "11370e18-6e24-453e-b45a-76d3e32ea33d",
            UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            FileUploads(files =
              Seq(
                FileUpload.Initiated(1, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(2, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result1 =
          await(requestWithoutJourneyId(s"/add/journey/${journeyId.value}/file-verification").get())

        result1.status shouldBe 202
        result1.body.isEmpty shouldBe true
        journey.getState shouldBe (
          WaitingForFileVerification(
            AmendCaseModel(
              caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
              typeOfAmendment = Some(TypeOfAmendment.WriteResponse)
            ),
            "11370e18-6e24-453e-b45a-76d3e32ea33d",
            UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
            FileUpload.Posted(1, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
            FileUploads(files =
              Seq(
                FileUpload.Posted(1, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(2, "2b72fe99-8adf-4edb-865e-622ae710f77c")
              )
            )
          )
        )
      }
    }

    "GET /add/file-verification/:reference/status" should {
      "return file verification status" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = FileUploaded(
          AmendCaseModel(
            caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
            typeOfAmendment = Some(TypeOfAmendment.WriteResponse)
          ),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(
                1,
                "11370e18-6e24-453e-b45a-76d3e32ea33d",
                uploadRequest =
                  Some(UploadRequest(href = "https://s3.amazonaws.com/bucket/123abc", fields = Map("foo1" -> "bar1")))
              ),
              FileUpload.Posted(2, "2b72fe99-8adf-4edb-865e-622ae710f77c"),
              FileUpload.Accepted(
                4,
                "f029444f-415c-4dec-9cf2-36774ec63ab8",
                "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf"
              ),
              FileUpload.Failed(
                3,
                "4b1e15a4-4152-4328-9448-4924d9aee6e2",
                UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "some reason")
              ),
              FileUpload.Rejected(
                5,
                "4b1e15a4-4152-4328-9448-4924d9aee6e3",
                details = S3UploadError("key", "errorCode", "Invalid file type.")
              ),
              FileUpload.Duplicate(
                6,
                "4b1e15a4-4152-4328-9448-4924d9aee6e4",
                checksum = "0" * 64,
                existingFileName = "test1.pdf",
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
        result3.body shouldBe """{"reference":"f029444f-415c-4dec-9cf2-36774ec63ab8","fileStatus":"ACCEPTED","fileMimeType":"application/pdf","fileName":"test.pdf","previewUrl":"/send-documents-for-customs-check/add/file-uploaded/f029444f-415c-4dec-9cf2-36774ec63ab8"}"""
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
        implicit val journeyId: JourneyId = JourneyId()
        val state = FileUploaded(
          AmendCaseModel(
            caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
            typeOfAmendment = Some(TypeOfAmendment.UploadDocuments)
          ),
          fileUploads = FileUploads(files =
            Seq(
              FileUpload.Accepted(
                1,
                "11370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf"
              )
            )
          )
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
        implicit val journeyId: JourneyId = JourneyId()
        val state = FileUploaded(
          AmendCaseModel(
            caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
            typeOfAmendment = Some(TypeOfAmendment.UploadDocuments)
          ),
          fileUploads = FileUploads(files =
            Seq(
              FileUpload.Accepted(
                1,
                "11370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test2.pdf",
                "application/pdf"
              ),
              FileUpload.Accepted(
                2,
                "22370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test1.png",
                "image/png"
              )
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/add/file-uploaded").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.file-uploaded.plural.title", "2"))
        result.body should include(htmlEscapedMessage("view.file-uploaded.plural.heading", "2"))
        journey.getState shouldBe state
      }
    }

    "GET /add/file-uploaded/:reference/remove" should {
      "remove file from upload list by reference" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = FileUploaded(
          AmendCaseModel(
            caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
            typeOfAmendment = Some(TypeOfAmendment.UploadDocuments)
          ),
          fileUploads = FileUploads(files =
            Seq(
              FileUpload.Accepted(
                1,
                "11370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test2.pdf",
                "application/pdf"
              ),
              FileUpload.Accepted(
                2,
                "22370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test1.png",
                "image/png"
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
          AmendCaseModel(
            caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
            typeOfAmendment = Some(TypeOfAmendment.UploadDocuments)
          ),
          fileUploads = FileUploads(files =
            Seq(
              FileUpload.Accepted(
                2,
                "22370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test1.png",
                "image/png"
              )
            )
          )
        )
      }
    }

    "PUT /add/file-uploaded/:reference/remove" should {
      "remove file from upload list by reference" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = UploadMultipleFiles(
          AmendCaseModel(
            caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
            typeOfAmendment = Some(TypeOfAmendment.UploadDocuments)
          ),
          fileUploads = FileUploads(files =
            Seq(
              FileUpload.Accepted(
                1,
                "11370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test2.pdf",
                "application/pdf"
              ),
              FileUpload.Accepted(
                2,
                "22370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test1.png",
                "image/png"
              )
            )
          )
        )
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/add/file-uploaded/11370e18-6e24-453e-b45a-76d3e32ea33d/remove").put(""))

        result.status shouldBe 204

        journey.getState shouldBe UploadMultipleFiles(
          AmendCaseModel(
            caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
            typeOfAmendment = Some(TypeOfAmendment.UploadDocuments)
          ),
          fileUploads = FileUploads(files =
            Seq(
              FileUpload.Accepted(
                2,
                "22370e18-6e24-453e-b45a-76d3e32ea33d",
                "https://s3.amazonaws.com/bucket/123",
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test1.png",
                "image/png"
              )
            )
          )
        )
      }
    }

    "GET /add/confirmation" should {
      "show confirmation page" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = AmendCaseConfirmation(TraderServicesResult("PC12010081330XGBNZJO04", generatedAt))
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))

        val result = await(request("/add/confirmation").get())

        result.status shouldBe 200
        result.body should include(htmlEscapedPageTitle("view.amend-case-confirmation.title"))
        result.body should include(htmlEscapedMessage("view.amend-case-confirmation.heading"))
        result.body should include(
          s"${htmlEscapedMessage("view.amend-case-confirmation.date")} ${generatedAt.ddMMYYYYAtTimeFormat}"
        )
        journey.getState shouldBe state
      }
    }

    "GET /add/file-uploaded/:reference" should {
      "stream the uploaded file content back if exists" in {
        implicit val journeyId: JourneyId = JourneyId()
        val bytes = Array.ofDim[Byte](1024 * 1024)
        Random.nextBytes(bytes)
        val upscanUrl = stubForFileDownload(200, bytes, "test1.png")
        val state = FileUploaded(
          AmendCaseModel(
            caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
            typeOfAmendment = Some(TypeOfAmendment.UploadDocuments)
          ),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(1, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Posted(2, "2b72fe99-8adf-4edb-865e-622ae710f77c"),
              FileUpload.Accepted(
                4,
                "f029444f-415c-4dec-9cf2-36774ec63ab8",
                upscanUrl,
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test1.png",
                "image/png"
              ),
              FileUpload.Failed(
                3,
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
            request("/add/file-uploaded/f029444f-415c-4dec-9cf2-36774ec63ab8")
              .get()
          )
        result.status shouldBe 200
        result.header("Content-Type") shouldBe Some("image/png")
        result.header("Content-Length") shouldBe Some(s"${bytes.length}")
        result.header("Content-Disposition") shouldBe Some("""inline; filename="test1.png"""")
        result.bodyAsBytes.toArray[Byte] shouldBe bytes
        journey.getState shouldBe state
      }

      "return 500 if file does not exist" in {
        implicit val journeyId: JourneyId = JourneyId()
        val upscanUrl = stubForFileDownloadFailure(404, "test.pdf")
        val state = FileUploaded(
          AmendCaseModel(
            caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
            typeOfAmendment = Some(TypeOfAmendment.UploadDocuments)
          ),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(1, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Posted(2, "2b72fe99-8adf-4edb-865e-622ae710f77c"),
              FileUpload.Accepted(
                4,
                "f029444f-415c-4dec-9cf2-36774ec63ab8",
                upscanUrl,
                ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                "test.pdf",
                "application/pdf"
              ),
              FileUpload.Failed(
                3,
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
            request("/add/file-uploaded/f029444f-415c-4dec-9cf2-36774ec63ab8")
              .get()
          )
        result.status shouldBe 500
        journey.getState shouldBe state
      }
    }

    "GET /add/check-your-answers" should {
      "show the amendment review page with both uploaded files and additional information section from WriteResponseAndUploadDocuments mode" in {
        val bytes = Array.ofDim[Byte](1024 * 1024)

        val upscanUrl = stubForFileDownload(200, bytes, "test1.png")
        implicit val journeyId: JourneyId = JourneyId()
        val fullAmendCaseStateModel = AmendCaseModel(
          caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
          typeOfAmendment = Some(TypeOfAmendment.WriteResponseAndUploadDocuments),
          responseText = Some(Random.alphanumeric.take(1000).mkString),
          fileUploads = Some(
            FileUploads(files =
              Seq(
                FileUpload.Initiated(1, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(2, "2b72fe99-8adf-4edb-865e-622ae710f77c"),
                FileUpload.Accepted(
                  4,
                  "f029444f-415c-4dec-9cf2-36774ec63ab8",
                  upscanUrl,
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
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
        implicit val journeyId: JourneyId = JourneyId()
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
        implicit val journeyId: JourneyId = JourneyId()
        val model = AmendCaseModel(
          caseReferenceNumber = Some("PC12010081330XGBNZJO04"),
          typeOfAmendment = Some(TypeOfAmendment.UploadDocuments),
          responseText = None,
          fileUploads = Some(
            FileUploads(files =
              Seq(
                FileUpload.Initiated(1, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
                FileUpload.Posted(2, "2b72fe99-8adf-4edb-865e-622ae710f77c"),
                FileUpload.Accepted(
                  4,
                  "f029444f-415c-4dec-9cf2-36774ec63ab8",
                  upscanUrl,
                  ZonedDateTime.parse("2018-04-24T09:30:00Z"),
                  "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
                  "test.pdf",
                  "application/pdf"
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
  }
}

trait AmendCaseJourneyISpecSetup extends ServerISpec {
  // define test service capable of manipulating journey state
  lazy val journey = new TestJourneyService[JourneyId]
    with AmendCaseJourneyService[JourneyId] with MongoDBCachedJourneyService[JourneyId] {

    override lazy val cacheMongoRepository = app.injector.instanceOf[CacheMongoRepository]
    override lazy val applicationCrypto = app.injector.instanceOf[ApplicationCrypto]

    override val stateFormats: Format[model.State] =
      AmendCaseJourneyStateFormats.formats

    override def getJourneyId(journeyId: JourneyId): Option[String] = Some(journeyId.value)
  }

  def request(path: String)(implicit journeyId: JourneyId) = {
    val sessionCookie = sessionCookieBaker.encodeAsCookie(Session(Map(journey.journeyKey -> journeyId.value)))

    wsClient
      .url(s"$baseUrl$path")
      .withCookies(
        DefaultWSCookie(sessionCookie.name, sessionCookieCrypto.crypto.encrypt(PlainText(sessionCookie.value)).value)
      )
  }
}
