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

package uk.gov.hmrc.traderservices.models

import uk.gov.hmrc.traderservices.support.UnitSpec
import java.time.ZonedDateTime

class FileUploadsSpec extends UnitSpec {

  val initiatedFileUpload1 = FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo1")
  val initiatedFileUpload2 = FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo2")
  val initiatedFileUpload3 = FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo3")
  val postedFileUpload1 = FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo1")
  val postedFileUpload2 = FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo2")
  val postedFileUpload3 = FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo3")

  val acceptedFileUpload = FileUpload.Accepted(
    Nonce(4),
    Timestamp.Any,
    "foo-bar-ref-4",
    "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
    ZonedDateTime.parse("2018-04-24T09:30:00Z"),
    "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
    "test.pdf",
    "application/pdf",
    Some(4567890)
  )

  val failedFileUpload = FileUpload.Failed(
    Nonce(4),
    Timestamp.Any,
    "foo-bar-ref-4",
    UpscanNotification.FailureDetails(
      UpscanNotification.QUARANTINE,
      "e.g. This file has a virus"
    )
  )

  val rejectedFileUpload = FileUpload.Rejected(
    Nonce(5),
    Timestamp.Any,
    "foo-bar-ref-5",
    S3UploadError("a", "b", "c")
  )

  val duplicateFileUpload = FileUpload.Duplicate(
    Nonce(6),
    Timestamp.Any,
    "foo-bar-ref-6",
    "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
    "test.pdf",
    "test2.png"
  )

