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
import uk.gov.hmrc.traderservices.models.{ImportFreightType, ImportPriorityGoods, ImportQuestions, ImportRequestType, ImportRouteType}
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryList
import uk.gov.hmrc.traderservices.controllers.routes.TraderServicesFrontendController

@Singleton
class ImportQuestionsViewContext
    extends RadioItemsHelper with SummaryListRowHelper with DateTimeFormatHelper with DeclarationDetailsHelper
    with VesselDetailsHelper with ContactDetailsHelper {

  def importRequestTypeItems(form: Form[_])(implicit messages: Messages): Seq[RadioItem] =
    radioItems[ImportRequestType](
      "import-questions",
      "requestType",
      Seq(
        ImportRequestType.New,
        ImportRequestType.Cancellation,
        ImportRequestType.Hold
      ),
      form
    )

  def importRouteTypeItems(form: Form[_])(implicit messages: Messages): Seq[RadioItem] =
    radioItems[ImportRouteType](
      "import-questions",
      "routeType",
      Seq(
        ImportRouteType.Route1,
        ImportRouteType.Route1Cap,
        ImportRouteType.Route2,
        ImportRouteType.Route3,
        ImportRouteType.Route6,
        ImportRouteType.Hold
      ),
      form
    )

  val importPriorityGoodsList = Seq(
    ImportPriorityGoods.ClassADrugs,
    ImportPriorityGoods.ExplosivesOrFireworks,
    ImportPriorityGoods.HighValueArt,
    ImportPriorityGoods.HumanRemains,
    ImportPriorityGoods.LiveAnimals
  )

  def importPriorityGoodsMessageKeys(messagePrefix: String): Seq[String] =
    importPriorityGoodsList.map(key => s"$messagePrefix.$key")

  def importHasPriorityGoodsItems(form: Form[_])(implicit messages: Messages): Seq[RadioItem] =
    Seq(
      RadioItem(
        value = Some("yes"),
        content = Text(messages(s"form.import-questions.hasPriorityGoods.yes")),
        checked = form("hasPriorityGoods").value.contains("yes")
      ),
      RadioItem(
        value = Some("no"),
        content = Text(messages(s"form.import-questions.hasPriorityGoods.no")),
        checked = form("hasPriorityGoods").value.contains("no")
      )
    )

  def importPriorityGoodsItems(form: Form[_])(implicit messages: Messages): Seq[RadioItem] =
    radioItems[ImportPriorityGoods](
      "import-questions",
      "priorityGoods",
      importPriorityGoodsList,
      form
    )

  def importFreightTypeItems(form: Form[_])(implicit messages: Messages): Seq[RadioItem] =
    radioItems[ImportFreightType](
      "import-questions",
      "freightType",
      Seq(
        ImportFreightType.Air,
        ImportFreightType.Maritime,
        ImportFreightType.RORO
      ),
      form
    )

  def importHasALVSItems(form: Form[_])(implicit messages: Messages): Seq[RadioItem] =
    Seq(
      RadioItem(
        value = Some("yes"),
        content = Text(messages(s"form.import-questions.hasALVS.yes")),
        checked = form("hasALVS").value.contains("yes")
      ),
      RadioItem(
        value = Some("no"),
        content = Text(messages(s"form.import-questions.hasALVS.no")),
        checked = form("hasALVS").value.contains("no")
      )
    )

  def summaryListOfImportQuestions(importQuestions: ImportQuestions)(implicit messages: Messages): SummaryList = {

    val requestTypeRows = Seq(
      summaryListRow(
        label = "summary.import-questions.requestType",
        value = importQuestions.requestType
          .flatMap(ImportRequestType.keyOf)
          .map(key => messages(s"form.import-questions.requestType.$key"))
          .getOrElse("-"),
        visuallyHiddenText = Some("summary.import-questions.requestType"),
        action = (TraderServicesFrontendController.showAnswerImportQuestionsRequestType(), "site.change")
      )
    )

    val routeTypeRows =
      if (importQuestions.shouldAskRouteQuestion)
        Seq(
          summaryListRow(
            label = "summary.import-questions.routeType",
            value = importQuestions.routeType
              .flatMap(ImportRouteType.keyOf)
              .map(key => messages(s"form.import-questions.routeType.$key"))
              .getOrElse("-"),
            visuallyHiddenText = Some("summary.import-questions.routeType"),
            action = (TraderServicesFrontendController.showAnswerImportQuestionsRouteType(), "site.change")
          )
        )
      else Seq.empty

    val hasPriorityGoodsRows = Seq(
      summaryListRow(
        label = "summary.import-questions.hasPriorityGoods",
        value =
          if (importQuestions.hasPriorityGoods.getOrElse(false))
            messages(s"form.import-questions.hasPriorityGoods.yes")
          else messages(s"form.import-questions.hasPriorityGoods.no"),
        visuallyHiddenText = Some("summary.import-questions.hasPriorityGoods"),
        action = (TraderServicesFrontendController.showAnswerImportQuestionsHasPriorityGoods(), "site.change")
      )
    )

    val whichPriorityGoodsRows =
      if (importQuestions.hasPriorityGoods.contains(true))
        Seq(
          summaryListRow(
            label = "summary.import-questions.whichPriorityGoods",
            value = importQuestions.priorityGoods
              .flatMap(ImportPriorityGoods.keyOf)
              .map(key => messages(s"form.import-questions.priorityGoods.$key"))
              .getOrElse("-"),
            visuallyHiddenText = Some("summary.import-questions.whichPriorityGoods"),
            action = (TraderServicesFrontendController.showAnswerImportQuestionsWhichPriorityGoods(), "site.change")
          )
        )
      else Seq.empty

    val hasALVSRows = Seq(
      summaryListRow(
        label = "summary.import-questions.hasALVS",
        value =
          if (importQuestions.hasALVS.getOrElse(false))
            messages(s"form.import-questions.hasALVS.yes")
          else messages(s"form.import-questions.hasALVS.no"),
        visuallyHiddenText = Some("summary.import-questions.hasALVS"),
        action = (TraderServicesFrontendController.showAnswerImportQuestionsALVS(), "site.change")
      )
    )

    val freightTypeRows = Seq(
      summaryListRow(
        label = "summary.import-questions.freightType",
        value = importQuestions.freightType
          .flatMap(ImportFreightType.keyOf)
          .map(key => messages(s"form.import-questions.freightType.$key"))
          .getOrElse("-"),
        visuallyHiddenText = Some("summary.import-questions.freightType"),
        action = (TraderServicesFrontendController.showAnswerImportQuestionsFreightType(), "site.change")
      )
    )

    SummaryList(
      requestTypeRows ++ routeTypeRows ++ hasPriorityGoodsRows ++ whichPriorityGoodsRows ++ hasALVSRows ++ freightTypeRows
    )
  }

}
