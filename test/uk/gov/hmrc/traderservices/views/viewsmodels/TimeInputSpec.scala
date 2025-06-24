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

import uk.gov.hmrc.govukfrontend.views.viewmodels.FormGroup
import uk.gov.hmrc.traderservices.views.viewmodels.TimeInput
import uk.gov.hmrc.govukfrontend.views.viewmodels.hint.Hint
import uk.gov.hmrc.govukfrontend.views.viewmodels.errormessage.ErrorMessage

class TimeInputSpec extends UnitSpec {

  "serialize and deserialize" when {

    "json and object are completely empty" in new JsonFormatTest[TimeInput](info) {

      validateJsonReads(
        """{}""",
        TimeInput()
      )
    }

    "json structure is set but fields are empty" in new JsonFormatTest[TimeInput](info) {

      validateJsonFormat(
        """{"id":"","items":[],"periodSelectItems":[],"formGroup":{"classes":"","attributes":{}},"classes":"","attributes":{},"showSelectPeriod":true}""",
        TimeInput(formGroup = FormGroup(Some("")))
      )
    }

    "json structure is set but fields are empty and object has id" in new JsonFormatTest[TimeInput](info) {

      validateJsonFormat(
        """{"id":"foo","items":[],"periodSelectItems":[],"formGroup":{"classes":"","attributes":{}},"classes":"","attributes":{},"showSelectPeriod":true}""",
        TimeInput(
          id = "foo",
          namePrefix = None,
          items = Seq.empty,
          periodSelectItems = Seq.empty,
          hint = None,
          errorMessage = None,
          formGroup = FormGroup(Some("")),
          fieldset = None,
          classes = "",
          attributes = Map.empty,
          showSelectPeriod = true
        )
      )
    }

    "json structure is populated" in new JsonFormatTest[TimeInput](info) {

      validateJsonFormat(
        """{"id":"foo","namePrefix":"bar","items":[],"periodSelectItems":[],"hint":{"classes":"a b c","attributes":{}},"errorMessage":{"id":"error_1","classes":"","attributes":{},"visuallyHiddenText":"Error"},"formGroup":{"classes":"abc cde","attributes":{}},"classes":"aaa bbb","attributes":{"a":"1"},"showSelectPeriod":true}""",
        TimeInput(
          id = "foo",
          namePrefix = Some("bar"),
          items = Seq.empty,
          periodSelectItems = Seq.empty,
          hint = Some(Hint(classes = "a b c")),
          errorMessage = Some(ErrorMessage(id = Some("error_1"))),
          formGroup = FormGroup(classes = Some("abc cde")),
          fieldset = None,
          classes = "aaa bbb",
          attributes = Map("a" -> "1"),
          showSelectPeriod = true
        )
      )
    }
  }
}
