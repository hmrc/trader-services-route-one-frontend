/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.traderservices.models.UploadRequest

/** Response from Upscan Initiate. see: https://github.com/hmrc/upscan-initiate#post-upscanv2initiate
  *
  * @param reference
  *   Globally unique file reference for the upload. This reference can be used by the Upscan service team to view the
  *   progress and result of the journey through the different Upscan components. The consuming service can use this
  *   reference to correlate the subsequent upload result with this upscan initiation.
  * @param uploadRequest
  *   Pre-filled template for the upload of the file
  *
  * @example
  *   <pre> { "reference": "11370e18-6e24-453e-b45a-76d3e32ea33d", "uploadRequest": { "href":
  *   "https://xxxx/upscan-upload-proxy/bucketName", "fields": { "Content-Type": "application/xml", "acl": "private",
  *   "key": "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx", "policy": "xxxxxxxx==", "x-amz-algorithm": "AWS4-HMAC-SHA256",
  *   "x-amz-credential": "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request", "x-amz-date": "yyyyMMddThhmmssZ",
  *   "x-amz-meta-callback-url": "https://myservice.com/callback", "x-amz-signature": "xxxx", "success_action_redirect":
  *   "https://myservice.com/nextPage", "error_action_redirect": "https://myservice.com/errorPage" } } } </pre>
  */
case class UpscanInitiateResponse(
  reference: String,
  uploadRequest: UploadRequest
)

object UpscanInitiateResponse {

  implicit val formats: Format[UpscanInitiateResponse] =
    Json.format[UpscanInitiateResponse]
}