  "FileUploads" should {
    "filter out initiated uploads" in {
      FileUploads(
        files = Seq(FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo"))
      ).filterOutInitiated shouldBe FileUploads()

      FileUploads(
        files = Seq(
          initiatedFileUpload1,
          initiatedFileUpload2,
          initiatedFileUpload3
        )
      ).filterOutInitiated shouldBe FileUploads()

      FileUploads(
        files = Seq(
          postedFileUpload1,
          initiatedFileUpload2,
          failedFileUpload,
          initiatedFileUpload3
        )
      ).filterOutInitiated shouldBe FileUploads(files = Seq(postedFileUpload1, failedFileUpload))

      FileUploads(
        files = Seq(
          postedFileUpload1,
          initiatedFileUpload2,
          rejectedFileUpload
        )
      ).filterOutInitiated shouldBe FileUploads(files = Seq(postedFileUpload1, rejectedFileUpload))

      FileUploads(
        files = Seq(
          postedFileUpload1,
          postedFileUpload2,
          postedFileUpload3
        )
      ).filterOutInitiated shouldBe FileUploads(files =
        Seq(
          postedFileUpload1,
          postedFileUpload2,
          postedFileUpload3
        )
      )

    }

    "filter accepted uploads" in {
      FileUploads(
        files = Seq(FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo"))
      ).onlyAccepted shouldBe FileUploads()

      FileUploads(
        files = Seq(
          initiatedFileUpload1,
          initiatedFileUpload2,
          initiatedFileUpload3,
          acceptedFileUpload
        )
      ).onlyAccepted shouldBe FileUploads(Seq(acceptedFileUpload))

      FileUploads(
        files = Seq(
          postedFileUpload1,
          acceptedFileUpload,
          initiatedFileUpload2,
          initiatedFileUpload3
        )
      ).onlyAccepted shouldBe FileUploads(files = Seq(acceptedFileUpload))

      FileUploads(
        files = Seq(
          postedFileUpload1,
          initiatedFileUpload2,
          acceptedFileUpload,
          postedFileUpload3
        )
      ).onlyAccepted shouldBe FileUploads(files = Seq(acceptedFileUpload))

      FileUploads(
        files = Seq(
          postedFileUpload1,
          acceptedFileUpload,
          postedFileUpload2,
          postedFileUpload3
        )
      ).onlyAccepted shouldBe FileUploads(files = Seq(acceptedFileUpload))

    }

    "match windows file path" in {
      ("""C:\Users\Public\Pictures\Sample Pictures\Chrysanthemum.jpg""" match {
        case FileUpload.isWindowPathHaving(name) => name
        case _                                   => "foo"
      }) shouldBe "Chrysanthemum.jpg"

      ("""C:\Users\Public\Pictures\Sample \u1213Pictures\Chrysanthemum.jpg""" match {
        case FileUpload.isWindowPathHaving(name) => name
        case _                                   => "foo"
      }) shouldBe "Chrysanthemum.jpg"

      ("""C:\Users\Public\Pictures\Sample \u1213Pictures\C\u1213hrysanthemum.jpg""" match {
        case FileUpload.isWindowPathHaving(name) => name
        case _                                   => "foo"
      }) shouldBe "C\u1213hrysanthemum.jpg"

      ("""D:\Desktop\My Best Pictures\Chrysanthemum.png""" match {
        case FileUpload.isWindowPathHaving(name) => name
        case _                                   => "foo"
      }) shouldBe "Chrysanthemum.png"

      ("""C:\Chrysanthemum.jpg""" match {
        case FileUpload.isWindowPathHaving(name) => name
        case _                                   => "foo"
      }) shouldBe "Chrysanthemum.jpg"

      ("""c:\Chrysanthemum""" match {
        case FileUpload.isWindowPathHaving(name) => name
        case _                                   => "foo"
      }) shouldBe "Chrysanthemum"

      ("""c:\""" match {
        case FileUpload.isWindowPathHaving(name) => name
        case _                                   => "foo"
      }) shouldBe "foo"

      ("""Chrysanthemum.jpg""" match {
        case FileUpload.isWindowPathHaving(name) => name
        case _                                   => "foo"
      }) shouldBe "foo"
    }

    "sanitize file name" in {
      FileUpload.sanitizeFileName("") shouldBe ""
      FileUpload.sanitizeFileName("a") shouldBe "a"
      FileUpload.sanitizeFileName("a.a") shouldBe "a.a"
      FileUpload.sanitizeFileName("123456780.abc") shouldBe "123456780.abc"
      FileUpload.sanitizeFileName(
        """C:\Users\Public\Pictures\Sample Pictures\Chrysanthemum.jpg"""
      ) shouldBe "Chrysanthemum.jpg"
      FileUpload.sanitizeFileName(
        """C:\Users\Public\Pictures\Sample \u1213Pictures\Chrysanthemum.jpg"""
      ) shouldBe "Chrysanthemum.jpg"
      FileUpload.sanitizeFileName(
        """C:\Users\Public\Pictures\Sample \u1213Pictures\Chrysanthemum\u1213jpg"""
      ) shouldBe "Chrysanthemum\u1213jpg"
      FileUpload.sanitizeFileName(
        """C:\Users\Public\Pictures\Sample \u1213Pictures\0000Chry*[(anth]?)emum\u1213jpg"""
      ) shouldBe "0000Chry*[(anth]?)emum\u1213jpg"
    }

    "trim the file name" in {
      val MAX = FileUpload.MAX_FILENAME_LENGTH

      FileUpload.trimFileName("") shouldBe ""
      FileUpload.trimFileName("a") shouldBe "a"
      FileUpload.trimFileName("a.a") shouldBe "a.a"
      FileUpload.trimFileName("a" * MAX) shouldBe "a" * MAX
      FileUpload.trimFileName("a" * (MAX + 1)) shouldBe "a" * MAX
      FileUpload.trimFileName("a" * (MAX - 5) + ".ext") shouldBe "a" * (MAX - 5) + ".ext"
      FileUpload.trimFileName("a" * (MAX - 4) + ".ext") shouldBe "a" * (MAX - 4) + ".ext"
      FileUpload.trimFileName("a" * (MAX + 1) + ".ext") shouldBe "a" * (MAX - 4) + ".ext"
      FileUpload.trimFileName("a" * (MAX - 2) + ".") shouldBe "a" * (MAX - 2) + "."
      FileUpload.trimFileName("a" * (MAX - 1) + ".") shouldBe "a" * (MAX - 1) + "."
      FileUpload.trimFileName("a" * MAX + ".") shouldBe "a" * (MAX - 1) + "."
      FileUpload.trimFileName("-" * MAX) shouldBe "-" * MAX
      FileUpload.trimFileName("-" * (MAX - 5) + ".ext") shouldBe "-" * (MAX - 5) + ".ext"
      FileUpload.trimFileName("-" * (MAX - 4) + ".ext") shouldBe "-" * (MAX - 4) + ".ext"
      FileUpload.trimFileName("-" * (MAX + 1) + ".ext") shouldBe "-" * (MAX - 4) + ".ext"

      FileUpload.trimFileName(
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus tempor egestas viverra usce."
      ) shouldBe "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus tempor egestas viverra usce."
      FileUpload.trimFileName(
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum cursus, erat sed fringilla lacinia, sem nulla vulputate mauris, at tincidunt eros.ext"
      ) shouldBe "LoremipsumdolorsitametconsecteturadipiscingelitVestibulumcursuseratsedfringillalaciniasem.ext"
      FileUpload.trimFileName(
        "123orem_ipsum_dolor_sit_amet-----consec9999999999tetur-adipiscing elit_Vestibulum***12cursus,!!![erat]+sed+fringilla (lacinia), sem/nulla/vulputate /_mauris,~at&tincidunt@eros.ext"
      ) shouldBe "123oremipsumdolorsitametconsec9999999999teturadipiscingelitVestibulum12cursuseratsedfring.ext"

    }

    "count accepted" in {
      FileUploads(
        files = Seq(
          postedFileUpload1,
          acceptedFileUpload,
          initiatedFileUpload2,
          acceptedFileUpload,
          initiatedFileUpload3,
          duplicateFileUpload,
          rejectedFileUpload,
          failedFileUpload
        )
      ).acceptedCount shouldBe 2
    }

    "count initiated or accepted" in {
      FileUploads(
        files = Seq(
          postedFileUpload1,
          acceptedFileUpload,
          initiatedFileUpload2,
          acceptedFileUpload,
          initiatedFileUpload3,
          duplicateFileUpload,
          rejectedFileUpload,
          failedFileUpload
        )
      ).initiatedOrAcceptedCount shouldBe 5
    }

    "have isReady property" in {
      postedFileUpload1.isReady shouldBe false
      initiatedFileUpload1.isReady shouldBe false
      acceptedFileUpload.isReady shouldBe true
      failedFileUpload.isReady shouldBe true
      rejectedFileUpload.isReady shouldBe true
      duplicateFileUpload.isReady shouldBe true
    }

    "have MAX_FILENAME_LENGTH" in {
      FileUpload.MAX_FILENAME_LENGTH shouldBe 93
    }
  }
}
