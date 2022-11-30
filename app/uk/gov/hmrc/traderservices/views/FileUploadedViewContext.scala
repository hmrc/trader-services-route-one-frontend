/*
 * Copyright 2022 HM Revenue & Customs
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

import javax.inject.Singleton
import play.api.data.Form
import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem
import uk.gov.hmrc.traderservices.models.FileUploads
import uk.gov.hmrc.traderservices.models.FileUpload
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryList
import play.api.mvc.Call

@Singleton
class FileUploadedViewContext extends RadioItemsHelper with SummaryListRowHelper {

  def uploadAnotherFileItems(form: Form[_])(implicit messages: Messages): Seq[RadioItem] =
    Seq(
      RadioItem(
        value = Some("yes"),
        content = Text(messages(s"form.file-uploaded.uploadAnotherFile.yes")),
        checked = form("uploadAnotherFile").value.contains("yes")
      ),
      RadioItem(
        value = Some("no"),
        content = Text(messages(s"form.file-uploaded.uploadAnotherFile.no")),
        checked = form("uploadAnotherFile").value.contains("no")
      )
    )

  def summaryListOfFileUploads(
    fileUploads: FileUploads,
    previewFileCall: (String, String) => Call,
    removeFileCall: String => Call
  )(implicit
    messages: Messages
  ): SummaryList = {

    def fileUploadRow(fileUpload: FileUpload.Accepted, index: Int) =
      summaryListRow(
        label = s"$index.",
        value = fileUpload.fileName,
        visuallyHiddenText = Some(fileUpload.fileName),
        keyClasses = Some(""),
        valueClasses = Some("govuk-!-width-full"),
        action = (removeFileCall(fileUpload.reference), "site.file.remove"),
        url = Some(previewFileCall(fileUpload.reference, fileUpload.fileName).url)
      )

    SummaryList(
      rows = fileUploads.files.collect { case a: FileUpload.Accepted => a }.zipWithIndex.map { case (file, index) =>
        fileUploadRow(file, index + 1)
      },
      classes = """govuk-summary-list govuk-!-margin-bottom-9"""
    )
  }
}
