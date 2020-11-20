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
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.State._
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.services.AmendCaseJourneyServiceWithHeaderCarrier
import uk.gov.hmrc.traderservices.wiring.AppConfig

import scala.concurrent.ExecutionContext
import uk.gov.hmrc.traderservices.connectors.UpscanInitiateConnector
import play.api.libs.json.Json
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.traderservices.connectors.UpscanInitiateRequest

@Singleton
class AmendCaseJourneyController @Inject() (
  appConfig: AppConfig,
  override val messagesApi: MessagesApi,
  traderServicesApiConnector: TraderServicesApiConnector,
  upscanInitiateConnector: UpscanInitiateConnector,
  val authConnector: FrontendAuthConnector,
  val env: Environment,
  override val journeyService: AmendCaseJourneyServiceWithHeaderCarrier,
  controllerComponents: MessagesControllerComponents,
  views: uk.gov.hmrc.traderservices.views.AmendCaseViews
)(implicit val config: Configuration, ec: ExecutionContext)
    extends FrontendController(controllerComponents) with I18nSupport with AuthActions
    with JourneyController[HeaderCarrier] with JourneyIdSupport[HeaderCarrier] {

  val controller = routes.AmendCaseJourneyController

  import AmendCaseJourneyController._
  import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel._

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
  val workInProgresDeadEndCall = Call("GET", "/trader-services/amend/work-in-progress")

  // GET /
  val showStart: Action[AnyContent] =
    whenAuthorisedAsUser
      .apply(Transitions.start)
      .display
      .andCleanBreadcrumbs()

  // GET /pre-clearance/amend/case-reference-number
  val showEnterCaseReferenceNumber: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.EnterCaseReferenceNumber]
      .orApply(Transitions.enterCaseReferenceNumber)

  // POST /pre-clearance/amend/case-reference-number
  val submitCaseReferenceNumber: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(EnterCaseReferenceNumberForm)
      .apply(Transitions.submitedCaseReferenceNumber)

  // GET /pre-clearance/amend/type-of-amendment
  val showSelectTypeOfAmendment: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.SelectTypeOfAmendment]
      .orApply(Transitions.backToSelectTypeOfAmendment)

  // POST /pre-clearance/amend/type-of-amendment
  val submitTypeOfAmendment: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(TypeOfAmendmentForm)
      .applyWithRequest(implicit request =>
        Transitions.submitedTypeOfAmendment(upscanRequest)(upscanInitiateConnector.initiate(_))
      )

  // GET /pre-clearance/amend/write-response
  val showEnterResponseText: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.EnterResponseText]
      .orApply(Transitions.backToEnterResponseText)

  // POST /pre-clearance/amend/write-response
  val submitResponseText: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ResponseTextForm)
      .applyWithRequest(implicit request =>
        Transitions.submitedResponseText(upscanRequest)(upscanInitiateConnector.initiate(_))
      )

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

  def upscanRequest(implicit rh: RequestHeader) =
    UpscanInitiateRequest(
      callbackUrl = appConfig.baseInternalCallbackUrl + controller.callbackFromUpscan(currentJourneyId).url,
      successRedirect = Some(successRedirect),
      errorRedirect = Some(errorRedirect),
      minimumFileSize = Some(1),
      maximumFileSize = Some(appConfig.fileFormats.maxFileSizeMb * 1024 * 1024),
      expectedContentType = Some(appConfig.fileFormats.approvedFileTypes)
    )

  // GET /pre-clearance/amend/file-upload
  val showFileUpload: Action[AnyContent] =
    whenAuthorisedAsUser
      .applyWithRequest { implicit request =>
        FileUploadTransitions
          .initiateFileUpload(upscanRequest)(upscanInitiateConnector.initiate(_))
      }
      .redirectOrDisplayIf[FileUploadState.UploadFile]

  // GET /pre-clearance/amend/file-rejected
  val markFileUploadAsRejected: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(UpscanUploadErrorForm)
      .apply(FileUploadTransitions.fileUploadWasRejected)

  // GET /pre-clearance/amend/journey/:journeyId/file-rejected-async
  def asyncMarkFileUploadAsRejected(journeyId: String): Action[AnyContent] =
    actions
      .bindForm(UpscanUploadErrorForm)
      .apply(FileUploadTransitions.fileUploadWasRejected(""))
      .displayUsing(implicit request => renderNoContent)

  // GET /pre-clearance/amend/file-verification
  val showWaitingForFileVerification: Action[AnyContent] =
    whenAuthorisedAsUser
      .waitForStateThenRedirect[FileUploadState.FileUploaded](3)
      .orApplyOnTimeout(_ => FileUploadTransitions.waitForFileVerification)
      .redirectOrDisplayIf[FileUploadState.WaitingForFileVerification]

  // GET /pre-clearance/amend/journey/:journeyId/file-verification-async
  def asyncWaitingForFileVerification(journeyId: String): Action[AnyContent] =
    actions
      .waitForStateAndDisplayUsing[FileUploadState.FileUploaded](3, implicit request => renderNoContent)
      .orApplyOnTimeout(_ => FileUploadTransitions.waitForFileVerification(""))
      .displayUsing(implicit request => renderNoContent)

  // POST /pre-clearance/amend/journey/:journeyId/callback-from-upscan
  def callbackFromUpscan(journeyId: String): Action[AnyContent] =
    actions
      .parseJson[UpscanNotification]
      .apply(FileUploadTransitions.upscanCallbackArrived)
      .transform { case _ => Accepted }
      .recover {
        case e: IllegalArgumentException => BadRequest
        case e                           => InternalServerError
      }

  // GET /pre-clearance/amend/file-uploaded
  val showFileUploaded: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[FileUploadState.FileUploaded]
      .orApply(FileUploadTransitions.backToFileUploaded)

  // POST /pre-clearance/amend/file-uploaded
  val submitUploadAnotherFileChoice: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm[Boolean](UploadAnotherFileChoiceForm)
      .applyWithRequest { implicit request =>
        FileUploadTransitions.submitedUploadAnotherFileChoice(upscanRequest)(upscanInitiateConnector.initiate(_))(
          Transitions.amendCase
        ) _
      }

  // GET /pre-clearance/amend/file-uploaded/:reference/remove
  def removeFileUploadByReference(reference: String): Action[AnyContent] =
    whenAuthorisedAsUser
      .applyWithRequest { implicit request =>
        FileUploadTransitions.removeFileUploadByReference(reference)(upscanRequest)(
          upscanInitiateConnector.initiate(_)
        ) _
      }

  // GET /pre-clearance/amend/file-verification/:reference/status
  def checkFileVerificationStatus(reference: String): Action[AnyContent] =
    whenAuthorisedAsUser.showCurrentState
      .displayUsing(implicit request => renderFileVerificationStatus(reference))

  // ----------------------- CONFIRMATION -----------------------

  // POST /pre-clearance/amend/amend-case
  def amendCase: Action[AnyContent] =
    whenAuthorisedAsUser
      .applyWithRequest { implicit request =>
        Transitions.amendCase
      }

  // GET /pre-clearance/amend/confirmation
  def showAmendCaseConfirmation: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AmendCaseConfirmation]
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

      case _: EnterCaseReferenceNumber =>
        controller.showEnterCaseReferenceNumber()

      case _: SelectTypeOfAmendment =>
        controller.showSelectTypeOfAmendment()

      case _: EnterResponseText =>
        controller.showEnterResponseText()

      case _: FileUploadState.UploadFile =>
        controller.showFileUpload()

      case _: FileUploadState.WaitingForFileVerification =>
        controller.showWaitingForFileVerification()

      case _: FileUploadState.FileUploaded =>
        controller.showFileUploaded()

      case _: AmendCaseConfirmation =>
        controller.showAmendCaseConfirmation()

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
        Redirect(controller.showEnterCaseReferenceNumber())

      case EnterCaseReferenceNumber(model) =>
        Ok(
          views.enterCaseReferenceNumberView(
            formWithErrors.or(EnterCaseReferenceNumberForm, model.caseReferenceNumber),
            controller.submitCaseReferenceNumber(),
            routes.CreateCaseJourneyController.showChooseNewOrExistingCase()
          )
        )

      case SelectTypeOfAmendment(model) =>
        Ok(
          views.selectTypeOfAmendmentView(
            formWithErrors.or(TypeOfAmendmentForm, model.typeOfAmendment),
            controller.submitTypeOfAmendment(),
            controller.showEnterCaseReferenceNumber()
          )
        )

      case EnterResponseText(model) =>
        Ok(
          views.enterResponseTextView(
            formWithErrors.or(ResponseTextForm, model.responseText),
            controller.submitResponseText(),
            controller.showSelectTypeOfAmendment()
          )
        )

      case FileUploadState.UploadFile(model, reference, uploadRequest, fileUploads, maybeUploadError) =>
        Ok(
          views.uploadFileView(
            uploadRequest,
            fileUploads,
            maybeUploadError,
            successAction = controller.showFileUploaded,
            failureAction = controller.showFileUpload,
            checkStatusAction = controller.checkFileVerificationStatus(reference),
            backLink =
              if (fileUploads.isEmpty) backLinkFromFileUpload(model)
              else controller.showFileUploaded()
          )
        )

      case FileUploadState.WaitingForFileVerification(_, reference, _, _, _) =>
        Ok(
          views.waitingForFileVerificationView(
            successAction = controller.showFileUploaded,
            failureAction = controller.showFileUpload,
            checkStatusAction = controller.checkFileVerificationStatus(reference),
            backLink = controller.showFileUpload
          )
        )

      case FileUploadState.FileUploaded(model, fileUploads, _) =>
        Ok(
          if (fileUploads.acceptedCount < maxFileUploadsNumber)
            views.fileUploadedView(
              formWithErrors.or(UploadAnotherFileChoiceForm),
              fileUploads,
              controller.submitUploadAnotherFileChoice,
              controller.removeFileUploadByReference,
              backLinkFromFileUpload(model)
            )
          else
            views.fileUploadedSummaryView(
              fileUploads,
              controller.amendCase,
              controller.removeFileUploadByReference,
              backLinkFromFileUpload(model)
            )
        )

      case AmendCaseConfirmation(model) =>
        Ok // FIXME

      case _ => NotImplemented

    }

  def backLinkFromFileUpload(model: AmendCaseModel): Call =
    model.typeOfAmendment match {
      case Some(TypeOfAmendment.WriteResponse) | Some(TypeOfAmendment.WriteResponseAndUploadDocuments) =>
        controller.showEnterResponseText()
      case _ =>
        controller.showSelectTypeOfAmendment()
    }

  def renderFileVerificationStatus(
    reference: String
  )(state: State, breadcrumbs: List[State], formWithErrors: Option[Form[_]])(implicit
    request: Request[_]
  ): Result =
    state match {
      case s: FileUploadState =>
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

object AmendCaseJourneyController {

  import FormFieldMappings._

  val EnterCaseReferenceNumberForm = Form[String](
    mapping("caseReferenceNumber" -> caseReferenceNumberMapping)(identity)(Some(_))
  )

  val TypeOfAmendmentForm = Form[TypeOfAmendment](
    mapping("typeOfAmendment" -> typeOfAmendmentMapping)(identity)(Some(_))
  )

  val ResponseTextForm = Form[String](
    mapping("responseText" -> responseTextMapping)(identity)(Some(_))
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