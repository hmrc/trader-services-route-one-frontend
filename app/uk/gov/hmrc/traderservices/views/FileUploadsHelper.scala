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

package uk.gov.hmrc.traderservices.views

import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryList
import play.api.i18n.Messages
import play.api.mvc.Call
import uk.gov.hmrc.traderservices.models.FileUploads
import play.twirl.api.HtmlFormat

trait FileUploadsHelper extends SummaryListRowHelper {

  def summaryListOfFileUploads(fileUploads: FileUploads, changeCall: Call)(implicit
    messages: Messages
  ): SummaryList = {

    val value =
      fileUploads.toUploadedFiles
        .map(file => s"<div>${HtmlFormat.escape(file.fileName)}</div>")
        .mkString

    SummaryList(
      Seq(
        summaryListRow(
          label = "summary.file-names",
          value = if (value.nonEmpty) value else "-",
          visuallyHiddenText = Some("summary.file-names"),
          action = (changeCall, "site.change"),
          escape = false
        )
      )
    )
  }

}
