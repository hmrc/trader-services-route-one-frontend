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

package uk.gov.hmrc.traderservices.support

import org.scalacheck.Gen
import uk.gov.hmrc.traderservices.models._

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import uk.gov.hmrc.traderservices.connectors.TraderServicesCreateCaseRequest
import java.time.LocalDateTime
import uk.gov.hmrc.traderservices.connectors.FileTransferResult
import uk.gov.hmrc.traderservices.connectors.TraderServicesResult
import uk.gov.hmrc.traderservices.connectors.TraderServicesCaseResponse
import uk.gov.hmrc.traderservices.connectors.ApiError
import uk.gov.hmrc.traderservices.connectors.TraderServicesUpdateCaseRequest

object Generators {

  final implicit class OptionExt[A](val value: Option[A]) extends AnyVal {
    def existsIn(set: Set[A]): Boolean =
      value.exists(set.contains)
  }

  final val upperCaseChar = Gen.oneOf("ABCDEFGHIJKLMNOPRSUWXYZ".toCharArray())

  final def nonEmptyString(maxSize: Int, noOfSpaces: Int = 0): Gen[String] =
    Gen
      .listOfN(
        noOfSpaces + 1,
        Gen
          .nonEmptyContainerOf[Array, Char](Gen.alphaNumChar)
          .map(_.take(maxSize / (noOfSpaces + 1)))
          .map(String.valueOf)
      )
      .map(_.mkString(" "))

  final val booleanGen = Gen.frequency((20, Gen.const(false)), (80, Gen.const(true)))
  final val noneGen = Gen.const(None)
  final def some[A](gen: Gen[A]): Gen[Option[A]] = gen.map(Some.apply)

  final val eoriGen = nonEmptyString(15)
  final val epuGen = Gen.chooseNum(1, 700).map(EPU.apply)

  final val entryNumberGen = for {
    prefix <- Gen.oneOf(Seq("A", "0"))
    body   <- Gen.listOfN(5, upperCaseChar)
    suffix <- upperCaseChar
  } yield EntryNumber(prefix + String.valueOf(body) + suffix)

  final val entryDateGen = Gen.const(LocalDate.now.plusDays(1))

  final val entryDetailsGen = for {
    epu         <- epuGen
    entryNumber <- entryNumberGen
    entryDate   <- entryDateGen
  } yield EntryDetails(
    epu,
    entryNumber,
    entryDate
  )

  final def conditional[A](b: Option[Any], gen: Gen[A]): Gen[Option[A]] =
    if (b.isDefined) Gen.option(gen) else noneGen

  final def follows[A](b: Option[Any], gen: Gen[A]): Gen[Option[A]] =
    if (b.isDefined) some(gen) else noneGen

  final def conditional[A](b: Boolean, gen: Gen[A]): Gen[Option[A]] =
    if (b) Gen.option(gen) else noneGen

  final val vesselDetailsGen = for {
    vesselName    <- Gen.option(nonEmptyString(10))
    dateOfArrival <- conditional(vesselName, Gen.const(LocalDate.now()))
    timeOfArrival <- follows(dateOfArrival, Gen.const(LocalTime.now()))
  } yield VesselDetails(
    vesselName,
    dateOfArrival,
    timeOfArrival
  )

  final val exportContactInfoGen = for {
    contactName   <- Gen.option(nonEmptyString(30, 1))
    contactEmail  <- Gen.const("foo@bar.co.uk")
    contactNumber <- Gen.option(Gen.const("079123456789"))
  } yield ExportContactInfo(
    contactName,
    contactEmail,
    contactNumber
  )

  final val importContactInfoGen = for {
    contactName   <- Gen.option(nonEmptyString(30, 1))
    contactEmail  <- Gen.const("bar@foo.co.uk")
    contactNumber <- Gen.option(Gen.const("07998765431"))
  } yield ImportContactInfo(
    contactName,
    contactEmail,
    contactNumber
  )

  final val exportQuestionsGen = for {
    requestType <- Gen.option(Gen.oneOf(ExportRequestType.values))
    routeType   <- conditional(requestType, Gen.oneOf(ExportRouteType.values))
    reason <- conditional(
                routeType.contains(ExportRouteType.Route3) || requestType.existsIn(
                  Set(ExportRequestType.Cancellation, ExportRequestType.WithdrawalOrReturn)
                ),
                nonEmptyString(100, 5)
              )
    hasPriorityGoods <- conditional(routeType, booleanGen)
    priorityGoods    <- conditional(hasPriorityGoods.contains(true), Gen.oneOf(ExportPriorityGoods.values))
    freightType      <- conditional(priorityGoods, Gen.oneOf(ExportFreightType.values))
    vesselDetails    <- conditional(freightType, vesselDetailsGen)
    contactInfo      <- conditional(vesselDetails, exportContactInfoGen)
  } yield ExportQuestions(
    requestType,
    routeType,
    reason,
    hasPriorityGoods,
    priorityGoods,
    freightType,
    vesselDetails,
    contactInfo
  )

