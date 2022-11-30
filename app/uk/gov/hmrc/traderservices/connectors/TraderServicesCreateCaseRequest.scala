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

import play.api.libs.json.Json
import uk.gov.hmrc.traderservices.models.{EntryDetails, QuestionsAnswers, UploadedFile}

/** @param entryDetails
  * @param questionsAnswers
  * @param uploadedFiles
  * @param eori
  *
  * @example
  *   { "entryDetails" : { "epu" : "123", "entryNumber" : "000000Z", "entryDate" : "2020-10-05" }, "questionsAnswers" :
  *   { "export" : { "requestType" : "New", "routeType" : "Route2", "freightType" : "Air", "vesselDetails" : {
  *   "vesselName" : "Foo Bar", "dateOfArrival" : "2020-10-19", "timeOfArrival" : "10:09:00" }, "contactInfo" : {
  *   "contactName" : "Bob", "contactEmail" : "name@somewhere.com", "contactNumber" : "01234567891" } } },
  *   "uploadedFiles" : [ { "downloadUrl" : "https://bucketName.s3.eu-west-2.amazonaws.com?1235676", "uploadTimestamp" :
  *   "2018-04-24T09:30:00Z", "checksum" : "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
  *   "fileName" : "test.pdf", "fileMimeType" : "application/pdf" } ], "eori" : "GB123456789012345" }
  */
case class TraderServicesCreateCaseRequest(
  entryDetails: EntryDetails,
  questionsAnswers: QuestionsAnswers,
  uploadedFiles: Seq[UploadedFile],
  eori: Option[String]
)

object TraderServicesCreateCaseRequest {
  implicit val formats = Json.format[TraderServicesCreateCaseRequest]
}
