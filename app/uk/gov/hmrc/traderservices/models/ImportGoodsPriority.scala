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

package uk.gov.hmrc.traderservices.models

sealed trait ImportGoodsPriority

object ImportGoodsPriority extends EnumerationFormats[ImportGoodsPriority] {

  case object None extends ImportGoodsPriority
  case object LiveAnimals extends ImportGoodsPriority
  case object HumanRemains extends ImportGoodsPriority
  case object ExplosivesOrFireworks extends ImportGoodsPriority
  case object HighValueArt extends ImportGoodsPriority
  case object ClassADrugs extends ImportGoodsPriority

  val values = Set(None, LiveAnimals, HumanRemains, ExplosivesOrFireworks, HighValueArt, ClassADrugs)
}
