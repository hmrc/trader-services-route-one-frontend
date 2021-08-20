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

package uk.gov.hmrc.traderservices.controllers

import javax.inject.{Inject, Singleton}
import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.play.fsm.{JourneyController, JourneyIdSupport}
import uk.gov.hmrc.traderservices.connectors.{FrontendAuthConnector, PdfGeneratorConnector, TraderServicesApiConnector, TraderServicesResult, UpscanInitiateConnector, UpscanInitiateRequest}
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.State._
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.services.CreateCaseJourneyServiceWithHeaderCarrier
import uk.gov.hmrc.traderservices.wiring.AppConfig

import scala.concurrent.ExecutionContext
import play.api.libs.json.Json
import play.mvc.Http.HeaderNames
import akka.actor.ActorSystem
import uk.gov.hmrc.traderservices.views.UploadFileViewContext

import java.time.LocalDate
import akka.actor.Scheduler

import scala.concurrent.Future
import uk.gov.hmrc.traderservices.connectors.FileStream
import uk.gov.hmrc.traderservices.views.CommonUtilsHelper.DateTimeUtilities

@Singleton
class CreateCaseJourneyController @Inject() (
  appConfig: AppConfig,
  override val messagesApi: MessagesApi,
  traderServicesApiConnector: TraderServicesApiConnector,
  upscanInitiateConnector: UpscanInitiateConnector,
  val authConnector: FrontendAuthConnector,
  val env: Environment,
  controllerComponents: MessagesControllerComponents,
  views: uk.gov.hmrc.traderservices.views.CreateCaseViews,
  uploadFileViewContext: UploadFileViewContext,
  printStylesheet: ReceiptStylesheet,
  pdfGeneratorConnector: PdfGeneratorConnector,
  override val journeyService: CreateCaseJourneyServiceWithHeaderCarrier,
  override val actionBuilder: DefaultActionBuilder
)(implicit val config: Configuration, ec: ExecutionContext, val actorSystem: ActorSystem)
    extends FrontendController(controllerComponents) with I18nSupport with AuthActions
    with JourneyController[HeaderCarrier] with JourneyIdSupport[HeaderCarrier] with FileStream {

  final val controller = routes.CreateCaseJourneyController

  import CreateCaseJourneyController._
  import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel._

  implicit val scheduler: Scheduler = actorSystem.scheduler

  /** AsUser authorisation request */
  final val AsUser: WithAuthorised[Option[String]] =
    if (appConfig.requireEnrolmentFeature) { implicit request => body =>
      authorisedWithEnrolment(
        appConfig.authorisedServiceName,
        appConfig.authorisedIdentifierKey
      )(x => body(x._2))
    } else { implicit request => body =>
      authorisedWithoutEnrolment(x => body(x._2))
    }

  final val AsUserWithUidAndEori: WithAuthorised[(Option[String], Option[String])] =
    if (appConfig.requireEnrolmentFeature) { implicit request =>
      authorisedWithEnrolment(
        appConfig.authorisedServiceName,
        appConfig.authorisedIdentifierKey
      )
    } else { implicit request =>
      authorisedWithoutEnrolment
    }

  /** Base authorized action builder */
  final val whenAuthorisedAsUser = actions.whenAuthorised(AsUser)
  final val whenAuthorisedAsUserWithEori = actions.whenAuthorisedWithRetrievals(AsUser)
  final val whenAuthorisedAsUserWithUidAndEori = actions.whenAuthorisedWithRetrievals(AsUserWithUidAndEori)

  /** Dummy action to use only when developing to fill loose-ends. */
  private val actionNotYetImplemented = Action(NotImplemented)

  final def toSubscriptionJourney(continueUrl: String): Result =
    Redirect(appConfig.subscriptionJourneyUrl)

  // Dummy URL to use when developing the journey
  final val workInProgresDeadEndCall = Call("GET", "/send-documents-for-customs-check/work-in-progress")

  // GET /
  final val showStart: Action[AnyContent] =
    whenAuthorisedAsUser
      .apply(Transitions.start)
      .display
      .andCleanBreadcrumbs()

  // GET /new-or-existing
  final val showChooseNewOrExistingCase: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.ChooseNewOrExistingCase]
      .orApply(Transitions.chooseNewOrExistingCase)

  // POST /new-or-existing
  final val submitNewOrExistingCaseChoice: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(NewOrExistingCaseForm)
      .apply(Transitions.submittedNewOrExistingCaseChoice)

  // GET /new/entry-details
  final val showEnterEntryDetails: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.EnterEntryDetails]
      .orApply(Transitions.backToEnterEntryDetails)

  // POST /new/entry-details
  final val submitEntryDetails: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(EntryDetailsForm)
      .apply(Transitions.submittedEntryDetails)

  // ----------------------- EXPORT QUESTIONS -----------------------

  // GET /new/export/request-type
  final val showAnswerExportQuestionsRequestType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsRequestType]
      .orApply(Transitions.backToAnswerExportQuestionsRequestType)

  // POST /new/export/request-type
  final val submitExportQuestionsRequestTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportRequestTypeForm)
      .apply(Transitions.submittedExportQuestionsAnswerRequestType(appConfig.requireOptionalTransportFeature))

  // GET /new/export/route-type
  final val showAnswerExportQuestionsRouteType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsRouteType]
      .orApply(Transitions.backToAnswerExportQuestionsRouteType)

  // POST /new/export/route-type
  final val submitExportQuestionsRouteTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportRouteTypeForm)
      .apply(Transitions.submittedExportQuestionsAnswerRouteType(appConfig.requireOptionalTransportFeature))

  // GET /new/export/has-priority-goods
  final val showAnswerExportQuestionsHasPriorityGoods: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsHasPriorityGoods]
      .orApply(Transitions.backToAnswerExportQuestionsHasPriorityGoods)

  // POST /new/export/has-priority-goods
  final val submitExportQuestionsHasPriorityGoodsAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportHasPriorityGoodsForm)
      .apply(Transitions.submittedExportQuestionsAnswerHasPriorityGoods)

  // GET /new/export/which-priority-goods
  final val showAnswerExportQuestionsWhichPriorityGoods: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsWhichPriorityGoods]
      .orApply(Transitions.backToAnswerExportQuestionsWhichPriorityGoods)

  // POST /new/export/which-priority-goods
  final val submitExportQuestionsWhichPriorityGoodsAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportPriorityGoodsForm)
      .apply(Transitions.submittedExportQuestionsAnswerWhichPriorityGoods)

  // GET /new/export/transport-type
  final val showAnswerExportQuestionsFreightType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsFreightType]
      .orApply(Transitions.backToAnswerExportQuestionsFreightType)

  // POST /new/export/transport-type
  final val submitExportQuestionsFreightTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportFreightTypeForm)
      .apply(Transitions.submittedExportQuestionsAnswerFreightType(appConfig.requireOptionalTransportFeature))

  // GET /new/export/transport-information-required
  final val showAnswerExportQuestionsMandatoryVesselInfo: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsMandatoryVesselInfo]
      .orApply(Transitions.backToAnswerExportQuestionsMandatoryVesselInfo)

  // POST /new/export/transport-information-required
  final val submitExportQuestionsMandatoryVesselInfoAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindFormDerivedFromState(state =>
        mandatoryExportVesselDetailsForm(extractArrivalDate(state), extractExportRequestType(state))
      )
      .apply(Transitions.submittedExportQuestionsMandatoryVesselDetails)

  // GET /new/export/transport-information
  final val showAnswerExportQuestionsOptionalVesselInfo: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsOptionalVesselInfo]
      .orApply(Transitions.backToAnswerExportQuestionsOptionalVesselInfo)

  // POST /new/export/transport-information
  final val submitExportQuestionsOptionalVesselInfoAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindFormDerivedFromState(state =>
        optionalExportVesselDetailsForm(extractArrivalDate(state), extractExportRequestType(state))
      )
      .apply(Transitions.submittedExportQuestionsOptionalVesselDetails)

  // GET /new/export/contact-information
  final val showAnswerExportQuestionsContactInfo: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsContactInfo]
      .orApply(Transitions.backToAnswerExportQuestionsContactInfo)

  // POST /new/export/contact-information
  final val submitExportQuestionsContactInfoAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportContactForm)
      .applyWithRequest(implicit request =>
        Transitions.submittedExportQuestionsContactInfo(preferUploadMultipleFiles)(upscanRequest)(
          upscanInitiateConnector.initiate(_)
        )
      )

  // GET /new/export/check-your-answers
  final val showExportQuestionsSummary: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.ExportQuestionsSummary]
      .orApply(Transitions.toSummary)

  // GET /new/export/missing-information
  final val showExportQuestionsMissingInformationError: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.ExportQuestionsMissingInformationError]
      .orApply(Transitions.backToExportQuestionsMissingInformationError)

  // ----------------------- IMPORT QUESTIONS -----------------------

  // GET /new/import/request-type
  final val showAnswerImportQuestionsRequestType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsRequestType]
      .orApply(Transitions.backToAnswerImportQuestionsRequestType)

  // POST /new/import/request-type
  final val submitImportQuestionsRequestTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportRequestTypeForm)
      .apply(Transitions.submittedImportQuestionsAnswersRequestType)

  // GET /new/import/route-type
  final val showAnswerImportQuestionsRouteType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsRouteType]
      .orApply(Transitions.backToAnswerImportQuestionsRouteType)

  // POST /new/import/route-type
  final val submitImportQuestionsRouteTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportRouteTypeForm)
      .apply(Transitions.submittedImportQuestionsAnswerRouteType(appConfig.requireOptionalTransportFeature))

  // GET /new/import/has-priority-goods
  final val showAnswerImportQuestionsHasPriorityGoods: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsHasPriorityGoods]
      .orApply(Transitions.backToAnswerImportQuestionsHasPriorityGoods)

  // POST /new/import/has-priority-goods
  final val submitImportQuestionsHasPriorityGoodsAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportHasPriorityGoodsForm)
      .apply(Transitions.submittedImportQuestionsAnswerHasPriorityGoods)

  // GET /new/import/which-priority-goods
  final val showAnswerImportQuestionsWhichPriorityGoods: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsWhichPriorityGoods]
      .orApply(Transitions.backToAnswerImportQuestionsWhichPriorityGoods)

  // POST /new/import/which-priority-goods
  final val submitImportQuestionsWhichPriorityGoodsAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportPriorityGoodsForm)
      .apply(Transitions.submittedImportQuestionsAnswerWhichPriorityGoods)

  // GET /new/import/automatic-licence-verification
  final val showAnswerImportQuestionsALVS: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsALVS]
      .orApply(Transitions.backToAnswerImportQuestionsALVS)

  // POST /new/import/automatic-licence-verification
  final val submitImportQuestionsALVSAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportHasALVSForm)
      .apply(Transitions.submittedImportQuestionsAnswerHasALVS)

  // GET /new/import/transport-type
  final val showAnswerImportQuestionsFreightType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsFreightType]
      .orApply(Transitions.backToAnswerImportQuestionsFreightType)

  // POST /new/import/transport-type
  final val submitImportQuestionsFreightTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportFreightTypeForm)
      .apply(Transitions.submittedImportQuestionsAnswerFreightType(appConfig.requireOptionalTransportFeature))

  // GET /new/import/transport-information-required
  final val showAnswerImportQuestionsMandatoryVesselInfo: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsMandatoryVesselInfo]
      .orApply(Transitions.backToAnswerImportQuestionsMandatoryVesselInfo)

  // POST /new/import/transport-information-required
  final val submitImportQuestionsMandatoryVesselInfoAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindFormDerivedFromState(state => mandatoryImportVesselDetailsForm(extractArrivalDate(state)))
      .apply(Transitions.submittedImportQuestionsMandatoryVesselDetails)

  // GET /new/import/transport-information
  final val showAnswerImportQuestionsOptionalVesselInfo: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsOptionalVesselInfo]
      .orApply(Transitions.backToAnswerImportQuestionsOptionalVesselInfo)

  // POST /new/import/transport-information
  final val submitImportQuestionsOptionalVesselInfoAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindFormDerivedFromState(state => optionalImportVesselDetailsForm(extractArrivalDate(state)))
      .apply(Transitions.submittedImportQuestionsOptionalVesselDetails)

  // GET /new/import/contact-information
  final val showAnswerImportQuestionsContactInfo: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsContactInfo]
      .orApply(Transitions.backToAnswerImportQuestionsContactInfo)

  // POST /new/import/contact-information
  final val submitImportQuestionsContactInfoAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportContactForm)
      .applyWithRequest(implicit request =>
        Transitions.submittedImportQuestionsContactInfo(preferUploadMultipleFiles)(upscanRequest)(
          upscanInitiateConnector.initiate(_)
        )
      )

  // GET /new/import/check-your-answers
  final val showImportQuestionsSummary: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.ImportQuestionsSummary]
      .orApply(Transitions.toSummary)

  // GET /new/import/missing-information
  final val showImportQuestionsMissingInformationError: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.ImportQuestionsMissingInformationError]
      .orApply(Transitions.backToImportQuestionsMissingInformationError)

  // ----------------------- FILES UPLOAD -----------------------

  /** Initial time to wait for callback arrival. */
  final val INITIAL_CALLBACK_WAIT_TIME_SECONDS = 2

  /**
    * This cookie is set by the script on each request
    * coming from one of our own pages open in the browser.
    */
  final val COOKIE_JSENABLED = "jsenabled"

  final def preferUploadMultipleFiles(implicit rh: RequestHeader): Boolean =
    rh.cookies.get(COOKIE_JSENABLED).isDefined && appConfig.uploadMultipleFilesFeature

  final def successRedirect(implicit rh: RequestHeader) =
    appConfig.baseExternalCallbackUrl + (rh.cookies.get(COOKIE_JSENABLED) match {
      case Some(_) => controller.asyncWaitingForFileVerification(journeyId.get)
      case None    => controller.showWaitingForFileVerification
    })

  final def successRedirectWhenUploadingMultipleFiles(implicit rh: RequestHeader) =
    appConfig.baseExternalCallbackUrl + controller.asyncMarkFileUploadAsPosted(journeyId.get)

  final def errorRedirect(implicit rh: RequestHeader) =
    appConfig.baseExternalCallbackUrl + (rh.cookies.get(COOKIE_JSENABLED) match {
      case Some(_) => controller.asyncMarkFileUploadAsRejected(journeyId.get)
      case None    => controller.markFileUploadAsRejected
    })

  final def upscanRequest(implicit rh: RequestHeader): String => UpscanInitiateRequest =
    nonce =>
      UpscanInitiateRequest(
        callbackUrl = appConfig.baseInternalCallbackUrl + controller.callbackFromUpscan(currentJourneyId, nonce).url,
        successRedirect = Some(successRedirect),
        errorRedirect = Some(errorRedirect),
        minimumFileSize = Some(1),
        maximumFileSize = Some(appConfig.fileFormats.maxFileSizeMb * 1024 * 1024),
        expectedContentType = Some(appConfig.fileFormats.approvedFileTypes)
      )

  final def upscanRequestWhenUploadingMultipleFiles(implicit rh: RequestHeader): String => UpscanInitiateRequest =
    nonce =>
      UpscanInitiateRequest(
        callbackUrl = appConfig.baseInternalCallbackUrl + controller.callbackFromUpscan(currentJourneyId, nonce).url,
        successRedirect = Some(successRedirectWhenUploadingMultipleFiles),
        errorRedirect = Some(errorRedirect),
        minimumFileSize = Some(1),
        maximumFileSize = Some(appConfig.fileFormats.maxFileSizeMb * 1024 * 1024),
        expectedContentType = Some(appConfig.fileFormats.approvedFileTypes)
      )

  // GET /new/upload-files
  final val showUploadMultipleFiles: Action[AnyContent] =
    whenAuthorisedAsUser
      .apply(FileUploadTransitions.toUploadMultipleFiles)
      .redirectOrDisplayIf[FileUploadState.UploadMultipleFiles]

  // POST /new/upload-files/initialise/:uploadId
  final def initiateNextFileUpload(uploadId: String): Action[AnyContent] =
    whenAuthorisedAsUser
      .applyWithRequest { implicit request =>
        FileUploadTransitions
          .initiateNextFileUpload(uploadId)(upscanRequestWhenUploadingMultipleFiles)(
            upscanInitiateConnector.initiate(_)
          )
      }
      .displayUsing(renderUploadRequestJson(uploadId))

  // GET /new/file-upload
  final val showFileUpload: Action[AnyContent] =
    whenAuthorisedAsUser
      .applyWithRequest { implicit request =>
        FileUploadTransitions
          .initiateFileUpload(upscanRequest)(upscanInitiateConnector.initiate(_))
      }
      .redirectOrDisplayIf[FileUploadState.UploadFile]

  // GET /new/file-rejected
  final val markFileUploadAsRejected: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(UpscanUploadErrorForm)
      .apply(FileUploadTransitions.markUploadAsRejected)

  // POST /new/file-rejected
  final val markFileUploadAsRejectedAsync: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(UpscanUploadErrorForm)
      .apply(FileUploadTransitions.markUploadAsRejected)
      .displayUsing(acknowledgeFileUploadRedirect)

  // GET /new/journey/:journeyId/file-rejected
  final def asyncMarkFileUploadAsRejected(journeyId: String): Action[AnyContent] =
    actions
      .bindForm(UpscanUploadErrorForm)
      .apply(FileUploadTransitions.markUploadAsRejected)
      .displayUsing(acknowledgeFileUploadRedirect)

  // GET /new/file-verification
  final val showWaitingForFileVerification: Action[AnyContent] =
    whenAuthorisedAsUser
      .waitForStateThenRedirect[FileUploadState.FileUploaded](INITIAL_CALLBACK_WAIT_TIME_SECONDS)
      .orApplyOnTimeout(FileUploadTransitions.waitForFileVerification)
      .redirectOrDisplayIf[FileUploadState.WaitingForFileVerification]

  // GET /new/journey/:journeyId/file-verification
  final def asyncWaitingForFileVerification(journeyId: String): Action[AnyContent] =
    actions
      .waitForStateAndDisplayUsing[FileUploadState.FileUploaded](
        INITIAL_CALLBACK_WAIT_TIME_SECONDS,
        acknowledgeFileUploadRedirect
      )
      .orApplyOnTimeout(FileUploadTransitions.waitForFileVerification)
      .displayUsing(acknowledgeFileUploadRedirect)

  // OPTIONS
  final def preflightUpload(journeyId: String): Action[AnyContent] =
    Action {
      Created.withHeaders(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    }

  // GET /new/journey/:journeyId/file-posted
  final def asyncMarkFileUploadAsPosted(journeyId: String): Action[AnyContent] =
    actions
      .bindForm(UpscanUploadSuccessForm)
      .apply(FileUploadTransitions.markUploadAsPosted)
      .displayUsing(acknowledgeFileUploadRedirect)

  // POST /new/journey/:journeyId/callback-from-upscan
  final def callbackFromUpscan(journeyId: String, nonce: String): Action[AnyContent] =
    actions
      .parseJsonWithFallback[UpscanNotification](BadRequest)
      .apply(FileUploadTransitions.upscanCallbackArrived(Nonce(nonce)))
      .transform { case _ => NoContent }
      .recover {
        case e => InternalServerError
      }

  // GET /new/file-uploaded
  final val showFileUploaded: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[FileUploadState.FileUploaded]
      .orApply(FileUploadTransitions.backToFileUploaded)

  // POST /new/file-uploaded
  final val submitUploadAnotherFileChoice: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm[Boolean](UploadAnotherFileChoiceForm)
      .applyWithRequest { implicit request =>
        FileUploadTransitions.submitedUploadAnotherFileChoice(upscanRequest)(upscanInitiateConnector.initiate(_))(
          Transitions.toSummary
        ) _
      }

  // GET /new/file-uploaded/:reference/remove
  final def removeFileUploadByReference(reference: String): Action[AnyContent] =
    whenAuthorisedAsUser
      .applyWithRequest { implicit request =>
        FileUploadTransitions.removeFileUploadByReference(reference)(upscanRequest)(
          upscanInitiateConnector.initiate(_)
        )
      }

  // POST /new/file-uploaded/:reference/remove
  final def removeFileUploadByReferenceAsync(reference: String): Action[AnyContent] =
    whenAuthorisedAsUser
      .applyWithRequest { implicit request =>
        FileUploadTransitions.removeFileUploadByReference(reference)(upscanRequest)(
          upscanInitiateConnector.initiate(_)
        )
      }
      .displayUsing(renderFileRemovalStatusJson(reference))

  // GET /new/file-uploaded/:reference/:fileName
  final def previewFileUploadByReference(reference: String, fileName: String): Action[AnyContent] =
    whenAuthorisedAsUser.showCurrentState
      .displayAsyncUsing(streamFileFromUspcan(reference))

  // GET /new/file-verification/:reference/status
  final def checkFileVerificationStatus(reference: String): Action[AnyContent] =
    whenAuthorisedAsUser.showCurrentState
      .displayUsing(renderFileVerificationStatusJson(reference))

  // ----------------------- CONFIRMATION -----------------------

  // POST /new/create-case
  final def createCase: Action[AnyContent] =
    whenAuthorisedAsUserWithUidAndEori
      .applyWithRequest { implicit request => uidAndEori =>
        Transitions.createCase(traderServicesApiConnector.createCase(_))(uidAndEori)
      }

  // GET /new/confirmation
  final def showCreateCaseConfirmation: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.CreateCaseConfirmation]
      .orRollback
      .andCleanBreadcrumbs() // forget journey history

  // GET /new/confirmation/receipt
  final def downloadCreateCaseConfirmationReceipt: Action[AnyContent] =
    whenAuthorisedAsUser.showCurrentState
      .displayAsyncUsing(renderConfirmationReceiptHtml)

  // GET /new/confirmation/receipt/pdf/:fileName
  final def downloadCreateCaseConfirmationReceiptAsPdf(fileName: String): Action[AnyContent] =
    whenAuthorisedAsUser.showCurrentState
      .displayAsyncUsing(renderConfirmationReceiptPdf)

  // GET /new/case-already-exists
  final val showCaseAlreadyExists: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.CaseAlreadyExists]
      .orRollback

  // GET /new/case-already-submitted
  final val showCaseAlreadySubmitted: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.CaseAlreadySubmitted.type]
      .orRollback

  /**
    * Function from the `State` to the `Call` (route),
    * used by play-fsm internally to create redirects.
    */
  final override def getCallFor(state: State)(implicit request: Request[_]): Call =
    state match {
      case Start =>
        controller.showStart

      case _: ChooseNewOrExistingCase =>
        controller.showChooseNewOrExistingCase

      case TurnToAmendCaseJourney(continue) =>
        if (continue)
          routes.AmendCaseJourneyController.showEnterCaseReferenceNumber
        else
          routes.AmendCaseJourneyController.showStart

      case _: EnterEntryDetails =>
        controller.showEnterEntryDetails

      case _: AnswerExportQuestionsRequestType =>
        controller.showAnswerExportQuestionsRequestType

      case _: AnswerExportQuestionsRouteType =>
        controller.showAnswerExportQuestionsRouteType

      case _: AnswerExportQuestionsHasPriorityGoods =>
        controller.showAnswerExportQuestionsHasPriorityGoods

      case _: AnswerExportQuestionsWhichPriorityGoods =>
        controller.showAnswerExportQuestionsWhichPriorityGoods

      case _: AnswerExportQuestionsFreightType =>
        controller.showAnswerExportQuestionsFreightType

      case _: AnswerExportQuestionsMandatoryVesselInfo =>
        controller.showAnswerExportQuestionsMandatoryVesselInfo

      case _: AnswerExportQuestionsOptionalVesselInfo =>
        controller.showAnswerExportQuestionsOptionalVesselInfo

      case _: AnswerExportQuestionsContactInfo =>
        controller.showAnswerExportQuestionsContactInfo

      case _: ExportQuestionsSummary =>
        controller.showExportQuestionsSummary

      case _: ExportQuestionsMissingInformationError =>
        controller.showExportQuestionsMissingInformationError

      case _: AnswerImportQuestionsRequestType =>
        controller.showAnswerImportQuestionsRequestType

      case _: AnswerImportQuestionsRouteType =>
        controller.showAnswerImportQuestionsRouteType

      case _: AnswerImportQuestionsHasPriorityGoods =>
        controller.showAnswerImportQuestionsHasPriorityGoods

      case _: AnswerImportQuestionsWhichPriorityGoods =>
        controller.showAnswerImportQuestionsWhichPriorityGoods

      case _: AnswerImportQuestionsALVS =>
        controller.showAnswerImportQuestionsALVS

      case _: AnswerImportQuestionsFreightType =>
        controller.showAnswerImportQuestionsFreightType

      case _: AnswerImportQuestionsMandatoryVesselInfo =>
        controller.showAnswerImportQuestionsMandatoryVesselInfo

      case _: AnswerImportQuestionsOptionalVesselInfo =>
        controller.showAnswerImportQuestionsOptionalVesselInfo

      case _: AnswerImportQuestionsContactInfo =>
        controller.showAnswerImportQuestionsContactInfo

      case _: ImportQuestionsSummary =>
        controller.showImportQuestionsSummary

      case _: ImportQuestionsMissingInformationError =>
        controller.showImportQuestionsMissingInformationError

      case _: FileUploadState.UploadMultipleFiles =>
        controller.showUploadMultipleFiles

      case _: FileUploadState.UploadFile =>
        controller.showFileUpload

      case _: FileUploadState.WaitingForFileVerification =>
        controller.showWaitingForFileVerification

      case _: FileUploadState.FileUploaded =>
        controller.showFileUploaded

      case _: CreateCaseConfirmation =>
        controller.showCreateCaseConfirmation

      case _: CaseAlreadyExists =>
        controller.showCaseAlreadyExists

      case CaseAlreadySubmitted =>
        controller.showCaseAlreadySubmitted

      case _ =>
        workInProgresDeadEndCall

    }

  import uk.gov.hmrc.play.fsm.OptionalFormOps._

  /**
    * Function from the `State` to the `Result`,
    * used by play-fsm internally to render the actual content.
    */
  final override def renderState(state: State, breadcrumbs: List[State], formWithErrors: Option[Form[_]])(implicit
    request: Request[_]
  ): Result =
    state match {

      case Start =>
        Redirect(controller.showChooseNewOrExistingCase)

      case ChooseNewOrExistingCase(newOrExistingCaseOpt, _, _, _, _, _) =>
        Ok(
          views.chooseNewOrExistingCaseView(
            formWithErrors.or(NewOrExistingCaseForm, newOrExistingCaseOpt),
            controller.submitNewOrExistingCaseChoice,
            routes.StartPageController.showGovUkStart
          )
        )

      case TurnToAmendCaseJourney(continue) =>
        if (continue)
          Redirect(routes.AmendCaseJourneyController.showEnterCaseReferenceNumber)
        else
          Redirect(routes.AmendCaseJourneyController.showStart)

      case EnterEntryDetails(entryDetailsOpt, _, _, _) =>
        Ok(
          views.entryDetailsEntryView(
            formWithErrors.or(EntryDetailsForm, entryDetailsOpt),
            controller.submitEntryDetails,
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsRequestType(model) =>
        Ok(
          views.exportQuestionsRequestTypeView(
            formWithErrors.or(ExportRequestTypeForm, model.exportQuestionsAnswers.requestType),
            controller.submitExportQuestionsRequestTypeAnswer,
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsRouteType(model) =>
        Ok(
          views.exportQuestionsRouteTypeView(
            formWithErrors.or(ExportRouteTypeForm, model.exportQuestionsAnswers.routeType),
            controller.submitExportQuestionsRouteTypeAnswer,
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsHasPriorityGoods(model) =>
        Ok(
          views.exportQuestionsHasPriorityGoodsView(
            formWithErrors.or(ExportHasPriorityGoodsForm, model.exportQuestionsAnswers.hasPriorityGoods),
            controller.submitExportQuestionsHasPriorityGoodsAnswer,
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsWhichPriorityGoods(model) =>
        Ok(
          views.exportQuestionsWhichPriorityGoodsView(
            formWithErrors.or(ExportPriorityGoodsForm, model.exportQuestionsAnswers.priorityGoods),
            controller.submitExportQuestionsWhichPriorityGoodsAnswer,
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsFreightType(model) =>
        Ok(
          views.exportQuestionsFreightTypeView(
            formWithErrors.or(ExportFreightTypeForm, model.exportQuestionsAnswers.freightType),
            controller.submitExportQuestionsFreightTypeAnswer,
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsMandatoryVesselInfo(model) =>
        val isArrivalRequestType = ExportRequestType.isArrivalRequestType(model.exportQuestionsAnswers.requestType)
        Ok(
          views.exportQuestionsMandatoryVesselDetailsView(
            formWithErrors
              .or(
                mandatoryExportVesselDetailsForm(extractArrivalDate(state), extractExportRequestType(state)),
                model.exportQuestionsAnswers.vesselDetails
              ),
            controller.submitExportQuestionsMandatoryVesselInfoAnswer,
            isArrivalRequestType,
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsOptionalVesselInfo(model) =>
        val isArrivalRequestType = ExportRequestType.isArrivalRequestType(model.exportQuestionsAnswers.requestType)

        Ok(
          views.exportQuestionsOptionalVesselDetailsView(
            formWithErrors
              .or(
                optionalExportVesselDetailsForm(extractArrivalDate(state), extractExportRequestType(state)),
                model.exportQuestionsAnswers.vesselDetails
              ),
            controller.submitExportQuestionsOptionalVesselInfoAnswer,
            isArrivalRequestType,
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsContactInfo(model) =>
        Ok(
          views.exportQuestionsContactInfoView(
            formWithErrors.or(ExportContactForm, model.exportQuestionsAnswers.contactInfo),
            controller.submitExportQuestionsContactInfoAnswer,
            backLinkFor(breadcrumbs)
          )
        )

      case ExportQuestionsSummary(model) =>
        Ok(
          views.exportQuestionsSummaryView(
            model.entryDetails,
            model.exportQuestionsAnswers,
            model.fileUploadsOpt.getOrElse(FileUploads()),
            controller.createCase,
            if (preferUploadMultipleFiles) controller.showUploadMultipleFiles
            else controller.showFileUpload,
            backLinkFor(breadcrumbs)
          )
        )

      case ExportQuestionsMissingInformationError(model) =>
        Ok(views.missingInformationErrorView(controller.showEnterEntryDetails, backLinkFor(breadcrumbs)))

      case AnswerImportQuestionsRequestType(model) =>
        Ok(
          views.importQuestionsRequestTypeView(
            formWithErrors.or(ImportRequestTypeForm, model.importQuestionsAnswers.requestType),
            controller.submitImportQuestionsRequestTypeAnswer,
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsRouteType(model) =>
        Ok(
          views.importQuestionsRouteTypeView(
            formWithErrors.or(ImportRouteTypeForm, model.importQuestionsAnswers.routeType),
            controller.submitImportQuestionsRouteTypeAnswer,
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsHasPriorityGoods(model) =>
        Ok(
          views.importQuestionsHasPriorityGoodsView(
            formWithErrors.or(ImportHasPriorityGoodsForm, model.importQuestionsAnswers.hasPriorityGoods),
            controller.submitImportQuestionsHasPriorityGoodsAnswer,
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsWhichPriorityGoods(model) =>
        Ok(
          views.importQuestionsWhichPriorityGoodsView(
            formWithErrors.or(ImportPriorityGoodsForm, model.importQuestionsAnswers.priorityGoods),
            controller.submitImportQuestionsWhichPriorityGoodsAnswer,
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsALVS(model) =>
        Ok(
          views.importQuestionsALVSView(
            formWithErrors.or(ImportHasALVSForm, model.importQuestionsAnswers.hasALVS),
            controller.submitImportQuestionsALVSAnswer,
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsFreightType(model) =>
        Ok(
          views.importQuestionsFreightTypeView(
            formWithErrors.or(ImportFreightTypeForm, model.importQuestionsAnswers.freightType),
            controller.submitImportQuestionsFreightTypeAnswer,
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsMandatoryVesselInfo(model) =>
        Ok(
          views.importQuestionsMandatoryVesselDetailsView(
            formWithErrors
              .or(
                mandatoryImportVesselDetailsForm(extractArrivalDate(state)),
                model.importQuestionsAnswers.vesselDetails
              ),
            controller.submitImportQuestionsMandatoryVesselInfoAnswer,
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsOptionalVesselInfo(model) =>
        Ok(
          views.importQuestionsOptionalVesselDetailsView(
            formWithErrors
              .or(
                optionalImportVesselDetailsForm(extractArrivalDate(state)),
                model.importQuestionsAnswers.vesselDetails
              ),
            controller.submitImportQuestionsOptionalVesselInfoAnswer,
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsContactInfo(model) =>
        Ok(
          views.importQuestionsContactInfoView(
            formWithErrors.or(ImportContactForm, model.importQuestionsAnswers.contactInfo),
            controller.submitImportQuestionsContactInfoAnswer,
            backLinkFor(breadcrumbs)
          )
        )

      case ImportQuestionsSummary(model) =>
        Ok(
          views.importQuestionsSummaryView(
            model.entryDetails,
            model.importQuestionsAnswers,
            model.fileUploadsOpt.getOrElse(FileUploads()),
            controller.createCase,
            if (preferUploadMultipleFiles) controller.showUploadMultipleFiles
            else controller.showFileUpload,
            backLinkFor(breadcrumbs)
          )
        )

      case ImportQuestionsMissingInformationError(model) =>
        Ok(views.missingInformationErrorView(controller.showEnterEntryDetails, backLinkFor(breadcrumbs)))

      case FileUploadState.UploadMultipleFiles(model, fileUploads) =>
        Ok(
          views.uploadMultipleFilesView(
            maxFileUploadsNumber,
            fileUploads.files,
            initiateNextFileUpload = controller.initiateNextFileUpload,
            checkFileVerificationStatus = controller.checkFileVerificationStatus,
            removeFile = controller.removeFileUploadByReferenceAsync,
            previewFile = controller.previewFileUploadByReference,
            markFileRejected = controller.markFileUploadAsRejectedAsync,
            exportRequestType = extractRequestType(state),
            continueAction = linkToSummary(model.questionsAnswers),
            backLink = backLinkFor(breadcrumbs)
          )
        )

      case FileUploadState.UploadFile(model, reference, uploadRequest, fileUploads, maybeUploadError) =>
        Ok(
          views.uploadFileView(
            uploadRequest,
            fileUploads,
            maybeUploadError,
            exportRequestType = extractRequestType(state),
            successAction = controller.showFileUploaded,
            failureAction = controller.showFileUpload,
            checkStatusAction = controller.checkFileVerificationStatus(reference),
            backLink = backLinkFor(breadcrumbs)
          )
        )

      case FileUploadState.WaitingForFileVerification(_, reference, _, _, _) =>
        Ok(
          views.waitingForFileVerificationView(
            successAction = controller.showFileUploaded,
            failureAction = controller.showFileUpload,
            checkStatusAction = controller.checkFileVerificationStatus(reference),
            backLink = backLinkFor(breadcrumbs)
          )
        )

      case FileUploadState.FileUploaded(model, fileUploads, _) =>
        Ok(
          if (fileUploads.acceptedCount < maxFileUploadsNumber)
            views.fileUploadedView(
              formWithErrors.or(UploadAnotherFileChoiceForm),
              fileUploads,
              controller.submitUploadAnotherFileChoice,
              controller.previewFileUploadByReference,
              controller.removeFileUploadByReference,
              backLinkFor(breadcrumbs)
            )
          else
            views.fileUploadedSummaryView(
              fileUploads,
              linkToSummary(model.questionsAnswers),
              controller.previewFileUploadByReference,
              controller.removeFileUploadByReference,
              backLinkFor(breadcrumbs)
            )
        )

      case CreateCaseConfirmation(
            entryDetails,
            questionsAnswers,
            uploadedFiles,
            TraderServicesResult(caseReferenceId, generatedAt, _),
            caseSLA
          ) =>
        Ok(
          views.createCaseConfirmationView(
            caseReferenceId,
            entryDetails,
            questionsAnswers,
            uploadedFiles,
            generatedAt.asLondonClockTime.ddMMYYYYAtTimeFormat,
            caseSLA,
            controller.downloadCreateCaseConfirmationReceipt,
            controller.downloadCreateCaseConfirmationReceiptAsPdf(
              s"Document_receipt_${entryDetails.entryNumber.value}.pdf"
            ),
            controller.showStart
          )
        )

      case CaseAlreadyExists(caseReferenceId) =>
        Ok(
          views.caseAlreadyExistsView(
            caseReferenceId,
            routes.AmendCaseJourneyController.showStart
          )
        )

      case CaseAlreadySubmitted =>
        Ok(
          views.caseAlreadySubmittedView(
            routes.CreateCaseJourneyController.showStart
          )
        )

      case _ => NotImplemented

    }

  private def linkToSummary(questionsAnswers: QuestionsAnswers): Call =
    questionsAnswers match {
      case _: ExportQuestions => controller.showExportQuestionsSummary
      case _: ImportQuestions => controller.showImportQuestionsSummary
    }

  private def renderUploadRequestJson(
    uploadId: String
  ) =
    Renderer.simple {
      case s: FileUploadState.UploadMultipleFiles =>
        s.fileUploads
          .findReferenceAndUploadRequestForUploadId(uploadId) match {
          case Some((reference, uploadRequest)) =>
            val json =
              Json.obj(
                "upscanReference" -> reference,
                "uploadId"        -> uploadId,
                "uploadRequest"   -> UploadRequest.formats.writes(uploadRequest)
              )
            Ok(json)

          case None => NotFound
        }

      case _ => Forbidden
    }

  private def renderFileVerificationStatusJson(
    reference: String
  ) =
    Renderer.withRequest(implicit request => {
      case s: FileUploadState =>
        s.fileUploads.findUploadWithUpscanReference(reference) match {
          case Some(file) =>
            Ok(
              Json.toJson(
                FileVerificationStatus(
                  file,
                  uploadFileViewContext,
                  controller.previewFileUploadByReference(_, _),
                  appConfig.fileFormats.maxFileSizeMb
                )
              )
            )
          case None => NotFound
        }
      case _ => NotFound
    })

  private def renderFileRemovalStatusJson(
    reference: String
  ) =
    Renderer.simple {
      case s: FileUploadState => NoContent
      case _                  => BadRequest
    }

  private def streamFileFromUspcan(
    reference: String
  ) =
    AsyncRenderer.simple {
      case s: FileUploadState =>
        s.fileUploads.files.find(_.reference == reference) match {
          case Some(file: FileUpload.Accepted) =>
            getFileStream(
              file.url,
              file.fileName,
              file.fileMimeType,
              (fileName, fileMimeType) =>
                fileMimeType match {
                  case _ =>
                    HeaderNames.CONTENT_DISPOSITION ->
                      s"""inline; filename="${fileName.filter(_.toInt < 128)}"; filename*=utf-8''${RFC3986Encoder
                        .encode(fileName)}"""
                }
            )

          case _ => Future.successful(NotFound)
        }
      case _ => Future.successful(NotFound)
    }

  private def acknowledgeFileUploadRedirect =
    Renderer
      .simple {
        case state =>
          (state match {
            case _: FileUploadState.UploadMultipleFiles        => Created
            case _: FileUploadState.FileUploaded               => Created
            case _: FileUploadState.WaitingForFileVerification => Accepted
            case _                                             => NoContent
          })
            .withHeaders(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
      }

  private val renderConfirmationReceiptHtml =
    AsyncRenderer.withRequest(implicit request => {
      case CreateCaseConfirmation(
            entryDetails,
            _,
            uploadedFiles,
            TraderServicesResult(caseReferenceId, generatedAt, _),
            caseSLA
          ) =>
        printStylesheet.content.map(stylesheet =>
          Ok(
            views.createCaseConfirmationReceiptView(
              caseReferenceId,
              entryDetails,
              uploadedFiles,
              generatedAt.asLondonClockTime.ddMMYYYYAtTimeFormat,
              caseSLA,
              stylesheet
            )
          ).withHeaders(
            HeaderNames.CONTENT_DISPOSITION -> s"""attachment; filename="Document_receipt_${entryDetails.entryNumber.value}.html""""
          )
        )

      case _ => Future.successful(BadRequest)
    })

  private val renderConfirmationReceiptPdf =
    AsyncRenderer.withRequest(implicit request => {
      case CreateCaseConfirmation(
            entryDetails,
            _,
            uploadedFiles,
            TraderServicesResult(caseReferenceId, generatedAt, _),
            caseSLA
          ) =>
        printStylesheet.content
          .map(stylesheet =>
            views
              .createCaseConfirmationReceiptView(
                caseReferenceId,
                entryDetails,
                uploadedFiles,
                generatedAt.asLondonClockTime.ddMMYYYYAtTimeFormat,
                caseSLA,
                stylesheet
              )
              .body
          )
          .flatMap(
            pdfGeneratorConnector.convertHtmlToPdf(_, s"Document_receipt_${entryDetails.entryNumber.value}.pdf")
          )

      case _ => Future.successful(BadRequest)
    })

  private val extractExportRequestType: State => Option[ExportRequestType] = {
    case s: AnswerExportQuestionsMandatoryVesselInfo => s.model.exportQuestionsAnswers.requestType
    case s: AnswerExportQuestionsOptionalVesselInfo  => s.model.exportQuestionsAnswers.requestType
    case _                                           => None
  }

  private val extractArrivalDate: State => Option[LocalDate] = {
    case s: State.HasEntryDetails => Some(s.entryDetails.entryDate)
    case _                        => None
  }

  private val extractRequestType: State => Option[ExportRequestType] = {
    case s: FileUploadState =>
      s.hostData.questionsAnswers match {
        case eq: ExportQuestions => eq.requestType
        case _                   => None
      }
    case _ => None
  }

  private val journeyIdPathParamRegex = ".*?/journey/([A-Za-z0-9-]{36})/.*".r

  final override def journeyId(implicit rh: RequestHeader): Option[String] = {
    val journeyIdFromPath = rh.path match {
      case journeyIdPathParamRegex(id) => Some(id)
      case _                           => None
    }

    val idOpt = journeyIdFromPath
      .orElse(rh.session.get(journeyService.journeyKey))

    idOpt
  }

  private def currentJourneyId(implicit rh: RequestHeader): String = journeyId.get

  final override implicit def context(implicit rh: RequestHeader): HeaderCarrier =
    appendJourneyId(super.hc)

  final override def amendContext(headerCarrier: HeaderCarrier)(key: String, value: String): HeaderCarrier =
    headerCarrier.withExtraHeaders(key -> value)
}

object CreateCaseJourneyController {

  import FormFieldMappings._

  val NewOrExistingCaseForm = Form[NewOrExistingCase](
    mapping("newOrExistingCase" -> newOrExistingCaseMapping)(identity)(Option.apply)
  )

  val EntryDetailsForm = Form[EntryDetails](
    mapping(
      "epu"         -> epuMapping,
      "entryNumber" -> entryNumberMapping,
      "entryDate"   -> entryDateMapping
    )(EntryDetails.apply)(EntryDetails.unapply)
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

  val MandatoryImportVesselDetailsForm = mandatoryImportVesselDetailsForm(None)

  def mandatoryImportVesselDetailsForm(entryDate: Option[LocalDate]) =
    Form[VesselDetails](
      mapping(
        "vesselName" -> mandatoryVesselNameMapping,
        "dateOfArrival" -> mandatoryDateOfArrivalMapping
          .verifying(dateOfArrivalRangeConstraint(entryDate)),
        "timeOfArrival" -> mandatoryTimeOfArrivalMapping
      )(VesselDetails.apply)(VesselDetails.unapply)
    )

  val OptionalImportVesselDetailsForm = optionalImportVesselDetailsForm(None)

  def optionalImportVesselDetailsForm(entryDate: Option[LocalDate]) =
    Form[VesselDetails](
      mapping(
        "vesselName" -> optionalVesselNameMapping,
        "dateOfArrival" -> optionalDateOfArrivalMapping
          .verifying(dateOfArrivalRangeConstraint(entryDate)),
        "timeOfArrival" -> optionalTimeOfArrivalMapping
      )(VesselDetails.apply)(VesselDetails.unapply)
    )

  val MandatoryExportVesselDetailsForm = mandatoryExportVesselDetailsForm(None, None)

  def mandatoryExportVesselDetailsForm(entryDate: Option[LocalDate], exportRequestType: Option[ExportRequestType]) = {
    val isArrivalExportType = ExportRequestType.isArrivalRequestType(exportRequestType)
    val dateMapping =
      if (isArrivalExportType)
        "dateOfArrival" -> mandatoryDateOfArrivalMapping.verifying(dateOfArrivalRangeConstraint(entryDate))
      else
        "dateOfDeparture" -> mandatoryDateOfDepartureMapping.verifying(dateOfDepartureRangeConstraint(entryDate))
    val timeMapping =
      if (isArrivalExportType)
        "timeOfArrival" -> mandatoryTimeOfArrivalMapping
      else
        "timeOfDeparture" -> mandatoryTimeOfDepartureMapping

    Form[VesselDetails](
      mapping(
        "vesselName" -> mandatoryVesselNameMapping,
        dateMapping,
        timeMapping
      )(VesselDetails.apply)(VesselDetails.unapply)
    )
  }

  val OptionalExportVesselDetailsForm = optionalExportVesselDetailsForm(None, None)

  def optionalExportVesselDetailsForm(entryDate: Option[LocalDate], exportRequestType: Option[ExportRequestType]) = {
    val isArrivalExportType = ExportRequestType.isArrivalRequestType(exportRequestType)
    val dateMapping =
      if (isArrivalExportType)
        "dateOfArrival" -> optionalDateOfArrivalMapping
          .verifying(dateOfArrivalRangeConstraint(entryDate))
      else
        "dateOfDeparture" -> optionalDateOfDepartureMapping
          .verifying(dateOfDepartureRangeConstraint(entryDate))
    val timeMapping =
      if (isArrivalExportType)
        "timeOfArrival" -> optionalTimeOfArrivalMapping
      else
        "timeOfDeparture" -> optionalTimeOfDepartureMapping
    Form[VesselDetails](
      mapping(
        "vesselName" -> optionalVesselNameMapping,
        dateMapping,
        timeMapping
      )(VesselDetails.apply)(VesselDetails.unapply)
    )
  }

  val UploadAnotherFileChoiceForm = Form[Boolean](
    mapping("uploadAnotherFile" -> uploadAnotherFileMapping)(identity)(Option.apply)
  )

  val UpscanUploadSuccessForm = Form[S3UploadSuccess](
    mapping(
      "key"    -> nonEmptyText,
      "bucket" -> optional(nonEmptyText)
    )(S3UploadSuccess.apply)(S3UploadSuccess.unapply)
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
