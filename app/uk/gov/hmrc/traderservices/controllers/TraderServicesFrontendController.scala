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

package uk.gov.hmrc.traderservices.controllers

import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.play.fsm.{JourneyController, JourneyIdSupport}
import uk.gov.hmrc.traderservices.connectors.{FrontendAuthConnector, TraderServicesApiConnector}
import uk.gov.hmrc.traderservices.journeys.TraderServicesFrontendJourneyModel.State._
import uk.gov.hmrc.traderservices.models.{DeclarationDetails, ExportContactInfo, ExportFreightType, ExportPriorityGoods, ExportRequestType, ExportRouteType, FileVerificationStatus, ImportContactInfo, ImportFreightType, ImportPriorityGoods, ImportRequestType, ImportRouteType, S3UploadError, UpscanNotification, VesselDetails}
import uk.gov.hmrc.traderservices.services.TraderServicesFrontendJourneyServiceWithHeaderCarrier
import uk.gov.hmrc.traderservices.wiring.AppConfig

import scala.concurrent.ExecutionContext
import uk.gov.hmrc.traderservices.connectors.UpscanInitiateConnector
import play.api.libs.json.Json

@Singleton
class TraderServicesFrontendController @Inject() (
  appConfig: AppConfig,
  override val messagesApi: MessagesApi,
  traderServicesApiConnector: TraderServicesApiConnector,
  upscanInitiateConnector: UpscanInitiateConnector,
  val authConnector: FrontendAuthConnector,
  val env: Environment,
  override val journeyService: TraderServicesFrontendJourneyServiceWithHeaderCarrier,
  controllerComponents: MessagesControllerComponents,
  views: uk.gov.hmrc.traderservices.views.Views
)(implicit val config: Configuration, ec: ExecutionContext)
    extends FrontendController(controllerComponents) with I18nSupport with AuthActions
    with JourneyController[HeaderCarrier] with JourneyIdSupport[HeaderCarrier] {

  import TraderServicesFrontendController._
  import uk.gov.hmrc.traderservices.journeys.TraderServicesFrontendJourneyModel._

  /** AsUser authorisation request */
  val AsUser: WithAuthorised[String] = { implicit request =>
    authorisedWithEnrolment(appConfig.authorisedServiceName, appConfig.authorisedIdentifierKey)
  }

  /** Base authorized action builder */
  val whenAuthorisedAsUser = actions.whenAuthorised(AsUser)

  /** Dummy action to use only when developing to fill loose-ends. */
  private val actionNotYetImplemented = Action(NotImplemented)

  def toSubscriptionJourney(continueUrl: String): Result =
    Redirect(
      appConfig.subscriptionJourneyUrl,
      Map(
        "continue" -> Seq(continueUrl)
      )
    )

  // Dummy URL to use when developing the journey
  val workInProgresDeadEndCall = Call("GET", "/trader-services/work-in-progress")

  // GET /
  val showStart: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.Start.type]
      .orApply(Transitions.start)
      .andCleanBreadcrumbs()

  // GET /pre-clearance/declaration-details
  val showEnterDeclarationDetails: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.EnterDeclarationDetails]
      .using(Mergers.copyDeclarationDetails)
      .orApply(Transitions.enterDeclarationDetails)

  // POST /pre-clearance/declaration-details
  val submitDeclarationDetails: Action[AnyContent] =
    whenAuthorisedAsUser.bindForm(DeclarationDetailsForm).apply(Transitions.submittedDeclarationDetails)

  // ----------------------- EXPORT QUESTIONS -----------------------

  // GET /pre-clearance/export-questions/request-type
  val showAnswerExportQuestionsRequestType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsRequestType]
      .using(Mergers.copyExportQuestionsStateModel[AnswerExportQuestionsRequestType])

  // POST /pre-clearance/export-questions/request-type
  val submitExportQuestionsRequestTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportRequestTypeForm)
      .apply(Transitions.submittedExportQuestionsAnswerRequestType)

  // GET /pre-clearance/export-questions/route-type
  val showAnswerExportQuestionsRouteType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsRouteType]
      .using(Mergers.copyExportQuestionsStateModel[AnswerExportQuestionsRouteType])

  // POST /pre-clearance/export-questions/route-type
  val submitExportQuestionsRouteTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportRouteTypeForm)
      .apply(Transitions.submittedExportQuestionsAnswerRouteType)

  // GET /pre-clearance/export-questions/has-priority-goods
  val showAnswerExportQuestionsHasPriorityGoods: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsHasPriorityGoods]
      .using(Mergers.copyExportQuestionsStateModel[AnswerExportQuestionsHasPriorityGoods])

  // POST /pre-clearance/export-questions/has-priority-goods
  val submitExportQuestionsHasPriorityGoodsAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportHasPriorityGoodsForm)
      .apply(Transitions.submittedExportQuestionsAnswerHasPriorityGoods)

  // GET /pre-clearance/export-questions/which-priority-goods
  val showAnswerExportQuestionsWhichPriorityGoods: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsWhichPriorityGoods]
      .using(Mergers.copyExportQuestionsStateModel[AnswerExportQuestionsWhichPriorityGoods])

  // POST /pre-clearance/export-questions/which-priority-goods
  val submitExportQuestionsWhichPriorityGoodsAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportPriorityGoodsForm)
      .apply(Transitions.submittedExportQuestionsAnswerWhichPriorityGoods)

  // GET /pre-clearance/export-questions/transport-type
  val showAnswerExportQuestionsFreightType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsFreightType]
      .using(Mergers.copyExportQuestionsStateModel[AnswerExportQuestionsFreightType])

  // POST /pre-clearance/export-questions/transport-type
  val submitExportQuestionsFreightTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportFreightTypeForm)
      .apply(Transitions.submittedExportQuestionsAnswerFreightType)

  // GET /pre-clearance/export-questions/vessel-info-required
  val showAnswerExportQuestionsMandatoryVesselInfo: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsMandatoryVesselInfo]
      .using(Mergers.copyExportQuestionsStateModel[AnswerExportQuestionsMandatoryVesselInfo])

  // POST /pre-clearance/export-questions/vessel-info-required
  val submitExportQuestionsMandatoryVesselInfoAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(MandatoryVesselDetailsForm)
      .apply(Transitions.submittedExportQuestionsMandatoryVesselDetails)

  // GET /pre-clearance/export-questions/vessel-info
  val showAnswerExportQuestionsOptionalVesselInfo: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsOptionalVesselInfo]
      .using(Mergers.copyExportQuestionsStateModel[AnswerExportQuestionsOptionalVesselInfo])

  // POST /pre-clearance/export-questions/vessel-info
  val submitExportQuestionsOptionalVesselInfoAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(OptionalVesselDetailsForm)
      .apply(Transitions.submittedExportQuestionsOptionalVesselDetails)

  // GET /pre-clearance/export-questions/contact-info
  val showAnswerExportQuestionsContactInfo: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsContactInfo]
      .using(Mergers.copyExportQuestionsStateModel[AnswerExportQuestionsContactInfo])

  // POST /pre-clearance/export-questions/contact-info
  val submitExportQuestionsContactInfoAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportContactForm)
      .apply(Transitions.submittedExportQuestionsContactInfo)

  // GET /pre-clearance/export-questions/summary
  val showExportQuestionsSummary: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.ExportQuestionsSummary]
      .using(Mergers.copyExportQuestionsStateModel[ExportQuestionsSummary])

  // ----------------------- IMPORT QUESTIONS -----------------------

  // GET /pre-clearance/import-questions/request-type
  val showAnswerImportQuestionsRequestType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsRequestType]
      .using(Mergers.copyImportQuestionsStateModel[AnswerImportQuestionsRequestType])

  // POST /pre-clearance/import-questions/request-type
  val submitImportQuestionsRequestTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportRequestTypeForm)
      .apply(Transitions.submittedImportQuestionsAnswersRequestType)

  // GET /pre-clearance/import-questions/route-type
  val showAnswerImportQuestionsRouteType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsRouteType]
      .using(Mergers.copyImportQuestionsStateModel[AnswerImportQuestionsRouteType])

  // POST /pre-clearance/import-questions/route-type
  val submitImportQuestionsRouteTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportRouteTypeForm)
      .apply(Transitions.submittedImportQuestionsAnswerRouteType)

  // GET /pre-clearance/import-questions/has-priority-goods
  val showAnswerImportQuestionsHasPriorityGoods: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsHasPriorityGoods]
      .using(Mergers.copyImportQuestionsStateModel[AnswerImportQuestionsHasPriorityGoods])

  // POST /pre-clearance/import-questions/has-priority-goods
  val submitImportQuestionsHasPriorityGoodsAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportHasPriorityGoodsForm)
      .apply(Transitions.submittedImportQuestionsAnswerHasPriorityGoods)

  // GET /pre-clearance/import-questions/which-priority-goods
  val showAnswerImportQuestionsWhichPriorityGoods: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsWhichPriorityGoods]
      .using(Mergers.copyImportQuestionsStateModel[AnswerImportQuestionsWhichPriorityGoods])

  // POST /pre-clearance/import-questions/which-priority-goods
  val submitImportQuestionsWhichPriorityGoodsAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportPriorityGoodsForm)
      .apply(Transitions.submittedImportQuestionsAnswerWhichPriorityGoods)

  // GET /pre-clearance/import-questions/automatic-licence-verification
  val showAnswerImportQuestionsALVS: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsALVS]
      .using(Mergers.copyImportQuestionsStateModel[AnswerImportQuestionsALVS])

  // POST /pre-clearance/import-questions/automatic-licence-verification
  val submitImportQuestionsALVSAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportHasALVSForm)
      .apply(Transitions.submittedImportQuestionsAnswerHasALVS)

  // GET /pre-clearance/import-questions/transport-type
  val showAnswerImportQuestionsFreightType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsFreightType]
      .using(Mergers.copyImportQuestionsStateModel[AnswerImportQuestionsFreightType])

  // POST /pre-clearance/import-questions/transport-type
  val submitImportQuestionsFreightTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportFreightTypeForm)
      .apply(Transitions.submittedImportQuestionsAnswerFreightType)

  // GET /pre-clearance/import-questions/vessel-info-required
  val showAnswerImportQuestionsMandatoryVesselInfo: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsMandatoryVesselInfo]
      .using(Mergers.copyImportQuestionsStateModel[AnswerImportQuestionsMandatoryVesselInfo])

  // POST /pre-clearance/import-questions/vessel-info-required
  val submitImportQuestionsMandatoryVesselInfoAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(MandatoryVesselDetailsForm)
      .apply(Transitions.submittedImportQuestionsMandatoryVesselDetails)

  // GET /pre-clearance/import-questions/vessel-info
  val showAnswerImportQuestionsOptionalVesselInfo: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsOptionalVesselInfo]
      .using(Mergers.copyImportQuestionsStateModel[AnswerImportQuestionsOptionalVesselInfo])

  // POST /pre-clearance/import-questions/vessel-info
  val submitImportQuestionsOptionalVesselInfoAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(OptionalVesselDetailsForm)
      .apply(Transitions.submittedImportQuestionsOptionalVesselDetails)

  // GET /pre-clearance/import-questions/contact-info
  val showAnswerImportQuestionsContactInfo: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsContactInfo]
      .using(Mergers.copyImportQuestionsStateModel[AnswerImportQuestionsContactInfo])

  // POST /pre-clearance/import-questions/contact-info
  val submitImportQuestionsContactInfoAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportContactForm)
      .apply(Transitions.submittedImportQuestionsContactInfo)

  // GET /pre-clearance/import-questions/summary
  val showImportQuestionsSummary: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.ImportQuestionsSummary]
      .using(Mergers.copyImportQuestionsStateModel[ImportQuestionsSummary])

  // ----------------------- FILES UPLOAD -----------------------

  val successRedirect =
    appConfig.baseExternalCallbackUrl + routes.TraderServicesFrontendController.showWaitingForFileVerification

  val errorRedirect =
    appConfig.baseExternalCallbackUrl + routes.TraderServicesFrontendController.markFileUploadAsRejected

  // GET /pre-clearance/file-upload
  val showFileUpload: Action[AnyContent] =
    whenAuthorisedAsUser
      .applyWithRequest { implicit request =>
        val callbackUrl =
          appConfig.baseInternalCallbackUrl + routes.TraderServicesFrontendController
            .callbackFromUpscan(currentJourneyId)
            .url
        Transitions
          .initiateFileUpload(callbackUrl, successRedirect, errorRedirect, appConfig.fileFormats.maxFileSizeMb)(
            upscanInitiateConnector.initiate(_)
          )
      }
      .redirectOrDisplayIf[State.UploadFile]

  // GET
  val markFileUploadAsRejected: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(UpscanUploadErrorForm)
      .apply(Transitions.fileUploadWasRejected)

  // GET /pre-clearance/file-verification
  val showWaitingForFileVerification: Action[AnyContent] =
    whenAuthorisedAsUser
      .waitForStateThenRedirect[State.FileUploaded](3)
      .orApplyOnTimeout(_ => Transitions.waitForFileVerification)
      .redirectOrDisplayIf[State.WaitingForFileVerification]

  // POST /pre-clearance/journey/:journeyId/callback-from-upscan
  def callbackFromUpscan(journeyId: String): Action[AnyContent] =
    actions
      .parseJson[UpscanNotification]
      .apply(Transitions.upscanCallbackArrived)
      .transform { case _ => Accepted }
      .recover {
        case e: IllegalArgumentException => BadRequest
        case e                           => InternalServerError
      }

  // GET /pre-clearance/file-uploaded
  val showFileUploaded: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.FileUploaded]

  // POST /pre-clearance/file-uploaded
  val submitUploadAnotherFileChoice: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm[Boolean](UploadAnotherFileChoiceForm)
      .applyWithRequest { implicit request =>
        val callbackUrl =
          appConfig.baseInternalCallbackUrl + routes.TraderServicesFrontendController
            .callbackFromUpscan(currentJourneyId)
            .url
        Transitions.submitedUploadAnotherFileChoice(
          callbackUrl,
          successRedirect,
          errorRedirect,
          appConfig.fileFormats.maxFileSizeMb
        )(
          upscanInitiateConnector.initiate(_)
        ) _
      }

  // GET /pre-clearance/file-uploaded/:reference/remove
  def removeFileUploadByReference(reference: String): Action[AnyContent] =
    whenAuthorisedAsUser
      .applyWithRequest { implicit request =>
        val callbackUrl =
          appConfig.baseInternalCallbackUrl + routes.TraderServicesFrontendController
            .callbackFromUpscan(currentJourneyId)
            .url
        Transitions.removeFileUploadByReference(reference)(
          callbackUrl,
          successRedirect,
          errorRedirect,
          appConfig.fileFormats.maxFileSizeMb
        )(
          upscanInitiateConnector.initiate(_)
        ) _
      }

  // GET /pre-clearance/file-verification/:reference/status
  def checkFileVerificationStatus(reference: String): Action[AnyContent] =
    whenAuthorisedAsUser.showCurrentState
      .displayUsing(implicit request => renderFileVerificationStatus(reference))

  /**
    * Function from the `State` to the `Call` (route),
    * used by play-fsm internally to create redirects.
    */
  override def getCallFor(state: State)(implicit request: Request[_]): Call =
    state match {
      case Start =>
        routes.TraderServicesFrontendController.showStart()

      case _: EnterDeclarationDetails =>
        routes.TraderServicesFrontendController.showEnterDeclarationDetails()

      case _: AnswerExportQuestionsRequestType =>
        routes.TraderServicesFrontendController.showAnswerExportQuestionsRequestType()

      case _: AnswerExportQuestionsRouteType =>
        routes.TraderServicesFrontendController.showAnswerExportQuestionsRouteType()

      case _: AnswerExportQuestionsHasPriorityGoods =>
        routes.TraderServicesFrontendController.showAnswerExportQuestionsHasPriorityGoods()

      case _: AnswerExportQuestionsWhichPriorityGoods =>
        routes.TraderServicesFrontendController.showAnswerExportQuestionsWhichPriorityGoods()

      case _: AnswerExportQuestionsFreightType =>
        routes.TraderServicesFrontendController.showAnswerExportQuestionsFreightType()

      case _: AnswerExportQuestionsMandatoryVesselInfo =>
        routes.TraderServicesFrontendController.showAnswerExportQuestionsMandatoryVesselInfo()

      case _: AnswerExportQuestionsOptionalVesselInfo =>
        routes.TraderServicesFrontendController.showAnswerExportQuestionsOptionalVesselInfo()

      case _: AnswerExportQuestionsContactInfo =>
        routes.TraderServicesFrontendController.showAnswerExportQuestionsContactInfo()

      case _: ExportQuestionsSummary =>
        routes.TraderServicesFrontendController.showExportQuestionsSummary()

      case _: AnswerImportQuestionsRequestType =>
        routes.TraderServicesFrontendController.showAnswerImportQuestionsRequestType()

      case _: AnswerImportQuestionsRouteType =>
        routes.TraderServicesFrontendController.showAnswerImportQuestionsRouteType()

      case _: AnswerImportQuestionsHasPriorityGoods =>
        routes.TraderServicesFrontendController.showAnswerImportQuestionsHasPriorityGoods()

      case _: AnswerImportQuestionsWhichPriorityGoods =>
        routes.TraderServicesFrontendController.showAnswerImportQuestionsWhichPriorityGoods()

      case _: AnswerImportQuestionsALVS =>
        routes.TraderServicesFrontendController.showAnswerImportQuestionsALVS()

      case _: AnswerImportQuestionsFreightType =>
        routes.TraderServicesFrontendController.showAnswerImportQuestionsFreightType()

      case _: AnswerImportQuestionsMandatoryVesselInfo =>
        routes.TraderServicesFrontendController.showAnswerImportQuestionsMandatoryVesselInfo()

      case _: AnswerImportQuestionsOptionalVesselInfo =>
        routes.TraderServicesFrontendController.showAnswerImportQuestionsOptionalVesselInfo()

      case _: AnswerImportQuestionsContactInfo =>
        routes.TraderServicesFrontendController.showAnswerImportQuestionsContactInfo()

      case _: ImportQuestionsSummary =>
        routes.TraderServicesFrontendController.showImportQuestionsSummary()

      case _: UploadFile =>
        routes.TraderServicesFrontendController.showFileUpload()

      case _: WaitingForFileVerification =>
        routes.TraderServicesFrontendController.showWaitingForFileVerification()

      case _: FileUploaded =>
        routes.TraderServicesFrontendController.showFileUploaded()

      case _ =>
        workInProgresDeadEndCall

    }

  import uk.gov.hmrc.play.fsm.OptionalFormOps._

  /**
    * Function from the `State` to the `Result`,
    * used by play-fsm internally to render the actual content.
    */
  override def renderState(state: State, breadcrumbs: List[State], formWithErrors: Option[Form[_]])(implicit
    request: Request[_]
  ): Result =
    state match {

      case Start =>
        Ok(views.startView(routes.TraderServicesFrontendController.showEnterDeclarationDetails()))

      case EnterDeclarationDetails(declarationDetailsOpt, _, _, _) =>
        Ok(
          views.declarationDetailsEntryView(
            formWithErrors.or(DeclarationDetailsForm, declarationDetailsOpt),
            routes.TraderServicesFrontendController.submitDeclarationDetails(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsRequestType(model) =>
        Ok(
          views.exportQuestionsRequestTypeView(
            formWithErrors.or(ExportRequestTypeForm, model.exportQuestionsAnswers.requestType),
            routes.TraderServicesFrontendController.submitExportQuestionsRequestTypeAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsRouteType(model) =>
        Ok(
          views.exportQuestionsRouteTypeView(
            formWithErrors.or(ExportRouteTypeForm, model.exportQuestionsAnswers.routeType),
            routes.TraderServicesFrontendController.submitExportQuestionsRouteTypeAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsHasPriorityGoods(model) =>
        Ok(
          views.exportQuestionsHasPriorityGoodsView(
            formWithErrors.or(ExportHasPriorityGoodsForm, model.exportQuestionsAnswers.hasPriorityGoods),
            routes.TraderServicesFrontendController.submitExportQuestionsHasPriorityGoodsAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsWhichPriorityGoods(model) =>
        Ok(
          views.exportQuestionsWhichPriorityGoodsView(
            formWithErrors.or(ExportPriorityGoodsForm, model.exportQuestionsAnswers.priorityGoods),
            routes.TraderServicesFrontendController.submitExportQuestionsWhichPriorityGoodsAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsFreightType(model) =>
        Ok(
          views.exportQuestionsFreightTypeView(
            formWithErrors.or(ExportFreightTypeForm, model.exportQuestionsAnswers.freightType),
            routes.TraderServicesFrontendController.submitExportQuestionsFreightTypeAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsMandatoryVesselInfo(model) =>
        Ok(
          views.exportQuestionsMandatoryVesselDetailsView(
            formWithErrors.or(MandatoryVesselDetailsForm, model.exportQuestionsAnswers.vesselDetails),
            routes.TraderServicesFrontendController.submitExportQuestionsMandatoryVesselInfoAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsOptionalVesselInfo(model) =>
        Ok(
          views.exportQuestionsOptionalVesselDetailsView(
            formWithErrors.or(OptionalVesselDetailsForm, model.exportQuestionsAnswers.vesselDetails),
            routes.TraderServicesFrontendController.submitExportQuestionsOptionalVesselInfoAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case ExportQuestionsSummary(model) =>
        Ok(
          views.exportQuestionsSummaryView(
            model.declarationDetails,
            model.exportQuestionsAnswers,
            routes.TraderServicesFrontendController.showFileUpload,
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsContactInfo(model) =>
        Ok(
          views.exportQuestionsContactInfoView(
            formWithErrors.or(ExportContactForm, model.exportQuestionsAnswers.contactInfo),
            routes.TraderServicesFrontendController.submitExportQuestionsContactInfoAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsRequestType(model) =>
        Ok(
          views.importQuestionsRequestTypeView(
            formWithErrors.or(ImportRequestTypeForm, model.importQuestionsAnswers.requestType),
            routes.TraderServicesFrontendController.submitImportQuestionsRequestTypeAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsRouteType(model) =>
        Ok(
          views.importQuestionsRouteTypeView(
            formWithErrors.or(ImportRouteTypeForm, model.importQuestionsAnswers.routeType),
            routes.TraderServicesFrontendController.submitImportQuestionsRouteTypeAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsHasPriorityGoods(model) =>
        Ok(
          views.importQuestionsHasPriorityGoodsView(
            formWithErrors.or(ImportHasPriorityGoodsForm, model.importQuestionsAnswers.hasPriorityGoods),
            routes.TraderServicesFrontendController.submitImportQuestionsHasPriorityGoodsAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsWhichPriorityGoods(model) =>
        Ok(
          views.importQuestionsWhichPriorityGoodsView(
            formWithErrors.or(ImportPriorityGoodsForm, model.importQuestionsAnswers.priorityGoods),
            routes.TraderServicesFrontendController.submitImportQuestionsWhichPriorityGoodsAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsALVS(model) =>
        Ok(
          views.importQuestionsALVSView(
            formWithErrors.or(ImportHasALVSForm, model.importQuestionsAnswers.hasALVS),
            routes.TraderServicesFrontendController.submitImportQuestionsALVSAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsFreightType(model) =>
        Ok(
          views.importQuestionsFreightTypeView(
            formWithErrors.or(ImportFreightTypeForm, model.importQuestionsAnswers.freightType),
            routes.TraderServicesFrontendController.submitImportQuestionsFreightTypeAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsMandatoryVesselInfo(model) =>
        Ok(
          views.importQuestionsMandatoryVesselDetailsView(
            formWithErrors.or(MandatoryVesselDetailsForm, model.importQuestionsAnswers.vesselDetails),
            routes.TraderServicesFrontendController.submitImportQuestionsMandatoryVesselInfoAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsOptionalVesselInfo(model) =>
        Ok(
          views.importQuestionsOptionalVesselDetailsView(
            formWithErrors.or(OptionalVesselDetailsForm, model.importQuestionsAnswers.vesselDetails),
            routes.TraderServicesFrontendController.submitImportQuestionsOptionalVesselInfoAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsContactInfo(model) =>
        Ok(
          views.importQuestionsContactInfoView(
            formWithErrors.or(ImportContactForm, model.importQuestionsAnswers.contactInfo),
            routes.TraderServicesFrontendController.submitImportQuestionsContactInfoAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case ImportQuestionsSummary(model) =>
        Ok(
          views.importQuestionsSummaryView(
            model.declarationDetails,
            model.importQuestionsAnswers,
            routes.TraderServicesFrontendController.showFileUpload,
            backLinkFor(breadcrumbs)
          )
        )

      case UploadFile(_, _, _, uploadRequest, fileUploads, maybeUploadError) =>
        Ok(
          views.uploadFileView(
            uploadRequest,
            fileUploads,
            maybeUploadError,
            backLink =
              if (fileUploads.isEmpty)
                backLinkToMostRecent[State.SummaryState](breadcrumbs)
              else
                backLinkToMostRecent[State.FileUploaded](
                  breadcrumbs,
                  Some(backLinkToMostRecent[State.SummaryState](breadcrumbs))
                )
          )
        )

      case WaitingForFileVerification(_, _, reference, _, _, _) =>
        Ok(
          views.waitingForFileVerificationView(
            successAction = routes.TraderServicesFrontendController.showFileUploaded,
            failureAction = routes.TraderServicesFrontendController.showFileUpload,
            checkStatusAction = routes.TraderServicesFrontendController.checkFileVerificationStatus(reference),
            backLink = routes.TraderServicesFrontendController.showFileUpload
          )
        )

      case FileUploaded(declarationDetails, questionsAnswers, fileUploads, _) =>
        Ok(
          if (fileUploads.acceptedCount < Rules.maxFileUploadsNumber)
            views.fileUploadedView(
              formWithErrors.or(UploadAnotherFileChoiceForm),
              fileUploads,
              routes.TraderServicesFrontendController.submitUploadAnotherFileChoice,
              routes.TraderServicesFrontendController.removeFileUploadByReference,
              backLinkToMostRecent[State.SummaryState](breadcrumbs)
            )
          else
            views.fileUploadedSummaryView(
              fileUploads,
              workInProgresDeadEndCall,
              routes.TraderServicesFrontendController.removeFileUploadByReference,
              backLinkToMostRecent[State.SummaryState](breadcrumbs)
            )
        )

      case _ => NotImplemented

    }

  def renderFileVerificationStatus(
    reference: String
  )(state: State, breadcrumbs: List[State], formWithErrors: Option[Form[_]])(implicit
    request: Request[_]
  ): Result =
    state match {
      case s: State.HasFileUploads =>
        s.fileUploads.files.find(_.reference == reference) match {
          case Some(f) => Ok(Json.toJson(FileVerificationStatus(f)))
          case None    => NotFound
        }
      case _ => NotFound
    }

  private val journeyIdPathParamRegex = ".*?/journey/([A-Za-z0-9-]{36})/.*".r

  override def journeyId(implicit rh: RequestHeader): Option[String] = {
    val journeyIdFromPath = rh.path match {
      case journeyIdPathParamRegex(id) => Some(id)
      case _                           => None
    }
    journeyIdFromPath.orElse(rh.session.get(journeyService.journeyKey))
  }

  def currentJourneyId(implicit rh: RequestHeader): String = journeyId.get

  override implicit def context(implicit rh: RequestHeader): HeaderCarrier =
    appendJourneyId(super.hc)

  override def amendContext(headerCarrier: HeaderCarrier)(key: String, value: String): HeaderCarrier =
    headerCarrier.withExtraHeaders(key -> value)
}

object TraderServicesFrontendController {

  import FormFieldMappings._

  val DeclarationDetailsForm = Form[DeclarationDetails](
    mapping(
      "epu"         -> epuMapping,
      "entryNumber" -> entryNumberMapping,
      "entryDate"   -> entryDateMapping
    )(DeclarationDetails.apply)(DeclarationDetails.unapply)
  )

  val ExportRequestTypeForm = Form[ExportRequestType](
    mapping("requestType" -> exportRequestTypeMapping)(identity)(Option.apply)
  )

  val ExportRouteTypeForm = Form[ExportRouteType](
    mapping("routeType" -> exportRouteTypeMapping)(identity)(Option.apply)
  )

  val ExportHasPriorityGoodsForm = Form[Boolean](
    mapping("hasPriorityGoods" -> exportHasPriorityGoodsMapping)(identity)(Option.apply)
  )

  val ExportPriorityGoodsForm = Form[ExportPriorityGoods](
    mapping("priorityGoods" -> exportPriorityGoodsMapping)(identity)(Option.apply)
  )

  val ExportFreightTypeForm = Form[ExportFreightType](
    mapping("freightType" -> exportFreightTypeMapping)(identity)(Option.apply)
  )

  val ExportContactForm = Form[ExportContactInfo](
    mapping(
      "contactName"   -> exportContactNameMapping,
      "contactEmail"  -> exportContactEmailMapping,
      "contactNumber" -> exportContactNumberMapping
    )(ExportContactInfo.apply)(ExportContactInfo.unapply)
  )

  val ImportRequestTypeForm = Form[ImportRequestType](
    mapping("requestType" -> importRequestTypeMapping)(identity)(Option.apply)
  )

  val ImportRouteTypeForm = Form[ImportRouteType](
    mapping("routeType" -> importRouteTypeMapping)(identity)(Option.apply)
  )

  val ImportHasPriorityGoodsForm = Form[Boolean](
    mapping("hasPriorityGoods" -> importHasPriorityGoodsMapping)(identity)(Option.apply)
  )

  val ImportPriorityGoodsForm = Form[ImportPriorityGoods](
    mapping("priorityGoods" -> importPriorityGoodsMapping)(identity)(Option.apply)
  )

  val ImportFreightTypeForm = Form[ImportFreightType](
    mapping("freightType" -> importFreightTypeMapping)(identity)(Option.apply)
  )

  val ImportHasALVSForm = Form[Boolean](
    mapping("hasALVS" -> importHasALVSMapping)(identity)(Option.apply)
  )

  val ImportContactForm = Form[ImportContactInfo](
    mapping(
      "contactName"   -> importContactNameMapping,
      "contactEmail"  -> importContactEmailMapping,
      "contactNumber" -> importContactNumberMapping
    )(ImportContactInfo.apply)(ImportContactInfo.unapply)
  )

  val MandatoryVesselDetailsForm = Form[VesselDetails](
    mapping(
      "vesselName" -> mandatoryVesselNameMapping,
      "dateOfArrival" -> mandatoryDateOfArrivalMapping
        .verifying(dateOfArrivalRangeConstraint),
      "timeOfArrival" -> mandatoryTimeOfArrivalMapping
    )(VesselDetails.apply)(VesselDetails.unapply)
  )

  val OptionalVesselDetailsForm = Form[VesselDetails](
    mapping(
      "vesselName" -> optionalVesselNameMapping,
      "dateOfArrival" -> optionalDateOfArrivalMapping
        .verifying(dateOfArrivalRangeConstraint),
      "timeOfArrival" -> optionalTimeOfArrivalMapping
    )(VesselDetails.apply)(VesselDetails.unapply)
  )

  val UploadAnotherFileChoiceForm = Form[Boolean](
    mapping("uploadAnotherFile" -> uploadAnotherFileMapping)(identity)(Option.apply)
  )

  val UpscanUploadErrorForm = Form[S3UploadError](
    mapping(
      "key"            -> nonEmptyText,
      "errorCode"      -> text,
      "errorMessage"   -> text,
      "errorRequestId" -> optional(text),
      "errorResource"  -> optional(text)
    )(S3UploadError.apply)(S3UploadError.unapply)
  )
}
