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
import uk.gov.hmrc.govukfrontend.views.viewmodels.checkboxes.CheckboxItem
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.traderservices.models.{ExportFreightType, ExportPriorityGoods, ExportRequestType, ExportRouteType}

@Singleton
class ExportQuestionsViewContext extends RadioItemsHelper with CheckboxItemsHelper {

  def exportRequestTypeItems(form: Form[_])(implicit messages: Messages): Seq[RadioItem] =
    radioItems[ExportRequestType](
      "export-questions",
      "requestType",
      Seq(
        ExportRequestType.New,
        ExportRequestType.Cancellation,
        ExportRequestType.C1601,
        ExportRequestType.C1602,
        ExportRequestType.C1603,
        ExportRequestType.Hold,
        ExportRequestType.WithdrawalOrReturn
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
    ExportPriorityGoods.ClassADrugs,
    ExportPriorityGoods.ExplosivesOrFireworks,
    ExportPriorityGoods.HighValueArt,
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

}
