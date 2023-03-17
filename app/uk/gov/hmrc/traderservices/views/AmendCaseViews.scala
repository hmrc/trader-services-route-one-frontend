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

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.traderservices.views.html._

@Singleton
class AmendCaseViews @Inject() (
  val enterCaseReferenceNumberView: EnterCaseReferenceNumberView,
  val selectTypeOfAmendmentView: SelectTypeOfAmendmentView,
  val enterResponseTextView: EnterResponseTextView,
  val uploadFileView: UploadFileView,
  val waitingForFileVerificationView: WaitingForFileVerificationView,
  val fileUploadedView: FileUploadedView,
  val fileUploadedSummaryView: FileUploadedSummaryView,
  val amendCaseSummaryView: AmendCaseSummaryView,
  val uploadMultipleFilesView: UploadMultipleFilesView,
  val amendCaseConfirmationReceiptView: AmendCaseConfirmationReceiptView,
  val amendCaseConfirmationView: AmendCaseConfirmationView,
  val missingInformationErrorView: MissingInformationErrorView,
  val caseAlreadySubmittedView: CaseAlreadySubmittedView
)
