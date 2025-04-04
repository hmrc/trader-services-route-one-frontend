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

package uk.gov.hmrc.traderservices.controllers

import play.api.mvc.Cookie
import play.api.test.FakeRequest
import uk.gov.hmrc.traderservices.stubs.{TraderServicesApiStubs, UpscanInitiateStubs}
import uk.gov.hmrc.traderservices.models._

import java.time.LocalDateTime

class AmendCaseJourneyWithMultifileUploadISpec
    extends AmendCaseJourneyISpecSetup with TraderServicesApiStubs with UpscanInitiateStubs {

  import journey.model.FileUploadState._
  import journey.model.State._

  val dateTime = LocalDateTime.now()

  override def uploadMultipleFilesFeature: Boolean = true
  override def requireEnrolmentFeature: Boolean = true
  override def requireOptionalTransportFeature: Boolean = false

  "AmendCaseJourneyController" when {

    "preferUploadMultipleFiles" should {
      "return false when jsenabled cookie NOT set" in {
        controller.preferUploadMultipleFiles(FakeRequest()) shouldBe false
      }

      "return true when jsenabled cookie set and uploadMultipleFilesFeature flag set" in {
        controller.preferUploadMultipleFiles(
          fakeRequest(Cookie(controller.COOKIE_JSENABLED, "true"))
        ) shouldBe true
      }
    }

    "getCallFor" should {
      "return /add/file-verification for WaitingForFileVerification" in {

        val state = WaitingForFileVerification(
          exampleAmendCaseModel,
          "2b72fe99-8adf-4edb-865e-622ae710f77c",
          UploadRequest(href = "https://s3.bucket", fields = Map("callbackUrl" -> "https://foo.bar/callback")),
          FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c"),
          FileUploads(files =
            Seq(
              FileUpload.Initiated(Nonce.Any, Timestamp.Any, "11370e18-6e24-453e-b45a-76d3e32ea33d"),
              FileUpload.Posted(Nonce.Any, Timestamp.Any, "2b72fe99-8adf-4edb-865e-622ae710f77c")
            )
          )
        )
        controller
          .getCallFor(state)(FakeRequest())
          .url
          .should(endWith("/add/file-verification"))
      }

      "return workInProgresDeadEndCall for an unsupported state" in {
        controller
          .getCallFor(WorkInProgressDeadEnd)(FakeRequest())
          .shouldBe(controller.workInProgresDeadEndCall)
      }

      "return amend case already submitted" in {
        controller
          .getCallFor(AmendCaseAlreadySubmitted)(FakeRequest())
          .url
          .should(endWith("/add/case-already-submitted"))
      }
    }

    "renderState" should {
      "return NotImplemented for an unsupported state" in {
        controller
          .renderState(WorkInProgressDeadEnd, Nil, None)(FakeRequest())
          .shouldBe(controller.NotImplemented)
      }
    }

  }
}
