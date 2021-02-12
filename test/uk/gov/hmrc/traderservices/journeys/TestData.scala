/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.traderservices.journeys

import uk.gov.hmrc.traderservices.models._
import java.time._
import scala.concurrent.Future
import uk.gov.hmrc.traderservices.connectors._
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.UpscanInitiateApi

trait TestData {

  val eoriNumber = Some("foo")
  val correlationId = "123"
  val generatedAt = java.time.LocalDateTime.of(2018, 12, 11, 10, 20, 0)

  val exportDeclarationDetails = DeclarationDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-09-23"))
  val importDeclarationDetails = DeclarationDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-09-23"))
  val invalidDeclarationDetails = DeclarationDetails(EPU(123), EntryNumber("0000000"), LocalDate.parse("2020-09-23"))

  val completeExportQuestionsAnswers = ExportQuestions(
    requestType = Some(ExportRequestType.New),
    routeType = Some(ExportRouteType.Route3),
    hasPriorityGoods = Some(true),
    priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
    freightType = Some(ExportFreightType.Air),
    vesselDetails =
      Some(VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))),
    contactInfo = Some(ExportContactInfo(contactEmail = "name@somewhere.com"))
  )

  val completeImportQuestionsAnswers = ImportQuestions(
    requestType = Some(ImportRequestType.New),
    routeType = Some(ImportRouteType.Route3),
    hasPriorityGoods = Some(true),
    priorityGoods = Some(ImportPriorityGoods.ExplosivesOrFireworks),
    hasALVS = Some(true),
    freightType = Some(ImportFreightType.Air),
    vesselDetails =
      Some(VesselDetails(Some("Foo"), Some(LocalDate.parse("2021-01-01")), Some(LocalTime.parse("00:00")))),
    contactInfo = Some(ImportContactInfo(contactEmail = "name@somewhere.com"))
  )

  val nonEmptyFileUploads = FileUploads(files =
    Seq(
      FileUpload.Accepted(
        Nonce(1),
        Timestamp.Any,
        "foo-bar-ref-1",
        "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
        ZonedDateTime.parse("2018-04-24T09:30:00Z"),
        "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
        "test.pdf",
        "application/pdf",
        Some(4567890)
      )
    )
  )

  val mockUpscanInitiate: UpscanInitiateApi = request =>
    Future.successful(
      UpscanInitiateResponse(
        reference = "foo-bar-ref",
        uploadRequest = someUploadRequest(request)
      )
    )

  val testUpscanRequest: String => UpscanInitiateRequest =
    nonce =>
      UpscanInitiateRequest(
        callbackUrl = "https://foo.bar/callback",
        successRedirect = Some("https://foo.bar/success"),
        errorRedirect = Some("https://foo.bar/failure"),
        minimumFileSize = Some(0),
        maximumFileSize = Some(10 * 1024 * 1024),
        expectedContentType = Some("image/jpeg,image/png")
      )

  def someUploadRequest(request: UpscanInitiateRequest) =
    UploadRequest(
      href = "https://s3.bucket",
      fields = Map(
        "callbackUrl"         -> request.callbackUrl,
        "successRedirect"     -> request.successRedirect.getOrElse(""),
        "errorRedirect"       -> request.errorRedirect.getOrElse(""),
        "minimumFileSize"     -> request.minimumFileSize.getOrElse(0).toString,
        "maximumFileSize"     -> request.maximumFileSize.getOrElse(0).toString,
        "expectedContentType" -> request.expectedContentType.getOrElse("")
      )
    )

}
