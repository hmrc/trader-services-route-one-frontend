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

package uk.gov.hmrc.traderservices.models

import play.api.libs.json.{Format, Json}

/** Details of the S3 file upload error are supplied as query parameters, with the names errorCode, errorMessage,
  * errorResource and errorRequestId.
  *
  * The query parameter named key contains the globally unique file reference that was allocated by the initiate request
  * to identify the upload.
  *
  * If a redirect URL is not set, the proxy responds with the failure status code. The details of the error along with
  * the key are available from the JSON body that has the following structure:
  *
  * @example
  *   { "key": "11370e18-6e24-453e-b45a-76d3e32ea33d", "errorCode": "NoSuchKey", "errorMessage": "The resource you
  *   requested does not exist", "errorResource": "/mybucket/myfoto.jpg", "errorRequestId": "4442587FB7D0A2F9" }
  *
  * @param key
  *   file upload reference
  * @param errorCode
  *   S3 error code as per https://docs.aws.amazon.com/AmazonS3/latest/API/ErrorResponses.html#ErrorCodeList
  */
case class S3UploadError(
  key: String,
  errorCode: String,
  errorMessage: String,
  errorRequestId: Option[String] = None,
  errorResource: Option[String] = None
)

object S3UploadError {
  implicit val formats: Format[S3UploadError] =
    Json.format[S3UploadError]
}
