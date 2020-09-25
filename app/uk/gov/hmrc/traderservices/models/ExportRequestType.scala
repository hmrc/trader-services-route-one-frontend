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

sealed trait ExportRequestType

object ExportRequestType extends EnumerationFormats[ExportRequestType] {

  case object New extends ExportRequestType
  case object Cancellation extends ExportRequestType
  case object Hold extends ExportRequestType
  case object C1601 extends ExportRequestType
  case object C1602 extends ExportRequestType
  case object C1603 extends ExportRequestType
  case object WithdrawalOrReturn extends ExportRequestType

  val values = Set(New, Cancellation, Hold, C1601, C1602, C1603, WithdrawalOrReturn)
}
