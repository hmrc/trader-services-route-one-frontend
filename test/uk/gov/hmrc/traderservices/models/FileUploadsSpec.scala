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

class FileUploadsSpec extends UnitSpec {

  "FileUploads" should {
    "filter out initiated uploads" in {
      FileUploads(
        files = Seq(FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo"))
      ).filterOutInitiated shouldBe FileUploads()

      FileUploads(
        files = Seq(
          FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo1"),
          FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo2"),
          FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo3")
        )
      ).filterOutInitiated shouldBe FileUploads()

      FileUploads(
        files = Seq(
          FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo1"),
          FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo2"),
          FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo3")
        )
      ).filterOutInitiated shouldBe FileUploads(files = Seq(FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo1")))

      FileUploads(
        files = Seq(
          FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo1"),
          FileUpload.Initiated(Nonce.Any, Timestamp.Any, "foo2"),
          FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo3")
        )
      ).filterOutInitiated shouldBe FileUploads(files =
        Seq(FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo1"), FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo3"))
      )

      FileUploads(
        files = Seq(
          FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo1"),
          FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo2"),
          FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo3")
        )
      ).filterOutInitiated shouldBe FileUploads(files =
        Seq(
          FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo1"),
          FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo2"),
          FileUpload.Posted(Nonce.Any, Timestamp.Any, "foo3")
        )
      )

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
  }
}
