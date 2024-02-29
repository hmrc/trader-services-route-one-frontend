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

package uk.gov.hmrc.traderservices.controllers

import org.apache.pekko.actor.{ActorSystem, Scheduler}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json
import play.api.mvc._
import play.api.{Configuration, Environment}
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.State._
import uk.gov.hmrc.traderservices.journeys.{State, Transition}
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.views.CommonUtilsHelper.DateTimeUtilities
import uk.gov.hmrc.traderservices.views.UploadFileViewContext
import uk.gov.hmrc.traderservices.wiring.AppConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

@Singleton
class AmendCaseJourneyController @Inject() (
  amendCaseJourneyService: AmendCaseJourneyServiceWithHeaderCarrier,
  views: uk.gov.hmrc.traderservices.views.AmendCaseViews,
  traderServicesApiConnector: TraderServicesApiConnector,
  upscanInitiateConnector: UpscanInitiateConnector,
  uploadFileViewContext: UploadFileViewContext,
  printStylesheet: ReceiptStylesheet,
  pdfGeneratorConnector: PdfGeneratorConnector,
  appConfig: AppConfig,
  authConnector: FrontendAuthConnector,
  environment: Environment,
  configuration: Configuration,
  controllerComponents: MessagesControllerComponents,
  val actorSystem: ActorSystem
) extends BaseJourneyController(
      amendCaseJourneyService,
      controllerComponents,
      appConfig,
      authConnector,
      environment,
      configuration
    ) with FileStream {

  final val controller = routes.AmendCaseJourneyController

  import AmendCaseJourneyController._
  import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel._

  implicit val scheduler: Scheduler = actorSystem.scheduler

  private def handleGet[S <: State: ClassTag](
    transition: Transition[State]
  )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
    amendCaseJourneyService.updateSessionState(transition).map {
      case (state: State, breadcrumbs) if amendCaseJourneyService.is[S](state) => renderState(state, breadcrumbs, None)
      case other                                                               => Redirect(getCallFor(other._1))
    }

  // GET /
  final val showStart: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        amendCaseJourneyService
          .updateSessionState(Transitions.start)
          .map { case (state, breadcrumbs) =>
            renderState(state, breadcrumbs, None)
          }
          .andThen { case _ => amendCaseJourneyService.cleanBreadcrumbs }
      }
    }

  // GET /add/case-reference-number
  final val showEnterCaseReferenceNumber: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[EnterCaseReferenceNumber](Transitions.enterCaseReferenceNumber)
      }
    }

  // POST /add/case-reference-number
  final val submitCaseReferenceNumber: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        EnterCaseReferenceNumberForm
          .bindFromRequest()
          .fold(
            formWithErrors =>
              amendCaseJourneyService.currentSessionState.map {
                case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
                case _                          => Redirect(controller.showStart)
              },
            success =>
              amendCaseJourneyService
                .updateSessionState(Transitions.submitedCaseReferenceNumber(success))
                .map(sb => Redirect(getCallFor(sb._1)))
          )
      }
    }

  // GET /add/type-of-amendment
  final val showSelectTypeOfAmendment: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[SelectTypeOfAmendment](Transitions.backToSelectTypeOfAmendment)
      }
    }

  // POST /add/type-of-amendment
  final val submitTypeOfAmendment: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        TypeOfAmendmentForm
          .bindFromRequest()
          .fold(
            formWithErrors =>
              amendCaseJourneyService.currentSessionState.map {
                case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
                case _                          => Redirect(controller.showStart)
              },
            success =>
              amendCaseJourneyService
                .updateSessionState(
                  Transitions.submitedTypeOfAmendment(preferUploadMultipleFiles)(upscanRequest)(
                    upscanInitiateConnector.initiate(_)
                  )(success)
                )
                .map(sb => Redirect(getCallFor(sb._1)))
          )
      }
    }

  // GET /add/write-response
  final val showEnterResponseText: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[EnterResponseText](Transitions.backToEnterResponseText)
      }
    }

  // POST /add/write-response
  final val submitResponseText: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        ResponseTextForm
          .bindFromRequest()
          .fold(
            formWithErrors =>
              amendCaseJourneyService.currentSessionState.map {
                case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
                case _                          => Redirect(controller.showStart)
              },
            success =>
              amendCaseJourneyService
                .updateSessionState(
                  Transitions.submitedResponseText(preferUploadMultipleFiles)(upscanRequest)(
                    upscanInitiateConnector.initiate(_)
                  )(success)
                )
                .map(sb => Redirect(getCallFor(sb._1)))
          )
      }
    }

  // GET 	/add/check-your-answers
  final val showAmendCaseSummary: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[AmendCaseSummary](Transitions.toAmendSummary)
      }
    }

  // GET /new/export/missing-information
  final val showAmendCaseMissingInformationError: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[AmendCaseMissingInformationError](Transitions.backToAmendCaseMissingInformationError)
      }
    }

  // ----------------------- FILES UPLOAD -----------------------

  /** Initial time to wait for callback arrival. */
  final val INITIAL_CALLBACK_WAIT_TIME_SECONDS = 2

  /** This cookie is set by the script on each request coming from one of our own pages open in the browser.
    */
  final val COOKIE_JSENABLED = "jsenabled"

  final def preferUploadMultipleFiles(implicit rh: RequestHeader): Boolean =
    rh.cookies.get(COOKIE_JSENABLED).isDefined && appConfig.uploadMultipleFilesFeature

  final def successRedirect(journeyId: String)(implicit rh: RequestHeader) =
    appConfig.baseExternalCallbackUrl + (rh.cookies.get(COOKIE_JSENABLED) match {
      case Some(_) => controller.asyncWaitingForFileVerification(journeyId)
      case None    => controller.showWaitingForFileVerification
    })

  final def successRedirectWhenUploadingMultipleFiles(implicit rh: RequestHeader) =
    appConfig.baseExternalCallbackUrl + controller.asyncMarkFileUploadAsPosted(journeyId.get)

  final def errorRedirect(journeyId: String)(implicit rh: RequestHeader) =
    appConfig.baseExternalCallbackUrl + (rh.cookies.get(COOKIE_JSENABLED) match {
      case Some(_) => controller.asyncMarkFileUploadAsRejected(journeyId)
      case None    => controller.markFileUploadAsRejected
    })

  final def upscanRequest(nonce: String)(implicit rh: RequestHeader) =
    UpscanInitiateRequest(
      callbackUrl = appConfig.baseInternalCallbackUrl + internal.routes.UpscanCallBackAmendCaseController
        .callbackFromUpscan(currentJourneyId, nonce)
        .url,
      successRedirect = Some(successRedirect(currentJourneyId)),
      errorRedirect = Some(errorRedirect(currentJourneyId)),
      minimumFileSize = Some(1),
      maximumFileSize = Some(appConfig.fileFormats.maxFileSizeMb * 1024 * 1024),
      expectedContentType = Some(appConfig.fileFormats.approvedFileTypes)
    )

  final def upscanRequestWhenUploadingMultipleFiles(nonce: String)(implicit rh: RequestHeader) =
    UpscanInitiateRequest(
      callbackUrl = appConfig.baseInternalCallbackUrl + internal.routes.UpscanCallBackAmendCaseController
        .callbackFromUpscan(currentJourneyId, nonce)
        .url,
      successRedirect = Some(successRedirectWhenUploadingMultipleFiles),
      errorRedirect = Some(errorRedirect(currentJourneyId)),
      minimumFileSize = Some(1),
      maximumFileSize = Some(appConfig.fileFormats.maxFileSizeMb * 1024 * 1024),
      expectedContentType = Some(appConfig.fileFormats.approvedFileTypes)
    )

  // GET /add/upload-files
  final val showUploadMultipleFiles: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[FileUploadState.UploadMultipleFiles](FileUploadTransitions.toUploadMultipleFiles)
      }
    }

  // POST /add/upload-files/initialise/:uploadId
  final def initiateNextFileUpload(uploadId: String): Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        val sessionStateUpdate =
          FileUploadTransitions
            .initiateNextFileUpload(uploadId)(upscanRequestWhenUploadingMultipleFiles)(
              upscanInitiateConnector.initiate(_)
            )
        amendCaseJourneyService
          .updateSessionState(sessionStateUpdate)
          .map(renderUploadRequestJson(uploadId)(request, _))
      }
    }

  // GET /add/file-upload
  final val showFileUpload: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[FileUploadState.UploadFile](
          FileUploadTransitions
            .initiateFileUpload(upscanRequest)(upscanInitiateConnector.initiate(_))
        )
      }
    }

  // GET /add/file-rejected
  final val markFileUploadAsRejected: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        UpscanUploadErrorForm
          .bindFromRequest()
          .fold(
            formWithErrors =>
              amendCaseJourneyService.currentSessionState.map {
                case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
                case _                          => Redirect(controller.showStart)
              },
            success =>
              amendCaseJourneyService
                .updateSessionState(FileUploadTransitions.markUploadAsRejected(success))
                .map(sb => Redirect(getCallFor(sb._1)))
          )
      }
    }

  // POST /new/file-rejected
  final val markFileUploadAsRejectedAsync: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        UpscanUploadErrorForm
          .bindFromRequest()
          .fold(
            formWithErrors =>
              amendCaseJourneyService.currentSessionState.map {
                case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
                case _                          => Redirect(controller.showStart)
              },
            success =>
              amendCaseJourneyService
                .updateSessionState(FileUploadTransitions.markUploadAsRejected(success))
                .map(acknowledgeFileUploadRedirect)
          )
      }
    }

  // GET /add/journey/:journeyId/file-rejected
  final def asyncMarkFileUploadAsRejected(journeyId: String): Action[AnyContent] =
    Action.async { implicit request =>
      whenInSession(journeyId) {
        val journeyKeyHc: HeaderCarrier = hc.withExtraHeaders((amendCaseJourneyService.journeyKey, journeyId))
        UpscanUploadErrorForm
          .bindFromRequest()
          .fold(
            formWithErrors =>
              amendCaseJourneyService.currentSessionState(journeyKeyHc, ec).map {
                case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
                case _                          => Redirect(controller.showStart)
              },
            success =>
              amendCaseJourneyService
                .updateSessionState(FileUploadTransitions.markUploadAsRejected(success))(journeyKeyHc, ec)
                .map(acknowledgeFileUploadRedirect)
          )
      }
    }

  // GET /add/file-verification
  final val showWaitingForFileVerification: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {

        /** Initial time to wait for callback arrival. */
        val intervalInMiliseconds: Long = 500
        val timeoutNanoTime: Long =
          System.nanoTime() + INITIAL_CALLBACK_WAIT_TIME_SECONDS * 1000000000L

        amendCaseJourneyService
          .waitForSessionState[FileUploadState.FileUploaded](intervalInMiliseconds, timeoutNanoTime) {
            amendCaseJourneyService.updateSessionState(FileUploadTransitions.waitForFileVerification)
          }
          .map(response => renderState(response._1, response._2, None))

      }
    }

  // GET /add/journey/:journeyId/file-verification
  final def asyncWaitingForFileVerification(journeyId: String): Action[AnyContent] =
    Action.async { implicit request =>
      whenInSession(journeyId) {

        /** Initial time to wait for callback arrival. */
        val intervalInMiliseconds: Long = 500
        val timeoutNanoTime: Long =
          System.nanoTime() + INITIAL_CALLBACK_WAIT_TIME_SECONDS * 1000000000L
        val journeyKeyHc: HeaderCarrier = hc.withExtraHeaders((amendCaseJourneyService.journeyKey, journeyId))
        amendCaseJourneyService
          .waitForSessionState[FileUploadState.FileUploaded](intervalInMiliseconds, timeoutNanoTime) {
            amendCaseJourneyService.updateSessionState(FileUploadTransitions.waitForFileVerification)(journeyKeyHc, ec)
          }
          .map(acknowledgeFileUploadRedirect)

      }
    }

  // OPTIONS
  final def preflightUpload(journeyId: String): Action[AnyContent] =
    Action {
      Created.withHeaders(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    }

  // GET /new/journey/:journeyId/file-posted
  final def asyncMarkFileUploadAsPosted(journeyId: String): Action[AnyContent] =
    Action.async { implicit request =>
      whenInSession(journeyId) {
        val journeyKeyHc: HeaderCarrier = hc.withExtraHeaders((amendCaseJourneyService.journeyKey, journeyId))
        UpscanUploadSuccessForm
          .bindFromRequest()
          .fold(
            formWithErrors =>
              amendCaseJourneyService.currentSessionState(journeyKeyHc, ec).map {
                case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
                case _                          => Redirect(controller.showStart)
              },
            success =>
              amendCaseJourneyService
                .updateSessionState(FileUploadTransitions.markUploadAsPosted(success))(journeyKeyHc, ec)
                .map(acknowledgeFileUploadRedirect)
          )
      }
    }

  // GET /add/file-uploaded
  final val showFileUploaded: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[FileUploadState.FileUploaded](FileUploadTransitions.backToFileUploaded)
      }
    }

  // POST /add/file-uploaded
  final val submitUploadAnotherFileChoice: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        UploadAnotherFileChoiceForm
          .bindFromRequest()
          .fold(
            formWithErrors =>
              amendCaseJourneyService.currentSessionState.map {
                case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
                case _                          => Redirect(controller.showStart)
              },
            success =>
              amendCaseJourneyService
                .updateSessionState(
                  FileUploadTransitions.submitedUploadAnotherFileChoice(upscanRequest)(
                    upscanInitiateConnector.initiate(_)
                  )(
                    Transitions.toAmendSummary
                  )(success)
                )
                .map(sb => Redirect(getCallFor(sb._1)))
          )
      }
    }

  // GET /add/file-uploaded/:reference/remove
  final def removeFileUploadByReference(reference: String): Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        amendCaseJourneyService
          .updateSessionState(
            FileUploadTransitions.removeFileUploadByReference(reference)(upscanRequest)(
              upscanInitiateConnector.initiate(_)
            )
          )
          .map { case (state, breadcrumbs) =>
            renderState(state, breadcrumbs, None)
          }
      }
    }

  // POST /add/file-uploaded/:reference/remove
  final def removeFileUploadByReferenceAsync(reference: String): Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        val sessionStateUpdate =
          FileUploadTransitions.removeFileUploadByReference(reference)(upscanRequest)(
            upscanInitiateConnector.initiate(_)
          )
        amendCaseJourneyService
          .updateSessionState(sessionStateUpdate)
          .map(renderFileRemovalStatusJson(reference))
      }
    }

  // GET /add/file-uploaded/:reference/:fileName
  final def previewFileUploadByReference(reference: String, fileName: String): Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        amendCaseJourneyService.currentSessionState.flatMap {
          case Some((state, _)) => streamFileFromUspcan(reference)(request, state)
          case None             => NotFound.asFuture
        }
      }
    }

  // GET /add/file-verification/:reference/status
  final def checkFileVerificationStatus(reference: String): Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        amendCaseJourneyService.currentSessionState.map {
          case Some(sab) =>
            renderFileVerificationStatus(reference)(request, sab)
          case None => NotFound
        }
      }
    }

  // ----------------------- CONFIRMATION -----------------------

  // POST /add/amend-case
  final def amendCase: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        withUidAndEori.flatMap { uidAndEori =>
          amendCaseJourneyService
            .updateSessionState(Transitions.amendCase(traderServicesApiConnector.updateCase(_))(uidAndEori))
            .map(sb => Redirect(getCallFor(sb._1)))
        }
      }
    }

  // GET /add/confirmation
  final def showAmendCaseConfirmation: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        amendCaseJourneyService
          .rollback[AmendCaseConfirmation]()
          .map {
            case (state: AmendCaseConfirmation, breadcrumbs) => renderState(state, breadcrumbs, None)
            case _                                           => Redirect(getCallFor(root))
          }
          .andThen { case _ => amendCaseJourneyService.cleanBreadcrumbs }
      }
    }

  // GET /add/confirmation/receipt
  final def downloadAmendCaseConfirmationReceipt: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        amendCaseJourneyService.currentSessionState.flatMap {
          case Some((state, _)) => renderConfirmationReceiptHtml(request, state)
          case None             => NotFound.asFuture
        }
      }
    }

  // GET /add/confirmation/receipt/pdf/:fileName
  final def downloadAmendCaseConfirmationReceiptAsPdf(fileName: String): Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        amendCaseJourneyService.currentSessionState.flatMap {
          case Some((state, _)) => renderConfirmationReceiptPdf(request, state)
          case None             => NotFound.asFuture
        }
      }
    }

  // GET /add/case-already-submitted
  final val showAmendCaseAlreadySubmitted: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        amendCaseJourneyService.rollback[AmendCaseAlreadySubmitted.type]().map {
          case (state: AmendCaseAlreadySubmitted.type, breadcrumbs) => renderState(state, breadcrumbs, None)
          case _                                                    => Redirect(getCallFor(root))
        }
      }
    }

  /** Function from the `State` to the `Call` (route), used by play-fsm internally to create redirects.
    */
  final override def getCallFor(state: State)(implicit request: Request[_]): Call =
    state match {
      case Start =>
        controller.showStart

      case _: EnterCaseReferenceNumber =>
        controller.showEnterCaseReferenceNumber

      case _: SelectTypeOfAmendment =>
        controller.showSelectTypeOfAmendment

      case _: EnterResponseText =>
        controller.showEnterResponseText

      case _: FileUploadState.UploadMultipleFiles =>
        controller.showUploadMultipleFiles

      case _: FileUploadState.UploadFile =>
        controller.showFileUpload

      case _: FileUploadState.WaitingForFileVerification =>
        controller.showWaitingForFileVerification

      case _: FileUploadState.FileUploaded =>
        controller.showFileUploaded

      case _: AmendCaseSummary =>
        controller.showAmendCaseSummary

      case _: AmendCaseConfirmation =>
        controller.showAmendCaseConfirmation

      case _: AmendCaseMissingInformationError =>
        controller.showAmendCaseMissingInformationError

      case AmendCaseAlreadySubmitted =>
        controller.showAmendCaseAlreadySubmitted

      case _ =>
        workInProgresDeadEndCall

    }

  /** Function from the `State` to the `Result`, used by play-fsm internally to render the actual content.
    */
  final def renderState(state: State, breadcrumbs: List[State], formWithErrors: Option[Form[_]])(implicit
    request: Request[_]
  ): Result =
    state match {
      case Start =>
        Redirect(controller.showEnterCaseReferenceNumber)

      case EnterCaseReferenceNumber(model) =>
        Ok(
          views.enterCaseReferenceNumberView(
            formWithErrors.or(EnterCaseReferenceNumberForm, model.caseReferenceNumber),
            controller.submitCaseReferenceNumber,
            if (breadcrumbs.size == 1 && breadcrumbs.head == Start)
              routes.CreateCaseJourneyController.showChooseNewOrExistingCase
            else backLinkFor(breadcrumbs)
          )
        )

      case SelectTypeOfAmendment(model) =>
        Ok(
          views.selectTypeOfAmendmentView(
            formWithErrors.or(TypeOfAmendmentForm, model.typeOfAmendment),
            controller.submitTypeOfAmendment,
            backLinkFor(breadcrumbs)
          )
        )

      case EnterResponseText(model) =>
        Ok(
          views.enterResponseTextView(
            formWithErrors.or(ResponseTextForm, model.responseText),
            controller.submitResponseText,
            backLinkFor(breadcrumbs)
          )
        )

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
            None,
            continueAction = controller.showAmendCaseSummary,
            backLink = backLinkFor(breadcrumbs)
          )
        )

      case FileUploadState.UploadFile(model, reference, uploadRequest, fileUploads, maybeUploadError) =>
        Ok(
          views.uploadFileView(
            uploadRequest,
            fileUploads,
            maybeUploadError,
            None,
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
              controller.showAmendCaseSummary,
              controller.previewFileUploadByReference,
              controller.removeFileUploadByReference,
              backLinkFor(breadcrumbs)
            )
        )

      case AmendCaseSummary(model) =>
        Ok(
          views.amendCaseSummaryView(
            model,
            controller.amendCase,
            if (preferUploadMultipleFiles) controller.showUploadMultipleFiles
            else controller.showFileUpload,
            backLinkFor(breadcrumbs)
          )
        )

      case AmendCaseMissingInformationError(model) =>
        Ok(
          views.missingInformationErrorView(
            controller.showEnterCaseReferenceNumber,
            backLinkFor(breadcrumbs)
          )
        )

      case AmendCaseConfirmation(
            uploadedFiles,
            model,
            TraderServicesResult(caseReferenceNumber, generatedAt, _)
          ) =>
        Ok(
          views.amendCaseConfirmationView(
            caseReferenceNumber,
            uploadedFiles,
            model.responseText,
            generatedAt.asLondonClockTime.ddMMYYYYAtTimeFormat,
            controller.downloadAmendCaseConfirmationReceipt,
            controller.downloadAmendCaseConfirmationReceiptAsPdf(
              s"Document_receipt_$caseReferenceNumber.pdf"
            ),
            routes.CreateCaseJourneyController.showStart
          )
        )

      case AmendCaseAlreadySubmitted =>
        Ok(
          views.caseAlreadySubmittedView(
            routes.CreateCaseJourneyController.showStart
          )
        )

      case _ => NotImplemented

    }

  private def renderUploadRequestJson(
    uploadId: String
  ) =
    resultWithRequestOf(implicit request => {
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
    })

  private def renderFileVerificationStatus(
    reference: String
  ) =
    resultWithRequestOf(implicit request => {
      case s: FileUploadState =>
        s.fileUploads.files.find(_.reference == reference) match {
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
    resultOf {
      case s: FileUploadState => NoContent
      case _                  => BadRequest
    }

  private def streamFileFromUspcan(
    reference: String
  ) =
    asyncResultWithRequestOf(implicit request => {
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

    })

  private lazy val acknowledgeFileUploadRedirect = resultOf { case state =>
    (state match {
      case _: FileUploadState.UploadMultipleFiles        => Created
      case _: FileUploadState.FileUploaded               => Created
      case _: FileUploadState.WaitingForFileVerification => Accepted
      case _                                             => NoContent
    }).withHeaders(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
  }

  private val renderConfirmationReceiptHtml =
    asyncResultWithRequestOf(implicit request => {
      case AmendCaseConfirmation(
            uploadedFiles,
            model,
            TraderServicesResult(caseReferenceNumber, generatedAt, _)
          ) =>
        printStylesheet.content.map(stylesheet =>
          Ok(
            views.amendCaseConfirmationReceiptView(
              model.caseReferenceNumber.get,
              uploadedFiles,
              model.responseText,
              generatedAt.asLondonClockTime.ddMMYYYYAtTimeFormat,
              stylesheet
            )
          ).withHeaders(
            HeaderNames.CONTENT_DISPOSITION -> s"""attachment; filename="Document_receipt_${model.caseReferenceNumber.get}.html""""
          )
        )

      case _ => Future.successful(BadRequest)
    })

  private val renderConfirmationReceiptPdf =
    asyncResultWithRequestOf(implicit request => {
      case AmendCaseConfirmation(
            uploadedFiles,
            model,
            TraderServicesResult(caseReferenceNumber, generatedAt, _)
          ) =>
        printStylesheet.content
          .map(stylesheet =>
            views
              .amendCaseConfirmationReceiptView(
                model.caseReferenceNumber.get,
                uploadedFiles,
                model.responseText,
                generatedAt.asLondonClockTime.ddMMYYYYAtTimeFormat,
                stylesheet
              )
              .body
          )
          .flatMap(
            pdfGeneratorConnector.convertHtmlToPdf(_, s"Document_receipt_${model.caseReferenceNumber.get}.pdf")
          )

      case _ => Future.successful(BadRequest)
    })

  private def resultOf(
    f: PartialFunction[State, Result]
  ): ((State, List[State])) => Result =
    (stateAndBreadcrumbs: (State, List[State])) =>
      f.applyOrElse(stateAndBreadcrumbs._1, (_: State) => play.api.mvc.Results.NotImplemented)

  private def resultWithRequestOf(
    f: Request[_] => PartialFunction[State, Result]
  ): (Request[_], (State, List[State])) => Result =
    (request: Request[_], stateAndBreadcrumbs: (State, List[State])) =>
      f(request).applyOrElse(stateAndBreadcrumbs._1, (_: State) => play.api.mvc.Results.NotImplemented)

  private def asyncResultWithRequestOf(
    f: Request[_] => PartialFunction[State, Future[Result]]
  ): (Request[_], State) => Future[Result] = { (request: Request[_], state: State) =>
    f(request)(state)
  }
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
