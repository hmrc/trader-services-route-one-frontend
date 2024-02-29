/*
 * Copyright 2023 HM Revenue & Customs
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

import test.uk.gov.hmrc.traderservices.support.UnitSpec
import uk.gov.hmrc.traderservices.support.JsonFormatTest

class FileUploadFailureFormatSpec extends UnitSpec {

  "FileUploadFailure format" should {

    "serialize and deserialize FileTransmissionFailed" in new JsonFormatTest[FileUploadError](info) {
      validateJsonFormat(
        """{"FileTransmissionFailed":{"error":{"key":"a","errorCode":"b","errorMessage":"c","errorRequestId":"e","errorResource":"d"}}}""".stripMargin,
        FileTransmissionFailed(error = S3UploadError("a", "b", "c", Some("e"), Some("d")))
      )
    }

    "serialize and deserialize FileVerificationFailed" in new JsonFormatTest[FileUploadError](info) {
      validateJsonFormat(
        """{"FileVerificationFailed":{"details":{"failureReason":"QUARANTINE","message":"This file has virus."}}}""".stripMargin,
        FileVerificationFailed(details =
          UpscanNotification.FailureDetails(UpscanNotification.QUARANTINE, "This file has virus.")
        )
      )
    }

  }
}
