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

/** Details of the S3 file upload success.
  *
  * The query parameter named key contains the globally unique file reference that was allocated by the initiate request
  * to identify the upload.
  *
  * @param key
  *   file upload reference
  * @param bucket
  *   upscan inbound bucket name, optional
  */
case class S3UploadSuccess(
  key: String,
  bucket: Option[String]
)

object S3UploadSuccess {
  implicit val formats: Format[S3UploadSuccess] =
    Json.format[S3UploadSuccess]
}
