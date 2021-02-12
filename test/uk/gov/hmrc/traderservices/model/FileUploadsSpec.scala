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

package uk.gov.hmrc.traderservices.model

import uk.gov.hmrc.traderservices.support.UnitSpec
import uk.gov.hmrc.traderservices.models._

class FileUploadsSpec extends UnitSpec {

  "FileUploads" should {
    "filter out initiated uploads" in {
      FileUploads(
        files = Seq(FileUpload.Initiated(Nonce.Any, "foo"))
      ).filterOutInitiated shouldBe FileUploads()

      FileUploads(
        files = Seq(
          FileUpload.Initiated(Nonce.Any, "foo1"),
          FileUpload.Initiated(Nonce.Any, "foo2"),
          FileUpload.Initiated(Nonce.Any, "foo3")
        )
      ).filterOutInitiated shouldBe FileUploads()

      FileUploads(
        files = Seq(
          FileUpload.Posted(Nonce.Any, "foo1"),
          FileUpload.Initiated(Nonce.Any, "foo2"),
          FileUpload.Initiated(Nonce.Any, "foo3")
        )
      ).filterOutInitiated shouldBe FileUploads(files = Seq(FileUpload.Posted(Nonce.Any, "foo1")))

      FileUploads(
        files = Seq(
          FileUpload.Posted(Nonce.Any, "foo1"),
          FileUpload.Initiated(Nonce.Any, "foo2"),
          FileUpload.Posted(Nonce.Any, "foo3")
        )
      ).filterOutInitiated shouldBe FileUploads(files =
        Seq(FileUpload.Posted(Nonce.Any, "foo1"), FileUpload.Posted(Nonce.Any, "foo3"))
      )

      FileUploads(
        files = Seq(
          FileUpload.Posted(Nonce.Any, "foo1"),
          FileUpload.Posted(Nonce.Any, "foo2"),
          FileUpload.Posted(Nonce.Any, "foo3")
        )
      ).filterOutInitiated shouldBe FileUploads(files =
        Seq(
          FileUpload.Posted(Nonce.Any, "foo1"),
          FileUpload.Posted(Nonce.Any, "foo2"),
          FileUpload.Posted(Nonce.Any, "foo3")
        )
      )

    }
  }
}
