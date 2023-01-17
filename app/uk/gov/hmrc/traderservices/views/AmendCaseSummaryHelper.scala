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

import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryList
import uk.gov.hmrc.traderservices.controllers.routes.AmendCaseJourneyController
import uk.gov.hmrc.traderservices.models.AmendCaseModel
import javax.inject.Singleton

@Singleton
class AmendCaseSummaryHelper extends SummaryListRowHelper with FileUploadsHelper {

  def caseReferenceNumber(
    model: AmendCaseModel
  )(implicit messages: Messages): SummaryList =
    SummaryList(rows =
      Seq(
        summaryListRow(
          label = "view.amend-case.summary.caseReferenceNumber",
          value = model.caseReferenceNumber.getOrElse(""),
          valueClasses = Some("case-reference-number"),
          visuallyHiddenText = Some("view.amend-case.summary.caseReferenceNumber"),
          action = (AmendCaseJourneyController.showEnterCaseReferenceNumber, "site.change")
        )
      )
    )
  def additionalInformation(
    model: AmendCaseModel
  )(implicit messages: Messages): SummaryList = {
    val responseText = model.responseText
    val additionalInfo = summaryListRow(
      label = "view.amend-case.summary.additionalInfo.type",
      value = messages(model.typeOfAmendment.map(_.viewFormat).getOrElse("")),
      visuallyHiddenText = Some("view.amend-case.summary.additionalInfo.type"),
      action = (AmendCaseJourneyController.showSelectTypeOfAmendment, "site.change")
    )
    SummaryList(
      if (responseText.nonEmpty)
        Seq(
          additionalInfo,
          summaryListRow(
            label = "view.amend-case.summary.additionalInfo.message",
            value = model.responseText.get,
            visuallyHiddenText = Some("view.amend-case.summary.additionalInfo.message"),
            action = (AmendCaseJourneyController.showEnterResponseText, "site.change")
          )
        )
      else Seq(additionalInfo)
    )
  }
}
