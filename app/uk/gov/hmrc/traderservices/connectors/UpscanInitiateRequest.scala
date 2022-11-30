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

/** Request to Upscan Initiate. see: https://github.com/hmrc/upscan-initiate#post-upscanv2initiate
  *
  * @param callbackUrl
  *   (required) Url that will be called to report the outcome of file checking and upload, including retrieval details
  *   if successful. Notification format is detailed further down in this file. Must be https.
  * @param successRedirect
  *   (optional) Url to redirect to after file has been successfully uploaded
  * @param errorRedirect
  *   (optional) Url to redirect to if error encountered during upload
  * @param minimumFileSize
  *   (optional) Minimum file size (in Bytes). Default is 0
  * @param maximumFileSize
  *   (optional) Maximum file size (in Bytes). Cannot be greater than 100MB. Default is 100MB
  * @param expectedContentType
  *   (optional) MIME type describing the upload contents.
  */
case class UpscanInitiateRequest(
  callbackUrl: String,
  successRedirect: Option[String] = None,
  errorRedirect: Option[String] = None,
  minimumFileSize: Option[Int] = None,
  maximumFileSize: Option[Int] = None,
  expectedContentType: Option[String] = None
)

object UpscanInitiateRequest {
  implicit val formats: Format[UpscanInitiateRequest] =
    Json.format[UpscanInitiateRequest]
}
