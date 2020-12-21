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
import uk.gov.hmrc.traderservices.connectors.{FrontendAuthConnector, TraderServicesApiConnector, TraderServicesResult, UpscanInitiateConnector, UpscanInitiateRequest}
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.State._
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.services.AmendCaseJourneyServiceWithHeaderCarrier
import uk.gov.hmrc.traderservices.wiring.AppConfig

import scala.concurrent.ExecutionContext
import play.api.libs.json.Json
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.traderservices.views.CommonUtilsHelper.DateTimeUtilities

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

  final val controller = routes.AmendCaseJourneyController

  import AmendCaseJourneyController._
  import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel._

  /** AsUser authorisation request */
  final val AsUser: WithAuthorised[String] = { implicit request =>
    authorisedWithEnrolment(appConfig.authorisedServiceName, appConfig.authorisedIdentifierKey)
  }

  /** Base authorized action builder */
  final val whenAuthorisedAsUser = actions.whenAuthorised(AsUser)

  /** Dummy action to use only when developing to fill loose-ends. */
  private val actionNotYetImplemented = Action(NotImplemented)

  final def toSubscriptionJourney(continueUrl: String): Result =
    Redirect(
      appConfig.subscriptionJourneyUrl,
      Map(
        "continue" -> Seq(continueUrl)
      )
    )

  // Dummy URL to use when developing the journey
  final val workInProgresDeadEndCall = Call("GET", "/send-documents-for-customs-check/amend/work-in-progress")

  // GET /
  final val showStart: Action[AnyContent] =
    whenAuthorisedAsUser
      .apply(Transitions.start)
      .display
      .andCleanBreadcrumbs()

  // GET /add/case-reference-number
  final val showEnterCaseReferenceNumber: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.EnterCaseReferenceNumber]
      .orApply(Transitions.enterCaseReferenceNumber)

  // POST /add/case-reference-number
  final val submitCaseReferenceNumber: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(EnterCaseReferenceNumberForm)
      .apply(Transitions.submitedCaseReferenceNumber)

  // GET /add/type-of-amendment
  final val showSelectTypeOfAmendment: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.SelectTypeOfAmendment]
      .orApply(Transitions.backToSelectTypeOfAmendment)

  // POST /add/type-of-amendment
  final val submitTypeOfAmendment: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(TypeOfAmendmentForm)
      .applyWithRequest(implicit request =>
        Transitions.submitedTypeOfAmendment(upscanRequest)(upscanInitiateConnector.initiate(_))
      )

  // GET /add/write-response
  final val showEnterResponseText: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.EnterResponseText]
      .orApply(Transitions.backToEnterResponseText)

  // POST /add/write-response
  final val submitResponseText: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ResponseTextForm)
      .applyWithRequest(implicit request =>
        Transitions.submitedResponseText(
          upscanRequest,
          upscanInitiateConnector.initiate(_),
          traderServicesApiConnector.updateCase(_)
        )
      )

  // ----------------------- FILES UPLOAD -----------------------

  /** Initial time to wait for callback arrival. */
  final val INITIAL_CALLBACK_WAIT_TIME_SECONDS = 2

  /**
    * This cookie is set by the script on each request
    * coming from one of our own pages open in the browser.
    */
  final val COOKIE_JSENABLED = "jsenabled"

  final def successRedirect(implicit rh: RequestHeader) =
    appConfig.baseExternalCallbackUrl + (rh.cookies.get(COOKIE_JSENABLED) match {
      case Some(_) => controller.asyncWaitingForFileVerification(journeyId.get)
      case None    => controller.showWaitingForFileVerification()
    })

  final def errorRedirect(implicit rh: RequestHeader) =
    appConfig.baseExternalCallbackUrl + (rh.cookies.get(COOKIE_JSENABLED) match {
      case Some(_) => controller.asyncMarkFileUploadAsRejected(journeyId.get)
      case None    => controller.markFileUploadAsRejected()
    })

  final def upscanRequest(implicit rh: RequestHeader) =
    UpscanInitiateRequest(
      callbackUrl = appConfig.baseInternalCallbackUrl + controller.callbackFromUpscan(currentJourneyId).url,
      successRedirect = Some(successRedirect),
      errorRedirect = Some(errorRedirect),
      minimumFileSize = Some(1),
      maximumFileSize = Some(appConfig.fileFormats.maxFileSizeMb * 1024 * 1024),
      expectedContentType = Some(appConfig.fileFormats.approvedFileTypes)
    )

  // GET /add/file-upload
  final val showFileUpload: Action[AnyContent] =
    whenAuthorisedAsUser
      .applyWithRequest { implicit request =>
        FileUploadTransitions
          .initiateFileUpload(upscanRequest)(upscanInitiateConnector.initiate(_))
      }
      .redirectOrDisplayIf[FileUploadState.UploadFile]

  // GET /add/file-rejected
  final val markFileUploadAsRejected: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(UpscanUploadErrorForm)
      .apply(FileUploadTransitions.fileUploadWasRejected)

  // GET /add/journey/:journeyId/file-rejected-async
  final def asyncMarkFileUploadAsRejected(journeyId: String): Action[AnyContent] =
    actions
      .bindForm(UpscanUploadErrorForm)
      .apply(FileUploadTransitions.fileUploadWasRejected(""))
      .displayUsing(implicit request => acknowledgeFileUploadRedirect)

  // GET /add/file-verification
  final val showWaitingForFileVerification: Action[AnyContent] =
    whenAuthorisedAsUser
      .waitForStateThenRedirect[FileUploadState.FileUploaded](INITIAL_CALLBACK_WAIT_TIME_SECONDS)
      .orApplyOnTimeout(_ => FileUploadTransitions.waitForFileVerification)
      .redirectOrDisplayIf[FileUploadState.WaitingForFileVerification]

  // GET /add/journey/:journeyId/file-verification
  final def asyncWaitingForFileVerification(journeyId: String): Action[AnyContent] =
    actions
      .waitForStateAndDisplayUsing[FileUploadState.FileUploaded](
        INITIAL_CALLBACK_WAIT_TIME_SECONDS,
        implicit request => acknowledgeFileUploadRedirect
      )
      .orApplyOnTimeout(_ => FileUploadTransitions.waitForFileVerification(""))
      .displayUsing(implicit request => acknowledgeFileUploadRedirect)

  // POST /add/journey/:journeyId/callback-from-upscan
  final def callbackFromUpscan(journeyId: String): Action[AnyContent] =
    actions
      .parseJson[UpscanNotification]
      .apply(FileUploadTransitions.upscanCallbackArrived)
      .transform { case _ => NoContent }
      .recover {
        case e: IllegalArgumentException => BadRequest
        case e                           => InternalServerError
      }

  // GET /add/file-uploaded
  final val showFileUploaded: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[FileUploadState.FileUploaded]
      .orApply(FileUploadTransitions.backToFileUploaded)

  // POST /add/file-uploaded
  final val submitUploadAnotherFileChoice: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm[Boolean](UploadAnotherFileChoiceForm)
      .applyWithRequest { implicit request =>
        FileUploadTransitions.submitedUploadAnotherFileChoice(upscanRequest)(upscanInitiateConnector.initiate(_))(
          Transitions.amendCase(traderServicesApiConnector.updateCase(_))
        ) _
      }

  // GET /add/file-uploaded/:reference/remove
  final def removeFileUploadByReference(reference: String): Action[AnyContent] =
    whenAuthorisedAsUser
      .applyWithRequest { implicit request =>
        FileUploadTransitions.removeFileUploadByReference(reference)(upscanRequest)(
          upscanInitiateConnector.initiate(_)
        ) _
      }

  // GET /add/file-verification/:reference/status
  final def checkFileVerificationStatus(reference: String): Action[AnyContent] =
    whenAuthorisedAsUser.showCurrentState
      .displayUsing(implicit request => renderFileVerificationStatus(reference))

  // ----------------------- CONFIRMATION -----------------------

  // POST /add/amend-case
  final def amendCase: Action[AnyContent] =
    whenAuthorisedAsUser
      .applyWithRequest { implicit request =>
        Transitions.amendCase(traderServicesApiConnector.updateCase(_))
      }

  // GET /add/confirmation
  final def showAmendCaseConfirmation: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AmendCaseConfirmation]
      .orRollback
      .andCleanBreadcrumbs() // forget journey history

  /**
    * Function from the `State` to the `Call` (route),
    * used by play-fsm internally to create redirects.
    */
  final override def getCallFor(state: State)(implicit request: Request[_]): Call =
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
  final override def renderState(state: State, breadcrumbs: List[State], formWithErrors: Option[Form[_]])(implicit
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

      case AmendCaseConfirmation(TraderServicesResult(caseReferenceNumber, generatedAt)) =>
        Ok(
          views.amendCaseConfirmationView(
            caseReferenceNumber,
            generatedAt.ddMMYYYYAtTimeFormat,
            routes.CreateCaseJourneyController.showStart()
          )
        )

      case _ => NotImplemented

    }

  private def backLinkFromFileUpload(model: AmendCaseModel): Call =
    model.typeOfAmendment match {
      case Some(TypeOfAmendment.WriteResponse) | Some(TypeOfAmendment.WriteResponseAndUploadDocuments) =>
        controller.showEnterResponseText()
      case _ =>
        controller.showSelectTypeOfAmendment()
    }

  private def renderFileVerificationStatus(
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

  private def acknowledgeFileUploadRedirect(state: State, breadcrumbs: List[State], formWithErrors: Option[Form[_]])(
    implicit request: Request[_]
  ): Result =
    (state match {
      case _: FileUploadState.FileUploaded               => Created
      case _: FileUploadState.WaitingForFileVerification => Accepted
      case _                                             => NoContent
    }).withHeaders(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN -> "*")

  private val journeyIdPathParamRegex = ".*?/journey/([A-Za-z0-9-]{36})/.*".r

  override def journeyId(implicit rh: RequestHeader): Option[String] = {
    val journeyIdFromPath = rh.path match {
      case journeyIdPathParamRegex(id) => Some(id)
      case _                           => None
    }
    journeyIdFromPath.orElse(rh.session.get(journeyService.journeyKey))
  }

  private def currentJourneyId(implicit rh: RequestHeader): String = journeyId.get

  final override implicit def context(implicit rh: RequestHeader): HeaderCarrier =
    appendJourneyId(super.hc)

  final override def amendContext(headerCarrier: HeaderCarrier)(key: String, value: String): HeaderCarrier =
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
