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
import uk.gov.hmrc.traderservices.models.{ImportFreightType, ImportPriorityGoods, ImportRequestType, ImportRouteType}
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text

@Singleton
class ImportQuestionsViewContext extends RadioItemsHelper {

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

  def importGoodsPriorityItems(form: Form[_])(implicit messages: Messages): Seq[RadioItem] =
    radioItems[ImportPriorityGoods](
      "import-questions",
      "hasPriorityGoods",
      Seq(
        ImportPriorityGoods.None,
        ImportPriorityGoods.LiveAnimals,
        ImportPriorityGoods.HumanRemains,
        ImportPriorityGoods.ExplosivesOrFireworks,
        ImportPriorityGoods.HighValueArt,
        ImportPriorityGoods.ClassADrugs
      ),
      form
    )

  def importFreightTypeItems(form: Form[_])(implicit messages: Messages): Seq[RadioItem] =
    radioItems[ImportFreightType](
      "import-questions",
      "freightType",
      Seq(
        ImportFreightType.Maritime,
        ImportFreightType.Air,
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
}
