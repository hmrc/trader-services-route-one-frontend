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

import javax.inject.Singleton
import play.api.data.Form
import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem
import uk.gov.hmrc.traderservices.models.{ExportFreightType, ExportPriorityGoods, ExportQuestions, ExportRequestType, ExportRouteType}
import uk.gov.hmrc.traderservices.controllers.routes.CreateCaseJourneyController
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryList
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import play.api.mvc.Call
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel

@Singleton
class ExportQuestionsViewContext
    extends RadioItemsHelper with CheckboxItemsHelper with SummaryListRowHelper with DateTimeFormatHelper
    with DeclarationDetailsHelper with VesselDetailsHelper with ContactDetailsHelper {

  def exportRequestTypeItems(form: Form[_])(implicit messages: Messages): Seq[RadioItem] =
    radioItems[ExportRequestType](
      "export-questions",
      "requestType",
      Seq(
        ExportRequestType.New,
        ExportRequestType.Cancellation,
        ExportRequestType.WithdrawalOrReturn,
        ExportRequestType.C1601,
        ExportRequestType.C1602,
        ExportRequestType.C1603
      ),
      form
    )

  def exportRouteTypeItems(form: Form[_])(implicit messages: Messages): Seq[RadioItem] =
    radioItems[ExportRouteType](
      "export-questions",
      "routeType",
      Seq(
        ExportRouteType.Route1,
        ExportRouteType.Route1Cap,
        ExportRouteType.Route2,
        ExportRouteType.Route3,
        ExportRouteType.Route6,
        ExportRouteType.Hold
      ),
      form
    )

  val exportPriorityGoodsList = Seq(
    ExportPriorityGoods.ExplosivesOrFireworks,
    ExportPriorityGoods.HumanRemains,
    ExportPriorityGoods.LiveAnimals
  )

  def exportPriorityGoodsItems(form: Form[_])(implicit messages: Messages): Seq[RadioItem] =
    radioItems[ExportPriorityGoods](
      "export-questions",
      "priorityGoods",
      exportPriorityGoodsList,
      form
    )

  def exportPriorityGoodsMessageKeys(messagePrefix: String): Seq[String] =
    exportPriorityGoodsList.map(key => s"$messagePrefix.$key")

  def exportHasPriorityGoodsItems(form: Form[_])(implicit messages: Messages): Seq[RadioItem] =
    Seq(
      RadioItem(
        value = Some("yes"),
        content = Text(messages(s"form.export-questions.hasPriorityGoods.yes")),
        checked = form("hasPriorityGoods").value.contains("yes")
      ),
      RadioItem(
        value = Some("no"),
        content = Text(messages(s"form.export-questions.hasPriorityGoods.no")),
        checked = form("hasPriorityGoods").value.contains("no")
      )
    )

  def getChangeCallForVesselDetails(exportQuestions: ExportQuestions): Call =
    if (CreateCaseJourneyModel.Rules.isVesselDetailsAnswerMandatory(exportQuestions))
      CreateCaseJourneyController.showAnswerExportQuestionsMandatoryVesselInfo
    else
      CreateCaseJourneyController.showAnswerExportQuestionsOptionalVesselInfo

  def exportFreightTypeItems(form: Form[_])(implicit messages: Messages): Seq[RadioItem] =
    radioItems[ExportFreightType](
      "export-questions",
      "freightType",
      Seq(
        ExportFreightType.Air,
        ExportFreightType.Maritime,
        ExportFreightType.RORO
      ),
      form
    )

  def summaryListOfExportQuestions(exportQuestions: ExportQuestions)(implicit messages: Messages): SummaryList = {

    val requestTypeRows = Seq(
      summaryListRow(
        label = "summary.export-questions.requestType",
        value = exportQuestions.requestType
          .flatMap(ExportRequestType.keyOf)
          .map(key => messages(s"form.export-questions.requestType.$key"))
          .getOrElse("-"),
        visuallyHiddenText = Some("summary.export-questions.requestType"),
        action = (CreateCaseJourneyController.showAnswerExportQuestionsRequestType, "site.change")
      )
    )

    val routeTypeRows =
      Seq(
        summaryListRow(
          label = "summary.export-questions.routeType",
          value = exportQuestions.routeType
            .flatMap(ExportRouteType.keyOf)
            .map(key => messages(s"form.export-questions.routeType.$key"))
            .getOrElse("-"),
          visuallyHiddenText = Some("summary.export-questions.routeType"),
          action = (CreateCaseJourneyController.showAnswerExportQuestionsRouteType, "site.change")
        )
      )

    val hasPriorityGoodsRows = Seq(
      summaryListRow(
        label = "summary.export-questions.hasPriorityGoods",
        value =
          if (exportQuestions.hasPriorityGoods.getOrElse(false))
            messages(s"form.export-questions.hasPriorityGoods.yes")
          else messages(s"form.export-questions.hasPriorityGoods.no"),
        visuallyHiddenText = Some("summary.export-questions.hasPriorityGoods"),
        action = (CreateCaseJourneyController.showAnswerExportQuestionsHasPriorityGoods, "site.change")
      )
    )

    val whichPriorityGoodsRows =
      if (exportQuestions.hasPriorityGoods.contains(true))
        Seq(
          summaryListRow(
            label = "summary.export-questions.whichPriorityGoods",
            value = exportQuestions.priorityGoods
              .flatMap(ExportPriorityGoods.keyOf)
              .map(key => messages(s"form.export-questions.priorityGoods.$key"))
              .getOrElse("-"),
            visuallyHiddenText = Some("summary.export-questions.whichPriorityGoods"),
            action = (CreateCaseJourneyController.showAnswerExportQuestionsWhichPriorityGoods, "site.change")
          )
        )
      else Seq.empty

    val freightTypeRows = Seq(
      summaryListRow(
        label = "summary.export-questions.freightType",
        value = exportQuestions.freightType
          .flatMap(ExportFreightType.keyOf)
          .map(key => messages(s"form.export-questions.freightType.$key"))
          .getOrElse("-"),
        visuallyHiddenText = Some("summary.export-questions.freightType"),
        action = (CreateCaseJourneyController.showAnswerExportQuestionsFreightType, "site.change")
      )
    )

    SummaryList(
      requestTypeRows ++ routeTypeRows ++ hasPriorityGoodsRows ++ whichPriorityGoodsRows ++ freightTypeRows
    )
  }

}
