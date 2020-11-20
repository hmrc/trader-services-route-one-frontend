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

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.traderservices.views.html._

@Singleton
class CreateCaseViews @Inject() (
  val chooseNewOrExistingCaseView: ChooseNewOrExistingCaseView,
  val declarationDetailsEntryView: DeclarationDetailsEntryView,
  val exportQuestionsRequestTypeView: ExportQuestionsRequestTypeView,
  val exportQuestionsRouteTypeView: ExportQuestionsRouteTypeView,
  val exportQuestionsHasPriorityGoodsView: ExportQuestionsHasPriorityGoodsView,
  val exportQuestionsWhichPriorityGoodsView: ExportQuestionsWhichPriorityGoodsView,
  val exportQuestionsFreightTypeView: ExportQuestionsFreightTypeView,
  val exportQuestionsMandatoryVesselDetailsView: ExportQuestionsMandatoryVesselDetailsView,
  val exportQuestionsOptionalVesselDetailsView: ExportQuestionsOptionalVesselDetailsView,
  val exportQuestionsContactInfoView: ExportQuestionsContactInfoView,
  val exportQuestionsSummaryView: ExportQuestionsSummaryView,
  val importQuestionsRequestTypeView: ImportQuestionsRequestTypeView,
  val importQuestionsRouteTypeView: ImportQuestionsRouteTypeView,
  val importQuestionsHasPriorityGoodsView: ImportQuestionsHasPriorityGoodsView,
  val importQuestionsWhichPriorityGoodsView: ImportQuestionsWhichPriorityGoodsView,
  val importQuestionsALVSView: ImportQuestionsALVSView,
  val importQuestionsFreightTypeView: ImportQuestionsFreightTypeView,
  val importQuestionsMandatoryVesselDetailsView: ImportQuestionsMandatoryVesselDetailsView,
  val importQuestionsOptionalVesselDetailsView: ImportQuestionsOptionalVesselDetailsView,
  val importQuestionsContactInfoView: ImportQuestionsContactInfoView,
  val importQuestionsSummaryView: ImportQuestionsSummaryView,
  val uploadFileView: UploadFileView,
  val waitingForFileVerificationView: WaitingForFileVerificationView,
  val fileUploadedView: FileUploadedView,
  val fileUploadedSummaryView: FileUploadedSummaryView,
  val createCaseConfirmationView: CreateCaseConfirmationView,
  val caseAlreadyExistsView: CaseAlreadyExistsView
)
