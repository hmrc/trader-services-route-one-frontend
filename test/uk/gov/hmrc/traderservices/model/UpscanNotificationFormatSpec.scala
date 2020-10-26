/*
 * Copyright 2020 HM Revenue & Customs
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

import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.support.JsonFormatTest
import java.time.ZonedDateTime

class UpscanNotificationFormatSpec extends UnitSpec {

  "UpscanNotificationFormats" should {

    "serialize and deserialize UpscanFileReady" in new JsonFormatTest[UpscanNotification](info) {
      validateJsonFormat(
        """{
          |"reference":"11370e18-6e24-453e-b45a-76d3e32ea33d",
          |"fileStatus":"READY",
          |"downloadUrl":"https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
          |"uploadDetails":{
          |"uploadTimestamp":"2018-04-24T09:30:00Z",
          |"checksum":"396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
          |"fileName":"test.pdf",
          |"fileMimeType":"application/pdf"
          |}
          |}""".stripMargin,
        UpscanFileReady(
          reference = "11370e18-6e24-453e-b45a-76d3e32ea33d",
          downloadUrl = "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
          uploadDetails = UpscanNotification.UploadDetails(
            uploadTimestamp = ZonedDateTime.parse("2018-04-24T09:30:00Z"),
            checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
            fileName = "test.pdf",
            fileMimeType = "application/pdf"
          )
        )
      )
    }

    "serialize and deserialize UpscanFileFailed when QUARANTINE" in new JsonFormatTest[UpscanNotification](info) {
      validateJsonFormat(
        """{"reference":"11370e18-6e24-453e-b45a-76d3e32ea33d","fileStatus":"FAILED","failureDetails":{"failureReason":"QUARANTINE","message":"e.g. This file has a virus"}}""",
        UpscanFileFailed(
          reference = "11370e18-6e24-453e-b45a-76d3e32ea33d",
          failureDetails = UpscanNotification.FailureDetails(
            failureReason = UpscanNotification.QUARANTINE,
            message = "e.g. This file has a virus"
          )
        )
      )
    }

    "serialize and deserialize UpscanFileFailed when REJECTED" in new JsonFormatTest[UpscanNotification](info) {
      validateJsonFormat(
        """{"reference":"11370e18-6e24-453e-b45a-76d3e32ea33d","fileStatus":"FAILED","failureDetails":{"failureReason":"REJECTED","message":"MIME type $mime is not allowed for service $service-name"}}""",
        UpscanFileFailed(
          reference = "11370e18-6e24-453e-b45a-76d3e32ea33d",
          failureDetails = UpscanNotification.FailureDetails(
            failureReason = UpscanNotification.REJECTED,
            message = "MIME type $mime is not allowed for service $service-name"
          )
        )
      )
    }

    "serialize and deserialize UpscanFileFailed when UNKNOWN" in new JsonFormatTest[UpscanNotification](info) {
      validateJsonFormat(
        """{"reference":"11370e18-6e24-453e-b45a-76d3e32ea33d","fileStatus":"FAILED","failureDetails":{"failureReason":"UNKNOWN","message":"Something unknown happened"}}""",
        UpscanFileFailed(
          reference = "11370e18-6e24-453e-b45a-76d3e32ea33d",
          failureDetails = UpscanNotification.FailureDetails(
            failureReason = UpscanNotification.UNKNOWN,
            message = "Something unknown happened"
          )
        )
      )
    }

  }
}
