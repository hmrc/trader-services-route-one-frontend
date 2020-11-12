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
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.traderservices.models.ExportQuestions
import uk.gov.hmrc.traderservices.models.QuestionsAnswers
import uk.gov.hmrc.traderservices.models.ImportQuestions

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

  val controller = routes.TraderServicesFrontendController

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
      //.orRollbackUsing(Mergers.copyDeclarationDetails)
      .orApply(Transitions.enterDeclarationDetails)

  // POST /pre-clearance/declaration-details
  val submitDeclarationDetails: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(DeclarationDetailsForm)
      .apply(Transitions.submittedDeclarationDetails)

  // ----------------------- EXPORT QUESTIONS -----------------------

  // GET /pre-clearance/export-questions/request-type
  val showAnswerExportQuestionsRequestType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsRequestType]
      .orApply(Transitions.backToAnswerExportQuestionsRequestType)

  // POST /pre-clearance/export-questions/request-type
  val submitExportQuestionsRequestTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportRequestTypeForm)
      .apply(Transitions.submittedExportQuestionsAnswerRequestType)

  // GET /pre-clearance/export-questions/route-type
  val showAnswerExportQuestionsRouteType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsRouteType]
      .orApply(Transitions.backToAnswerExportQuestionsRouteType)

  // POST /pre-clearance/export-questions/route-type
  val submitExportQuestionsRouteTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportRouteTypeForm)
      .apply(Transitions.submittedExportQuestionsAnswerRouteType)

  // GET /pre-clearance/export-questions/has-priority-goods
  val showAnswerExportQuestionsHasPriorityGoods: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsHasPriorityGoods]
      .orApply(Transitions.backToAnswerExportQuestionsHasPriorityGoods)

  // POST /pre-clearance/export-questions/has-priority-goods
  val submitExportQuestionsHasPriorityGoodsAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportHasPriorityGoodsForm)
      .apply(Transitions.submittedExportQuestionsAnswerHasPriorityGoods)

  // GET /pre-clearance/export-questions/which-priority-goods
  val showAnswerExportQuestionsWhichPriorityGoods: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsWhichPriorityGoods]
      .orApply(Transitions.backToAnswerExportQuestionsWhichPriorityGoods)

  // POST /pre-clearance/export-questions/which-priority-goods
  val submitExportQuestionsWhichPriorityGoodsAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportPriorityGoodsForm)
      .apply(Transitions.submittedExportQuestionsAnswerWhichPriorityGoods)

  // GET /pre-clearance/export-questions/transport-type
  val showAnswerExportQuestionsFreightType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsFreightType]
      .orApply(Transitions.backToAnswerExportQuestionsFreightType)

  // POST /pre-clearance/export-questions/transport-type
  val submitExportQuestionsFreightTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportFreightTypeForm)
      .apply(Transitions.submittedExportQuestionsAnswerFreightType)

  // GET /pre-clearance/export-questions/vessel-info-required
  val showAnswerExportQuestionsMandatoryVesselInfo: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsMandatoryVesselInfo]
      .orApply(Transitions.backToAnswerExportQuestionsMandatoryVesselInfo)

  // POST /pre-clearance/export-questions/vessel-info-required
  val submitExportQuestionsMandatoryVesselInfoAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(MandatoryVesselDetailsForm)
      .apply(Transitions.submittedExportQuestionsMandatoryVesselDetails)

  // GET /pre-clearance/export-questions/vessel-info
  val showAnswerExportQuestionsOptionalVesselInfo: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsOptionalVesselInfo]
      .orApply(Transitions.backToAnswerExportQuestionsOptionalVesselInfo)

  // POST /pre-clearance/export-questions/vessel-info
  val submitExportQuestionsOptionalVesselInfoAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(OptionalVesselDetailsForm)
      .apply(Transitions.submittedExportQuestionsOptionalVesselDetails)

  // GET /pre-clearance/export-questions/contact-info
  val showAnswerExportQuestionsContactInfo: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsContactInfo]
      .orApply(Transitions.backToAnswerExportQuestionsContactInfo)

  // POST /pre-clearance/export-questions/contact-info
  val submitExportQuestionsContactInfoAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportContactForm)
      .apply(Transitions.submittedExportQuestionsContactInfo)

  // GET /pre-clearance/export-questions/summary
  val showExportQuestionsSummary: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.ExportQuestionsSummary]
      .orApply(Transitions.backToQuestionsSummary)

  // ----------------------- IMPORT QUESTIONS -----------------------

  // GET /pre-clearance/import-questions/request-type
  val showAnswerImportQuestionsRequestType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsRequestType]
      .orApply(Transitions.backToAnswerImportQuestionsRequestType)

  // POST /pre-clearance/import-questions/request-type
  val submitImportQuestionsRequestTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportRequestTypeForm)
      .apply(Transitions.submittedImportQuestionsAnswersRequestType)

  // GET /pre-clearance/import-questions/route-type
  val showAnswerImportQuestionsRouteType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsRouteType]
      .orApply(Transitions.backToAnswerImportQuestionsRouteType)

  // POST /pre-clearance/import-questions/route-type
  val submitImportQuestionsRouteTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportRouteTypeForm)
      .apply(Transitions.submittedImportQuestionsAnswerRouteType)

  // GET /pre-clearance/import-questions/has-priority-goods
  val showAnswerImportQuestionsHasPriorityGoods: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsHasPriorityGoods]
      .orApply(Transitions.backToAnswerImportQuestionsHasPriorityGoods)

  // POST /pre-clearance/import-questions/has-priority-goods
  val submitImportQuestionsHasPriorityGoodsAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportHasPriorityGoodsForm)
      .apply(Transitions.submittedImportQuestionsAnswerHasPriorityGoods)

  // GET /pre-clearance/import-questions/which-priority-goods
  val showAnswerImportQuestionsWhichPriorityGoods: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsWhichPriorityGoods]
      .orApply(Transitions.backToAnswerImportQuestionsWhichPriorityGoods)

  // POST /pre-clearance/import-questions/which-priority-goods
  val submitImportQuestionsWhichPriorityGoodsAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportPriorityGoodsForm)
      .apply(Transitions.submittedImportQuestionsAnswerWhichPriorityGoods)

  // GET /pre-clearance/import-questions/automatic-licence-verification
  val showAnswerImportQuestionsALVS: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsALVS]
      .orApply(Transitions.backToAnswerImportQuestionsALVS)

  // POST /pre-clearance/import-questions/automatic-licence-verification
  val submitImportQuestionsALVSAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportHasALVSForm)
      .apply(Transitions.submittedImportQuestionsAnswerHasALVS)

  // GET /pre-clearance/import-questions/transport-type
  val showAnswerImportQuestionsFreightType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsFreightType]
      .orApply(Transitions.backToAnswerImportQuestionsFreightType)

  // POST /pre-clearance/import-questions/transport-type
  val submitImportQuestionsFreightTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportFreightTypeForm)
      .apply(Transitions.submittedImportQuestionsAnswerFreightType)

  // GET /pre-clearance/import-questions/vessel-info-required
  val showAnswerImportQuestionsMandatoryVesselInfo: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsMandatoryVesselInfo]
      .orApply(Transitions.backToAnswerImportQuestionsMandatoryVesselInfo)

  // POST /pre-clearance/import-questions/vessel-info-required
  val submitImportQuestionsMandatoryVesselInfoAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(MandatoryVesselDetailsForm)
      .apply(Transitions.submittedImportQuestionsMandatoryVesselDetails)

  // GET /pre-clearance/import-questions/vessel-info
  val showAnswerImportQuestionsOptionalVesselInfo: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsOptionalVesselInfo]
      .orApply(Transitions.backToAnswerImportQuestionsOptionalVesselInfo)

  // POST /pre-clearance/import-questions/vessel-info
  val submitImportQuestionsOptionalVesselInfoAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(OptionalVesselDetailsForm)
      .apply(Transitions.submittedImportQuestionsOptionalVesselDetails)

  // GET /pre-clearance/import-questions/contact-info
  val showAnswerImportQuestionsContactInfo: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsContactInfo]
      .orApply(Transitions.backToAnswerImportQuestionsContactInfo)

  // POST /pre-clearance/import-questions/contact-info
  val submitImportQuestionsContactInfoAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportContactForm)
      .apply(Transitions.submittedImportQuestionsContactInfo)

  // GET /pre-clearance/import-questions/summary
  val showImportQuestionsSummary: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.ImportQuestionsSummary]
      .orApply(Transitions.backToQuestionsSummary)

  // ----------------------- FILES UPLOAD -----------------------

  /**
    * This cookie is set by the script on each request
    * coming from one of our own pages open in the browser.
    */
  val COOKIE_JSENABLED = "jsenabled"

  def successRedirect(implicit rh: RequestHeader) =
    appConfig.baseExternalCallbackUrl + (rh.cookies.get(COOKIE_JSENABLED) match {
      case Some(_) => controller.asyncWaitingForFileVerification(journeyId.get)
      case None    => controller.showWaitingForFileVerification()
    })

  def errorRedirect(implicit rh: RequestHeader) =
    appConfig.baseExternalCallbackUrl + (rh.cookies.get(COOKIE_JSENABLED) match {
      case Some(_) => controller.asyncMarkFileUploadAsRejected(journeyId.get)
      case None    => controller.markFileUploadAsRejected()
    })

  // GET /pre-clearance/file-upload
  val showFileUpload: Action[AnyContent] =
    whenAuthorisedAsUser
      .applyWithRequest { implicit request =>
        val callbackUrl = appConfig.baseInternalCallbackUrl + controller.callbackFromUpscan(currentJourneyId).url
        Transitions
          .initiateFileUpload(callbackUrl, successRedirect, errorRedirect, appConfig.fileFormats.maxFileSizeMb)(
            upscanInitiateConnector.initiate(_)
          )
      }
      .redirectOrDisplayIf[State.UploadFile]

  // GET /pre-clearance/file-rejected
  val markFileUploadAsRejected: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(UpscanUploadErrorForm)
      .apply(Transitions.fileUploadWasRejected)

  // GET /pre-clearance/journey/:journeyId/file-rejected-async
  def asyncMarkFileUploadAsRejected(journeyId: String): Action[AnyContent] =
    actions
      .bindForm(UpscanUploadErrorForm)
      .apply(Transitions.fileUploadWasRejected(""))
      .displayUsing(implicit request => renderNoContent)

  // GET /pre-clearance/file-verification
  val showWaitingForFileVerification: Action[AnyContent] =
    whenAuthorisedAsUser
      .waitForStateThenRedirect[State.FileUploaded](3)
      .orApplyOnTimeout(_ => Transitions.waitForFileVerification)
      .redirectOrDisplayIf[State.WaitingForFileVerification]

  // GET /pre-clearance/journey/:journeyId/file-verification-async
  def asyncWaitingForFileVerification(journeyId: String): Action[AnyContent] =
    actions
      .apply(Transitions.waitForFileVerification(""))
      .displayUsing(implicit request => renderNoContent)

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
      .orRollback

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
        )(upscanInitiateConnector.initiate(_))(traderServicesApiConnector.createCase(_)) _
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

  // POST /pre-clearance/create-case
  def createCase: Action[AnyContent] =
    whenAuthorisedAsUser
      .applyWithRequest { implicit request =>
        Transitions.createCase(traderServicesApiConnector.createCase(_))
      }

  // GET /pre-clearance/confirmation
  def showCreateCaseConfirmation: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.CreateCaseConfirmation]
      .orRollback
      .andCleanBreadcrumbs() // forget journey history

  /**
    * Function from the `State` to the `Call` (route),
    * used by play-fsm internally to create redirects.
    */
  override def getCallFor(state: State)(implicit request: Request[_]): Call =
    state match {
      case Start =>
        controller.showStart()

      case _: EnterDeclarationDetails =>
        controller.showEnterDeclarationDetails()

      case _: AnswerExportQuestionsRequestType =>
        controller.showAnswerExportQuestionsRequestType()

      case _: AnswerExportQuestionsRouteType =>
        controller.showAnswerExportQuestionsRouteType()

      case _: AnswerExportQuestionsHasPriorityGoods =>
        controller.showAnswerExportQuestionsHasPriorityGoods()

      case _: AnswerExportQuestionsWhichPriorityGoods =>
        controller.showAnswerExportQuestionsWhichPriorityGoods()

      case _: AnswerExportQuestionsFreightType =>
        controller.showAnswerExportQuestionsFreightType()

      case _: AnswerExportQuestionsMandatoryVesselInfo =>
        controller.showAnswerExportQuestionsMandatoryVesselInfo()

      case _: AnswerExportQuestionsOptionalVesselInfo =>
        controller.showAnswerExportQuestionsOptionalVesselInfo()

      case _: AnswerExportQuestionsContactInfo =>
        controller.showAnswerExportQuestionsContactInfo()

      case _: ExportQuestionsSummary =>
        controller.showExportQuestionsSummary()

      case _: AnswerImportQuestionsRequestType =>
        controller.showAnswerImportQuestionsRequestType()

      case _: AnswerImportQuestionsRouteType =>
        controller.showAnswerImportQuestionsRouteType()

      case _: AnswerImportQuestionsHasPriorityGoods =>
        controller.showAnswerImportQuestionsHasPriorityGoods()

      case _: AnswerImportQuestionsWhichPriorityGoods =>
        controller.showAnswerImportQuestionsWhichPriorityGoods()

      case _: AnswerImportQuestionsALVS =>
        controller.showAnswerImportQuestionsALVS()

      case _: AnswerImportQuestionsFreightType =>
        controller.showAnswerImportQuestionsFreightType()

      case _: AnswerImportQuestionsMandatoryVesselInfo =>
        controller.showAnswerImportQuestionsMandatoryVesselInfo()

      case _: AnswerImportQuestionsOptionalVesselInfo =>
        controller.showAnswerImportQuestionsOptionalVesselInfo()

      case _: AnswerImportQuestionsContactInfo =>
        controller.showAnswerImportQuestionsContactInfo()

      case _: ImportQuestionsSummary =>
        controller.showImportQuestionsSummary()

      case _: UploadFile =>
        controller.showFileUpload()

      case _: WaitingForFileVerification =>
        controller.showWaitingForFileVerification()

      case _: FileUploaded =>
        controller.showFileUploaded()

      case _: CreateCaseConfirmation =>
        controller.showCreateCaseConfirmation()

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
        Ok(views.startView(controller.showEnterDeclarationDetails()))

      case EnterDeclarationDetails(declarationDetailsOpt, _, _, _) =>
        Ok(
          views.declarationDetailsEntryView(
            formWithErrors.or(DeclarationDetailsForm, declarationDetailsOpt),
            controller.submitDeclarationDetails(),
            controller.showStart()
          )
        )

      case AnswerExportQuestionsRequestType(model) =>
        Ok(
          views.exportQuestionsRequestTypeView(
            formWithErrors.or(ExportRequestTypeForm, model.exportQuestionsAnswers.requestType),
            controller.submitExportQuestionsRequestTypeAnswer(),
            controller.showEnterDeclarationDetails()
          )
        )

      case AnswerExportQuestionsRouteType(model) =>
        Ok(
          views.exportQuestionsRouteTypeView(
            formWithErrors.or(ExportRouteTypeForm, model.exportQuestionsAnswers.routeType),
            controller.submitExportQuestionsRouteTypeAnswer(),
            controller.showAnswerExportQuestionsRequestType()
          )
        )

      case AnswerExportQuestionsHasPriorityGoods(model) =>
        Ok(
          views.exportQuestionsHasPriorityGoodsView(
            formWithErrors.or(ExportHasPriorityGoodsForm, model.exportQuestionsAnswers.hasPriorityGoods),
            controller.submitExportQuestionsHasPriorityGoodsAnswer(),
            controller.showAnswerExportQuestionsRouteType()
          )
        )

      case AnswerExportQuestionsWhichPriorityGoods(model) =>
        Ok(
          views.exportQuestionsWhichPriorityGoodsView(
            formWithErrors.or(ExportPriorityGoodsForm, model.exportQuestionsAnswers.priorityGoods),
            controller.submitExportQuestionsWhichPriorityGoodsAnswer(),
            controller.showAnswerExportQuestionsHasPriorityGoods()
          )
        )

      case AnswerExportQuestionsFreightType(model) =>
        Ok(
          views.exportQuestionsFreightTypeView(
            formWithErrors.or(ExportFreightTypeForm, model.exportQuestionsAnswers.freightType),
            controller.submitExportQuestionsFreightTypeAnswer(),
            if (model.exportQuestionsAnswers.priorityGoods.isDefined)
              controller.showAnswerExportQuestionsWhichPriorityGoods()
            else controller.showAnswerExportQuestionsHasPriorityGoods()
          )
        )

      case AnswerExportQuestionsMandatoryVesselInfo(model) =>
        Ok(
          views.exportQuestionsMandatoryVesselDetailsView(
            formWithErrors.or(MandatoryVesselDetailsForm, model.exportQuestionsAnswers.vesselDetails),
            controller.submitExportQuestionsMandatoryVesselInfoAnswer(),
            controller.showAnswerExportQuestionsFreightType()
          )
        )

      case AnswerExportQuestionsOptionalVesselInfo(model) =>
        Ok(
          views.exportQuestionsOptionalVesselDetailsView(
            formWithErrors.or(OptionalVesselDetailsForm, model.exportQuestionsAnswers.vesselDetails),
            controller.submitExportQuestionsOptionalVesselInfoAnswer(),
            controller.showAnswerExportQuestionsFreightType()
          )
        )

      case AnswerExportQuestionsContactInfo(model) =>
        Ok(
          views.exportQuestionsContactInfoView(
            formWithErrors.or(ExportContactForm, model.exportQuestionsAnswers.contactInfo),
            controller.submitExportQuestionsContactInfoAnswer(),
            if (Rules.isVesselDetailsAnswerMandatory(model.exportQuestionsAnswers))
              controller.showAnswerExportQuestionsMandatoryVesselInfo()
            else controller.showAnswerExportQuestionsOptionalVesselInfo()
          )
        )

      case ExportQuestionsSummary(model) =>
        Ok(
          views.exportQuestionsSummaryView(
            model.declarationDetails,
            model.exportQuestionsAnswers,
            controller.showFileUpload,
            controller.showAnswerExportQuestionsContactInfo()
          )
        )

      case AnswerImportQuestionsRequestType(model) =>
        Ok(
          views.importQuestionsRequestTypeView(
            formWithErrors.or(ImportRequestTypeForm, model.importQuestionsAnswers.requestType),
            controller.submitImportQuestionsRequestTypeAnswer(),
            controller.showEnterDeclarationDetails()
          )
        )

      case AnswerImportQuestionsRouteType(model) =>
        Ok(
          views.importQuestionsRouteTypeView(
            formWithErrors.or(ImportRouteTypeForm, model.importQuestionsAnswers.routeType),
            controller.submitImportQuestionsRouteTypeAnswer(),
            controller.showAnswerImportQuestionsRequestType()
          )
        )

      case AnswerImportQuestionsHasPriorityGoods(model) =>
        Ok(
          views.importQuestionsHasPriorityGoodsView(
            formWithErrors.or(ImportHasPriorityGoodsForm, model.importQuestionsAnswers.hasPriorityGoods),
            controller.submitImportQuestionsHasPriorityGoodsAnswer(),
            controller.showAnswerImportQuestionsRouteType()
          )
        )

      case AnswerImportQuestionsWhichPriorityGoods(model) =>
        Ok(
          views.importQuestionsWhichPriorityGoodsView(
            formWithErrors.or(ImportPriorityGoodsForm, model.importQuestionsAnswers.priorityGoods),
            controller.submitImportQuestionsWhichPriorityGoodsAnswer(),
            controller.showAnswerImportQuestionsHasPriorityGoods()
          )
        )

      case AnswerImportQuestionsALVS(model) =>
        Ok(
          views.importQuestionsALVSView(
            formWithErrors.or(ImportHasALVSForm, model.importQuestionsAnswers.hasALVS),
            controller.submitImportQuestionsALVSAnswer(),
            if (model.importQuestionsAnswers.priorityGoods.isDefined)
              controller.showAnswerImportQuestionsWhichPriorityGoods()
            else controller.showAnswerImportQuestionsHasPriorityGoods()
          )
        )

      case AnswerImportQuestionsFreightType(model) =>
        Ok(
          views.importQuestionsFreightTypeView(
            formWithErrors.or(ImportFreightTypeForm, model.importQuestionsAnswers.freightType),
            controller.submitImportQuestionsFreightTypeAnswer(),
            controller.showAnswerImportQuestionsALVS()
          )
        )

      case AnswerImportQuestionsMandatoryVesselInfo(model) =>
        Ok(
          views.importQuestionsMandatoryVesselDetailsView(
            formWithErrors.or(MandatoryVesselDetailsForm, model.importQuestionsAnswers.vesselDetails),
            controller.submitImportQuestionsMandatoryVesselInfoAnswer(),
            controller.showAnswerImportQuestionsFreightType()
          )
        )

      case AnswerImportQuestionsOptionalVesselInfo(model) =>
        Ok(
          views.importQuestionsOptionalVesselDetailsView(
            formWithErrors.or(OptionalVesselDetailsForm, model.importQuestionsAnswers.vesselDetails),
            controller.submitImportQuestionsOptionalVesselInfoAnswer(),
            controller.showAnswerImportQuestionsFreightType()
          )
        )

      case AnswerImportQuestionsContactInfo(model) =>
        Ok(
          views.importQuestionsContactInfoView(
            formWithErrors.or(ImportContactForm, model.importQuestionsAnswers.contactInfo),
            controller.submitImportQuestionsContactInfoAnswer(),
            if (Rules.isVesselDetailsAnswerMandatory(model.importQuestionsAnswers))
              controller.showAnswerImportQuestionsMandatoryVesselInfo()
            else controller.showAnswerImportQuestionsOptionalVesselInfo()
          )
        )

      case ImportQuestionsSummary(model) =>
        Ok(
          views.importQuestionsSummaryView(
            model.declarationDetails,
            model.importQuestionsAnswers,
            controller.showFileUpload,
            controller.showAnswerImportQuestionsContactInfo()
          )
        )

      case UploadFile(_, questionsAnswers, reference, uploadRequest, fileUploads, maybeUploadError) =>
        Ok(
          views.uploadFileView(
            uploadRequest,
            fileUploads,
            maybeUploadError,
            successAction = routes.TraderServicesFrontendController.showFileUploaded,
            failureAction = routes.TraderServicesFrontendController.showFileUpload,
            checkStatusAction = routes.TraderServicesFrontendController.checkFileVerificationStatus(reference),
            backLink =
              if (fileUploads.isEmpty) backLinkToSummary(questionsAnswers)
              else controller.showFileUploaded()
          )
        )

      case WaitingForFileVerification(_, _, reference, _, _, _) =>
        Ok(
          views.waitingForFileVerificationView(
            successAction = controller.showFileUploaded,
            failureAction = controller.showFileUpload,
            checkStatusAction = controller.checkFileVerificationStatus(reference),
            backLink = controller.showFileUpload
          )
        )

      case FileUploaded(declarationDetails, questionsAnswers, fileUploads, _) =>
        Ok(
          if (fileUploads.acceptedCount < Rules.maxFileUploadsNumber)
            views.fileUploadedView(
              formWithErrors.or(UploadAnotherFileChoiceForm),
              fileUploads,
              controller.submitUploadAnotherFileChoice,
              controller.removeFileUploadByReference,
              backLinkToSummary(questionsAnswers)
            )
          else
            views.fileUploadedSummaryView(
              fileUploads,
              controller.createCase,
              controller.removeFileUploadByReference,
              backLinkToSummary(questionsAnswers)
            )
        )

      case CreateCaseConfirmation(declarationDetails, questionsAnswers, fileUploads, caseReferenceId) =>
        Ok(
          views.createCaseConfirmationView(
            caseReferenceId,
            controller.showEnterDeclarationDetails()
          )
        )

      case _ => NotImplemented

    }

  def backLinkToSummary(questionsAnswers: QuestionsAnswers): Call =
    questionsAnswers match {
      case _: ExportQuestions => controller.showExportQuestionsSummary()
      case _: ImportQuestions => controller.showImportQuestionsSummary()
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

  def renderNoContent(state: State, breadcrumbs: List[State], formWithErrors: Option[Form[_]])(implicit
    request: Request[_]
  ): Result =
    state match {
      case _ => NoContent.withHeaders(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
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
