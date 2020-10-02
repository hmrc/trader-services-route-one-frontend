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
import uk.gov.hmrc.traderservices.models.{ExportFreightType, ExportGoodsPriority, ExportRequestType, ExportRouteType}

@Singleton
class ExportQuestionsViewContext extends RadioItemsHelper {

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

  def exportGoodsPriorityItems(form: Form[_])(implicit messages: Messages): Seq[RadioItem] =
    radioItems[ExportGoodsPriority](
      "export-questions",
      "goodsPriority",
      Seq(
        ExportGoodsPriority.None,
        ExportGoodsPriority.LiveAnimals,
        ExportGoodsPriority.HumanRemains,
        ExportGoodsPriority.ExplosivesOrFireworks,
        ExportGoodsPriority.HighValueArt,
        ExportGoodsPriority.ClassADrugs
      ),
      form
    )

  def exportFreightTypeItems(form: Form[_])(implicit messages: Messages): Seq[RadioItem] =
    radioItems[ExportFreightType](
      "export-questions",
      "freightType",
      Seq(
        ExportFreightType.Maritime,
        ExportFreightType.Air,
        ExportFreightType.RORO
      ),
      form
    )

}
