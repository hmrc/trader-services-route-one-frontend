/*
 * Copyright 2021 HM Revenue & Customs
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

sealed trait View {
  def viewFormat: String
}

sealed trait TypeOfAmendment extends View {
  def hasResponse: Boolean
  def hasFiles: Boolean
}

object TypeOfAmendment extends EnumerationFormats[TypeOfAmendment] {

  case object WriteResponse extends TypeOfAmendment {
    final override def viewFormat = "view.amend-case.summary.type.message"
    final override def hasResponse: Boolean = true
    final override def hasFiles: Boolean = false
  }
  case object UploadDocuments extends TypeOfAmendment {
    final override def viewFormat = "view.amend-case.summary.type.documents"
    final override def hasResponse: Boolean = false
    final override def hasFiles: Boolean = true
  }
  case object WriteResponseAndUploadDocuments extends TypeOfAmendment {
    final override def viewFormat = "view.amend-case.summary.type.messageAndDocuments"
    final override def hasResponse: Boolean = true
    final override def hasFiles: Boolean = true
  }

  final val values: Set[TypeOfAmendment] =
    Set(WriteResponse, UploadDocuments, WriteResponseAndUploadDocuments)
}
