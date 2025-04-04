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
          matchingJsonPath("callbackUrl", containing(callbackUrl))
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
