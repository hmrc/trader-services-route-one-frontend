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

import uk.gov.hmrc.traderservices.models.VesselDetails
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryList
import play.api.i18n.Messages
import play.api.mvc.Call

trait VesselDetailsHelper extends SummaryListRowHelper with DateTimeFormatHelper {

  def summaryListOfExportVesselDetails(vesselDetails: VesselDetails, changeCall: Call)(implicit
    messages: Messages
  ): SummaryList =
    SummaryList(
      Seq(
        summaryListRow(
          label = "summary.vessel-details.vesselName",
          value = vesselDetails.vesselName.getOrElse("-"),
          visuallyHiddenText = Some("summary.vessel-details.vesselName"),
          action = (changeCall, "site.change")
        ),
        summaryListRow(
          label = "summary.vessel-details.dateOfDeparture",
          value = vesselDetails.dateOfArrival.map(formatDateForDisplay).getOrElse("-"),
          visuallyHiddenText = Some("summary.vessel-details.dateOfDeparture"),
          action = (changeCall, "site.change")
        ),
        summaryListRow(
          label = "summary.vessel-details.timeOfDeparture",
          value = vesselDetails.timeOfArrival.map(formatTimeForDisplay).getOrElse("-"),
          visuallyHiddenText = Some("summary.vessel-details.timeOfDeparture"),
          action = (changeCall, "site.change")
        )
      )
    )

  def summaryListOfExportVesselDetailsForArrivalTypes(vesselDetails: VesselDetails, changeCall: Call)(implicit
    messages: Messages
  ): SummaryList =
    SummaryList(
      Seq(
        summaryListRow(
          label = "summary.vessel-details.vesselName",
          value = vesselDetails.vesselName.getOrElse("-"),
          visuallyHiddenText = Some("summary.vessel-details.vesselName"),
          action = (changeCall, "site.change")
        ),
        summaryListRow(
          label = "summary.vessel-details.dateOfArrival",
          value = vesselDetails.dateOfArrival.map(formatDateForDisplay).getOrElse("-"),
          visuallyHiddenText = Some("summary.vessel-details.dateOfArrival"),
          action = (changeCall, "site.change")
        ),
        summaryListRow(
          label = "summary.vessel-details.timeOfArrival",
          value = vesselDetails.timeOfArrival.map(formatTimeForDisplay).getOrElse("-"),
          visuallyHiddenText = Some("summary.vessel-details.timeOfArrival"),
          action = (changeCall, "site.change")
        )
      )
    )

  def summaryListOfImportVesselDetails(vesselDetails: VesselDetails, changeCall: Call)(implicit
    messages: Messages
  ): SummaryList =
    SummaryList(
      Seq(
        summaryListRow(
          label = "summary.vessel-details.vesselName",
          value = vesselDetails.vesselName.getOrElse("-"),
          visuallyHiddenText = Some("summary.vessel-details.vesselName"),
          action = (changeCall, "site.change")
        ),
        summaryListRow(
          label = "summary.vessel-details.dateOfArrival",
          value = vesselDetails.dateOfArrival.map(formatDateForDisplay).getOrElse("-"),
          visuallyHiddenText = Some("summary.vessel-details.dateOfArrival"),
          action = (changeCall, "site.change")
        ),
        summaryListRow(
          label = "summary.vessel-details.timeOfArrival",
          value = vesselDetails.timeOfArrival.map(formatTimeForDisplay).getOrElse("-"),
          visuallyHiddenText = Some("summary.vessel-details.timeOfArrival"),
          action = (changeCall, "site.change")
        )
      )
    )

}
