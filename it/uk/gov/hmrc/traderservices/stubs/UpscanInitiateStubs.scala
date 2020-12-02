package uk.gov.hmrc.traderservices.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.traderservices.support.WireMockSupport
import uk.gov.hmrc.traderservices.models.UploadRequest

trait UpscanInitiateStubs {
  me: WireMockSupport =>

  val testUploadRequest: UploadRequest =
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
        "x-amz-meta-callback-url" -> "https://myservice.com/callback",
        "x-amz-signature"         -> "xxxx",
        "success_action_redirect" -> "https://myservice.com/nextPage",
        "error_action_redirect"   -> "https://myservice.com/errorPage"
      )
    )

  def givenUpscanInitiateSucceeds(callbackUrl: String): StubMapping =
    stubFor(
      post(urlEqualTo(s"/upscan/v2/initiate"))
        .withHeader("User-Agent", containing("trader-services-route-one-frontend"))
        .withHeader(HeaderNames.CONTENT_TYPE, containing("application/json"))
        .withRequestBody(
          equalToJson(
            s"""{
               |    "callbackUrl": "$callbackUrl"
               |}""".stripMargin,
            true,
            true
          )
        )
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(s"""{
                         |    "reference": "11370e18-6e24-453e-b45a-76d3e32ea33d",
                         |    "uploadRequest": {
                         |        "href": "https://bucketName.s3.eu-west-2.amazonaws.com",
                         |        "fields": {
                         |            "Content-Type": "application/xml",
                         |            "acl": "private",
                         |            "key": "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
                         |            "policy": "xxxxxxxx==",
                         |            "x-amz-algorithm": "AWS4-HMAC-SHA256",
                         |            "x-amz-credential": "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
                         |            "x-amz-date": "yyyyMMddThhmmssZ",
                         |            "x-amz-meta-callback-url": "$callbackUrl",
                         |            "x-amz-signature": "xxxx",
                         |            "success_action_redirect": "https://myservice.com/nextPage",
                         |            "error_action_redirect": "https://myservice.com/errorPage"
                         |        }
                         |    }
                         |}""".stripMargin)
        )
    )

}
