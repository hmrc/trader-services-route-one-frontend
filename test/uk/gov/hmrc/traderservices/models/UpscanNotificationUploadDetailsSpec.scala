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

import uk.gov.hmrc.traderservices.support.UnitSpec

class UpscanNotificationUploadDetailsSpec extends UnitSpec {

  "UpscanNotification.UploadDetails" should {
    "decode single mime-encoded word" in {
      UpscanNotification.UploadDetails.decodeMimeEncodedWord(
        "=?UTF-8?Q?sample=5F640=C3=97426.tiff?="
      ) shouldBe "sample_640×426.tiff"
    }

    "decode text with multiple mime-encoded words" in {
      UpscanNotification.UploadDetails.decodeMimeEncodedWord(
        "=?UTF-8?Q?You=E2=80=99ve_submitted_your_documents_-_Send_d?= =?UTF-8?Q?ocuments_for_a_customs_check_-_GOV.UK.pdf?="
      ) shouldBe "You’ve submitted your documents - Send documents for a customs check - GOV.UK.pdf"
    }

    "do nothing if text has no mime-encodings at all" in {
      UpscanNotification.UploadDetails.decodeMimeEncodedWord(
        "submitted your documents 01234-56789"
      ) shouldBe "submitted your documents 01234-56789"
    }
  }

}
