/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.traderservices.views.viewmodels

import play.api.libs.functional.syntax._
import uk.gov.hmrc.govukfrontend.views.html.components._
import play.api.libs.json.{Reads, Writes, __}
import uk.gov.hmrc.govukfrontend.views.viewmodels.FormGroup

case class TimeInput(
  id: String = "",
  namePrefix: Option[String] = None,
  items: Seq[InputItem] = Seq.empty,
  periodSelectItems: Seq[SelectItem] = Seq.empty,
  hint: Option[Hint] = None,
  errorMessage: Option[ErrorMessage] = None,
  formGroup: FormGroup = FormGroup.empty,
  fieldset: Option[Fieldset] = None,
  classes: String = "",
  attributes: Map[String, String] = Map.empty,
  showSelectPeriod: Boolean = true
)

object TimeInput {

  def defaultObject: TimeInput = TimeInput()

  implicit lazy val jsonReads: Reads[TimeInput] =
    (
      (__ \ "id").readWithDefault[String](defaultObject.id) and
        (__ \ "namePrefix").readNullable[String] and
        (__ \ "items").readWithDefault[Seq[InputItem]](defaultObject.items) and
        (__ \ "periodSelectItems").readWithDefault[Seq[SelectItem]](defaultObject.periodSelectItems) and
        (__ \ "hint").readNullable[Hint] and
        (__ \ "errorMessage").readNullable[ErrorMessage] and
        (__ \ "formGroup").readWithDefault[FormGroup](defaultObject.formGroup) and
        (__ \ "fieldset").readNullable[Fieldset] and
        (__ \ "classes").readWithDefault[String](defaultObject.classes) and
        (__ \ "attributes").readWithDefault[Map[String, String]](defaultObject.attributes) and
        (__ \ "showSelectPeriod").readWithDefault[Boolean](defaultObject.showSelectPeriod)
    )(TimeInput.apply _)

  implicit lazy val jsonWrites: Writes[TimeInput] =
    (
      (__ \ "id").write[String] and
        (__ \ "namePrefix").writeNullable[String] and
        (__ \ "items").write[Seq[InputItem]] and
        (__ \ "periodSelectItems").write[Seq[SelectItem]] and
        (__ \ "hint").writeNullable[Hint] and
        (__ \ "errorMessage").writeNullable[ErrorMessage] and
        (__ \ "formGroup").write[FormGroup] and
        (__ \ "fieldset").writeNullable[Fieldset] and
        (__ \ "classes").write[String] and
        (__ \ "attributes").write[Map[String, String]] and
        (__ \ "showSelectPeriod").write[Boolean]
    )(unlift(TimeInput.unapply))

}