  final val importQuestionsGen = for {
    requestType <- Gen.option(Gen.oneOf(ImportRequestType.values))
    routeType   <- conditional(requestType, Gen.oneOf(ImportRouteType.values))
    reason <- conditional(
                routeType.contains(ImportRouteType.Route3) || requestType.existsIn(Set(ImportRequestType.Cancellation)),
                nonEmptyString(100, 5)
              )
    hasPriorityGoods <- conditional(routeType, booleanGen)
    priorityGoods    <- conditional(hasPriorityGoods.contains(true), Gen.oneOf(ImportPriorityGoods.values))
    hasALVS          <- conditional(priorityGoods, booleanGen)
    freightType      <- conditional(hasALVS, Gen.oneOf(ImportFreightType.values))
    vesselDetails    <- conditional(freightType, vesselDetailsGen)
    contactInfo      <- conditional(vesselDetails, importContactInfoGen)
  } yield ImportQuestions(
    requestType,
    routeType,
    reason,
    hasPriorityGoods,
    priorityGoods,
    hasALVS,
    freightType,
    vesselDetails,
    contactInfo
  )

  final val uploadedFileGen = for {
    upscanReference <- Gen.uuid.map(_.toString())
    downloadUrl     <- Gen.const("https://foo.bar/123")
    uploadTimestamp <- Gen.const(ZonedDateTime.now)
    checksum        <- nonEmptyString(64)
    fileName        <- nonEmptyString(20)
    fileMimeType    <- Gen.oneOf("text/plain", "text/jpeg", "application/pdf", "")
    fileSize        <- Gen.option(Gen.chooseNum(1, 6 * 1024 * 1024))
  } yield UploadedFile(
    upscanReference,
    downloadUrl,
    uploadTimestamp,
    checksum,
    fileName,
    fileMimeType,
    fileSize
  )

  final val traderServicesCreateCaseRequestGen = for {
    entryDetails  <- entryDetailsGen
    questions     <- if (entryDetails.isImportDeclaration) importQuestionsGen else exportQuestionsGen
    uploadedFiles <- Gen.choose(0, 10).flatMap(Gen.listOfN(_, uploadedFileGen))
    eori          <- Gen.option(eoriGen)
  } yield TraderServicesCreateCaseRequest(
    entryDetails,
    questions,
    uploadedFiles,
    eori
  )

  final val fileTransferResultGen = for {
    upscanReference <- Gen.uuid.map(_.toString())
    success         <- booleanGen
    httpStatus      <- if (success) Gen.const(200) else Gen.const(501)
    transferredAt   <- Gen.const(LocalDateTime.now)
    error           <- if (success) noneGen else some(nonEmptyString(50, 3))
  } yield FileTransferResult(
    upscanReference,
    success,
    httpStatus,
    transferredAt,
    error
  )

  final val traderServicesResultGen = for {
    caseId              <- nonEmptyString(12)
    generatedAt         <- Gen.const(LocalDateTime.now)
    fileTransferResults <- Gen.nonEmptyListOf(fileTransferResultGen)
  } yield TraderServicesResult(
    caseId,
    generatedAt,
    fileTransferResults
  )

  final val apiErrorGen = for {
    errorCode    <- nonEmptyString(10)
    errorMessage <- Gen.option(nonEmptyString(50, 3))
  } yield ApiError(errorCode, errorMessage)

  final val traderServicesCaseResponseGen = for {
    correlationId <- Gen.uuid.map(_.toString())
    flag          <- booleanGen
    result        <- conditional(flag, traderServicesResultGen)
    error         <- conditional(!flag, apiErrorGen)
  } yield TraderServicesCaseResponse(
    correlationId,
    error,
    result
  )

  final val traderServicesUpdateCaseRequestGen = for {
    caseReferenceNumber <- nonEmptyString(12)
    typeOfAmendment     <- Gen.oneOf(TypeOfAmendment.values)
    responseText        <- conditional(typeOfAmendment.hasResponse, nonEmptyString(100, 5))
    uploadedFiles <- if (typeOfAmendment.hasFiles) Gen.choose(0, 10).flatMap(Gen.listOfN(_, uploadedFileGen))
                     else Gen.const(Seq.empty)
    eori <- Gen.option(eoriGen)
  } yield TraderServicesUpdateCaseRequest(
    caseReferenceNumber,
    typeOfAmendment,
    responseText,
    uploadedFiles,
    eori
  )

}
