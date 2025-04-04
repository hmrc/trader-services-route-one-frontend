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

package uk.gov.hmrc.traderservices.models

import play.api.libs.json._
import uk.gov.hmrc.traderservices.support.UnitSpec

class QuestionsAnswersFormatSpec extends UnitSpec {

  "QuestionsAnswers" should {
    "report an error while de-serializing unsupported json" in {
      QuestionsAnswers.reads.reads(JsNull) shouldBe a[JsError]
      QuestionsAnswers.reads.reads(Json.obj()) shouldBe a[JsError]
      QuestionsAnswers.reads.reads(JsString("import")) shouldBe a[JsError]
      QuestionsAnswers.reads.reads(JsNumber(1)) shouldBe a[JsError]
      QuestionsAnswers.reads.reads(JsBoolean(true)) shouldBe a[JsError]
    }

    "throw an exception when serializing unknown impl" in {
      an[Exception] shouldBe thrownBy(
        QuestionsAnswers.writes.writes(new QuestionsAnswers {})
      )
    }

  }
}
