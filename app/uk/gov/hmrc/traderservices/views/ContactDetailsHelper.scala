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

package uk.gov.hmrc.traderservices.views

import uk.gov.hmrc.traderservices.models.ImportContactInfo
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryList
import play.api.i18n.Messages
import play.api.mvc.Call
import uk.gov.hmrc.traderservices.models.ExportContactInfo
import play.twirl.api.HtmlFormat

trait ContactDetailsHelper extends SummaryListRowHelper {

  def formatNumberForDisplay(phoneNum: String): String =
    phoneNum.patch(5, " ", 0).patch(9, " ", 0)

  def summaryListOfImportContactDetails(contactDetailsOpt: Option[ImportContactInfo], changeCall: Call)(implicit
    messages: Messages
  ): SummaryList = {
    val value = contactDetailsOpt match {
      case Some(contactDetails) =>
        val contactName =
          contactDetails.contactName.map(value => s"<div>${HtmlFormat.escape(value)}</div>").getOrElse("")
        val contactEmail = s"<div>${HtmlFormat.escape(contactDetails.contactEmail)}</div>"
        val contactNumber =
          contactDetails.contactNumber
            .map(value => s"<div>${HtmlFormat.escape(formatNumberForDisplay(value))}</div>")
            .getOrElse("")
        contactName + contactEmail + contactNumber

      case None => "-"

    }
    SummaryList(
      Seq(
        summaryListRow(
          label = "summary.contact-details",
          value = value,
          visuallyHiddenText = Some("summary.contact-details"),
          action = (changeCall, "site.change"),
          escape = false
        )
      )
    )
  }

  def summaryListOfExportContactDetails(contactDetailsOpt: Option[ExportContactInfo], changeCall: Call)(implicit
    messages: Messages
  ): SummaryList = {
    val value = contactDetailsOpt match {
      case Some(contactDetails) =>
        val contactName =
          contactDetails.contactName.map(value => s"<div>${HtmlFormat.escape(value)}</div>").getOrElse("")
        val contactEmail = s"<div>${HtmlFormat.escape(contactDetails.contactEmail)}</div>"
        val contactNumber =
          contactDetails.contactNumber
            .map(value => s"<div>${HtmlFormat.escape(formatNumberForDisplay(value))}</div>")
            .getOrElse("")
        contactName + contactEmail + contactNumber

      case None => "-"

    }
    SummaryList(
      Seq(
        summaryListRow(
          label = "summary.contact-details",
          value = value,
          visuallyHiddenText = Some("summary.contact-details"),
          action = (changeCall, "site.change"),
          escape = false
        )
      )
    )
  }

}
