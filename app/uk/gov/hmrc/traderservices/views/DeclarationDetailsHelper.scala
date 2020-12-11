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

package uk.gov.hmrc.traderservices.views

import uk.gov.hmrc.traderservices.models.DeclarationDetails
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryList
import uk.gov.hmrc.traderservices.controllers.routes.CreateCaseJourneyController
import play.api.i18n.Messages
import uk.gov.hmrc.traderservices.views.CommonUtilsHelper.Improvements

trait DeclarationDetailsHelper extends SummaryListRowHelper with DateTimeFormatHelper {

  def summaryListOfDeclarationDetails(
    declarationDetails: DeclarationDetails
  )(implicit messages: Messages): SummaryList =
    SummaryList(rows =
      Seq(
        summaryListRow(
          label = "summary.declaration-details.epu",
          value = declarationDetails.epu.value.format3d,
          visuallyHiddenText = Some("summary.declaration-details.epu"),
          action = (CreateCaseJourneyController.showEnterDeclarationDetails, "site.change")
        ),
        summaryListRow(
          label = "summary.declaration-details.entryNumber",
          value = declarationDetails.entryNumber.value,
          visuallyHiddenText = Some("summary.declaration-details.entryNumber"),
          action = (CreateCaseJourneyController.showEnterDeclarationDetails, "site.change")
        ),
        summaryListRow(
          label = "summary.declaration-details.entryDate",
          value = formatDateForDisplay(declarationDetails.entryDate),
          visuallyHiddenText = Some("summary.declaration-details.entryDate"),
          action = (CreateCaseJourneyController.showEnterDeclarationDetails, "site.change")
        )
      )
    )

}
