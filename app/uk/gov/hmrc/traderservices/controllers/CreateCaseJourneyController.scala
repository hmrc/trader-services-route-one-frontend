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

import akka.actor.{ActorSystem, Scheduler}
import com.fasterxml.jackson.core.JsonParseException
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json
import play.api.mvc._
import play.api.{Configuration, Environment}
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.traderservices.connectors._
import uk.gov.hmrc.traderservices.controllers.CreateCaseJourneyController._
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.CreateCaseJourneyState._
import uk.gov.hmrc.traderservices.journeys.{State, Transition}
import uk.gov.hmrc.traderservices.models._
import uk.gov.hmrc.traderservices.services.{CreateCaseJourneyServiceWithHeaderCarrier, SessionStateService}
import uk.gov.hmrc.traderservices.views.CommonUtilsHelper.DateTimeUtilities
import uk.gov.hmrc.traderservices.views.UploadFileViewContext
import uk.gov.hmrc.traderservices.wiring.AppConfig

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CreateCaseJourneyController @Inject() (
  sessionStateService: SessionStateService,
  createCaseJourneyService: CreateCaseJourneyServiceWithHeaderCarrier,
  views: uk.gov.hmrc.traderservices.views.CreateCaseViews,
  traderServicesApiConnector: TraderServicesApiConnector,
  upscanInitiateConnector: UpscanInitiateConnector,
  uploadFileViewContext: UploadFileViewContext,
  pdfGeneratorConnector: PdfGeneratorConnector,
  printStylesheet: ReceiptStylesheet,
  appConfig: AppConfig,
  authConnector: FrontendAuthConnector,
  environment: Environment,
  configuration: Configuration,
  controllerComponents: MessagesControllerComponents,
  val actorSystem: ActorSystem
)(implicit ec: ExecutionContext)
    extends BaseJourneyController(
      createCaseJourneyService,
      controllerComponents,
      appConfig,
      authConnector,
      environment,
      configuration
    ) with FileStream {

  final val controller = routes.CreateCaseJourneyController

  import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel._

  implicit val scheduler: Scheduler = actorSystem.scheduler

  private def handleGet[T](
    transition: Transition[State]
  )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
    sessionStateService.updateSessionState(transition).map {
      case (state: T, breadcrumbs) => renderState(state, breadcrumbs, None)
      case other                   => Redirect(getCallFor(other._1))
    }

  // GET /
  val showStart: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        sessionStateService
          .updateSessionState(Transitions.start)
          .map { case (state, breadcrumbs) =>
            renderState(state, breadcrumbs, None)
          }
          .andThen { case _ => sessionStateService.cleanBreadcrumbs }
      }
    }

  // GET /new-or-existing
  val showChooseNewOrExistingCase: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[ChooseNewOrExistingCase](Transitions.chooseNewOrExistingCase)
      }
    }

  // POST /new-or-existing
  final val submitNewOrExistingCaseChoice: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        NewOrExistingCaseForm.bindFromRequest.fold(
          formWithErrors =>
            sessionStateService.currentSessionState.map {
              case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
              case _                          => Redirect(controller.showStart)
            },
          success =>
            sessionStateService
              .updateSessionState(Transitions.submittedNewOrExistingCaseChoice(success))
              .map(sb => Redirect(getCallFor(sb._1)))
        )
      }
    }

  // GET /new/entry-details
  final val showEnterEntryDetails: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[EnterEntryDetails](Transitions.backToEnterEntryDetails)
      }
    }

  // POST /new/entry-details
  final val submitEntryDetails: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        EntryDetailsForm.bindFromRequest.fold(
          formWithErrors =>
            sessionStateService.currentSessionState.map {
              case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
              case _                          => Redirect(controller.showStart)
            },
          success =>
            sessionStateService
              .updateSessionState(Transitions.submittedEntryDetails(success))
              .map(sb => Redirect(getCallFor(sb._1)))
        )
      }
    }

  // ----------------------- EXPORT QUESTIONS -----------------------

  // GET /new/export/request-type
  final val showAnswerExportQuestionsRequestType: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[AnswerExportQuestionsRequestType](Transitions.backToAnswerExportQuestionsRequestType)
      }
    }

  // POST /new/export/request-type
  final val submitExportQuestionsRequestTypeAnswer: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        ExportRequestTypeForm.bindFromRequest.fold(
          formWithErrors =>
            sessionStateService.currentSessionState.map {
              case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
              case _                          => Redirect(controller.showStart)
            },
          success =>
            sessionStateService
              .updateSessionState(
                Transitions.submittedExportQuestionsAnswerRequestType(appConfig.requireOptionalTransportFeature)(
                  success
                )
              )
              .map(sb => Redirect(getCallFor(sb._1)))
        )
      }
    }

  // GET /new/export/route-type
  final val showAnswerExportQuestionsRouteType: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[AnswerExportQuestionsRouteType](Transitions.backToAnswerExportQuestionsRouteType)
      }
    }

  // POST /new/export/route-type
  final val submitExportQuestionsRouteTypeAnswer: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        ExportRouteTypeForm.bindFromRequest.fold(
          formWithErrors =>
            sessionStateService.currentSessionState.map {
              case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
              case _                          => Redirect(controller.showStart)
            },
          success =>
            sessionStateService
              .updateSessionState(
                Transitions.submittedExportQuestionsAnswerRouteType(appConfig.requireOptionalTransportFeature)(success)
              )
              .map(sb => Redirect(getCallFor(sb._1)))
        )
      }
    }

  // GET /new/export/reason
  final val showAnswerExportQuestionsReason: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[AnswerExportQuestionsReason](Transitions.backToAnswerExportQuestionsReason)
      }
    }

  // POST /new/export/reason
  final val submitExportQuestionsReasonAnswer: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        ExportReasonForm.bindFromRequest.fold(
          formWithErrors =>
            sessionStateService.currentSessionState.map {
              case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
              case _                          => Redirect(controller.showStart)
            },
          success =>
            sessionStateService
              .updateSessionState(Transitions.submittedExportQuestionsAnswerReason(success))
              .map(sb => Redirect(getCallFor(sb._1)))
        )
      }
    }

  // GET /new/export/has-priority-goods
  final val showAnswerExportQuestionsHasPriorityGoods: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[AnswerExportQuestionsHasPriorityGoods](Transitions.backToAnswerExportQuestionsHasPriorityGoods)
      }
    }

  // POST /new/export/has-priority-goods
  final val submitExportQuestionsHasPriorityGoodsAnswer: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        ExportHasPriorityGoodsForm.bindFromRequest.fold(
          formWithErrors =>
            sessionStateService.currentSessionState.map {
              case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
              case _                          => Redirect(controller.showStart)
            },
          success =>
            sessionStateService
              .updateSessionState(Transitions.submittedExportQuestionsAnswerHasPriorityGoods(success))
              .map(sb => Redirect(getCallFor(sb._1)))
        )
      }
    }

  // GET /new/export/which-priority-goods
  final val showAnswerExportQuestionsWhichPriorityGoods: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[AnswerExportQuestionsWhichPriorityGoods](Transitions.backToAnswerExportQuestionsWhichPriorityGoods)
      }
    }

  // POST /new/export/which-priority-goods
  final val submitExportQuestionsWhichPriorityGoodsAnswer: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        ExportPriorityGoodsForm.bindFromRequest.fold(
          formWithErrors =>
            sessionStateService.currentSessionState.map {
              case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
              case _                          => Redirect(controller.showStart)
            },
          success =>
            sessionStateService
              .updateSessionState(Transitions.submittedExportQuestionsAnswerWhichPriorityGoods(success))
              .map(sb => Redirect(getCallFor(sb._1)))
        )
      }
    }

  // GET /new/export/transport-type
  final val showAnswerExportQuestionsFreightType: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[AnswerExportQuestionsFreightType](Transitions.backToAnswerExportQuestionsFreightType)
      }
    }

  // POST /new/export/transport-type
  final val submitExportQuestionsFreightTypeAnswer: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        ExportFreightTypeForm.bindFromRequest.fold(
          formWithErrors =>
            sessionStateService.currentSessionState.map {
              case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
              case _                          => Redirect(controller.showStart)
            },
          success =>
            sessionStateService
              .updateSessionState(
                Transitions.submittedExportQuestionsAnswerFreightType(appConfig.requireOptionalTransportFeature)(
                  success
                )
              )
              .map(sb => Redirect(getCallFor(sb._1)))
        )
      }
    }

  // GET /new/export/transport-information-required
  final val showAnswerExportQuestionsMandatoryVesselInfo: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[AnswerExportQuestionsMandatoryVesselInfo](Transitions.backToAnswerExportQuestionsVesselInfo)
      }
    }

  // POST /new/export/transport-information-required
  final val submitExportQuestionsMandatoryVesselInfoAnswer: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        sessionStateService.currentSessionState.flatMap {
          case Some((state, breadcrumbs)) =>
            mandatoryExportVesselDetailsForm(extractArrivalDate(state), extractExportRequestType(state)).bindFromRequest
              .fold(
                formWithErrors => Future.successful(renderState(state, breadcrumbs, Some(formWithErrors))),
                success =>
                  sessionStateService
                    .updateSessionState(Transitions.submittedExportQuestionsMandatoryVesselDetails(success))
                    .map(sb => Redirect(getCallFor(sb._1)))
              )
          case _ => Future.successful(Redirect(controller.showStart))
        }
      }
    }

  // GET /new/export/transport-information
  final val showAnswerExportQuestionsOptionalVesselInfo: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[AnswerExportQuestionsOptionalVesselInfo](Transitions.backToAnswerExportQuestionsVesselInfo)
      }
    }

  // POST /new/export/transport-information
  final val submitExportQuestionsOptionalVesselInfoAnswer: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        sessionStateService.currentSessionState.flatMap {
          case Some((state, breadcrumbs)) =>
            optionalExportVesselDetailsForm(extractArrivalDate(state), extractExportRequestType(state)).bindFromRequest
              .fold(
                formWithErrors => Future.successful(renderState(state, breadcrumbs, Some(formWithErrors))),
                success =>
                  sessionStateService
                    .updateSessionState(Transitions.submittedExportQuestionsOptionalVesselDetails(success))
                    .map(sb => Redirect(getCallFor(sb._1)))
              )
          case _ => Future.successful(Redirect(controller.showStart))
        }
      }
    }

  // GET /new/export/contact-information
  final val showAnswerExportQuestionsContactInfo: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[AnswerExportQuestionsContactInfo](Transitions.backToAnswerExportQuestionsContactInfo)
      }
    }

  // POST /new/export/contact-information
  final val submitExportQuestionsContactInfoAnswer: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        ExportContactForm.bindFromRequest.fold(
          formWithErrors =>
            sessionStateService.currentSessionState.map {
              case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
              case _                          => Redirect(controller.showStart)
            },
          success =>
            sessionStateService
              .updateSessionState(
                Transitions.submittedExportQuestionsContactInfo(preferUploadMultipleFiles)(upscanRequest)(
                  upscanInitiateConnector.initiate(_)
                )(success)
              )
              .map(sb => Redirect(getCallFor(sb._1)))
        )
      }
    }

  // GET /new/export/check-your-answers
  final val showExportQuestionsSummary: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[ExportQuestionsSummary](Transitions.toSummary)
      }
    }

  // GET /new/export/missing-information
  final val showExportQuestionsMissingInformationError: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[ExportQuestionsMissingInformationError](Transitions.backToExportQuestionsMissingInformationError)
      }
    }

  // ----------------------- IMPORT QUESTIONS -----------------------

  // GET /new/import/request-type
  final val showAnswerImportQuestionsRequestType: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[AnswerImportQuestionsRequestType](Transitions.backToAnswerImportQuestionsRequestType)
      }
    }

  // POST /new/import/request-type
  final val submitImportQuestionsRequestTypeAnswer: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        ImportRequestTypeForm.bindFromRequest.fold(
          formWithErrors =>
            sessionStateService.currentSessionState.map {
              case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
              case _                          => Redirect(controller.showStart)
            },
          success =>
            sessionStateService
              .updateSessionState(Transitions.submittedImportQuestionsAnswersRequestType(success))
              .map(sb => Redirect(getCallFor(sb._1)))
        )
      }
    }

  // GET /new/import/route-type
  final val showAnswerImportQuestionsRouteType: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[AnswerImportQuestionsRouteType](Transitions.backToAnswerImportQuestionsRouteType)
      }
    }

  // POST /new/import/route-type
  final val submitImportQuestionsRouteTypeAnswer: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        ImportRouteTypeForm.bindFromRequest.fold(
          formWithErrors =>
            sessionStateService.currentSessionState.map {
              case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
              case _                          => Redirect(controller.showStart)
            },
          success =>
            sessionStateService
              .updateSessionState(
                Transitions.submittedImportQuestionsAnswerRouteType(appConfig.requireOptionalTransportFeature)(success)
              )
              .map(sb => Redirect(getCallFor(sb._1)))
        )
      }
    }

  // GET /new/import/reason
  final val showAnswerImportQuestionsReason: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[AnswerImportQuestionsReason](Transitions.backToAnswerImportQuestionsReason)
      }
    }

  // POST /new/import/reason
  final val submitImportQuestionsReasonAnswer: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        ImportReasonForm.bindFromRequest.fold(
          formWithErrors =>
            sessionStateService.currentSessionState.map {
              case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
              case _                          => Redirect(controller.showStart)
            },
          success =>
            sessionStateService
              .updateSessionState(Transitions.submittedImportQuestionsAnswerReason(success))
              .map(sb => Redirect(getCallFor(sb._1)))
        )
      }
    }

  // GET /new/import/has-priority-goods
  final val showAnswerImportQuestionsHasPriorityGoods: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[AnswerImportQuestionsHasPriorityGoods](Transitions.backToAnswerImportQuestionsHasPriorityGoods)
      }
    }

  // POST /new/import/has-priority-goods
  final val submitImportQuestionsHasPriorityGoodsAnswer: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        ImportHasPriorityGoodsForm.bindFromRequest.fold(
          formWithErrors =>
            sessionStateService.currentSessionState.map {
              case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
              case _                          => Redirect(controller.showStart)
            },
          success =>
            sessionStateService
              .updateSessionState(Transitions.submittedImportQuestionsAnswerHasPriorityGoods(success))
              .map(sb => Redirect(getCallFor(sb._1)))
        )
      }
    }

  // GET /new/import/which-priority-goods
  final val showAnswerImportQuestionsWhichPriorityGoods: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[AnswerImportQuestionsWhichPriorityGoods](Transitions.backToAnswerImportQuestionsWhichPriorityGoods)
      }
    }

  // POST /new/import/which-priority-goods
  final val submitImportQuestionsWhichPriorityGoodsAnswer: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        ImportPriorityGoodsForm.bindFromRequest.fold(
          formWithErrors =>
            sessionStateService.currentSessionState.map {
              case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
              case _                          => Redirect(controller.showStart)
            },
          success =>
            sessionStateService
              .updateSessionState(Transitions.submittedImportQuestionsAnswerWhichPriorityGoods(success))
              .map(sb => Redirect(getCallFor(sb._1)))
        )
      }
    }

  // GET /new/import/automatic-licence-verification
  final val showAnswerImportQuestionsALVS: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[AnswerImportQuestionsALVS](Transitions.backToAnswerImportQuestionsALVS)
      }
    }

  // POST /new/import/automatic-licence-verification
  final val submitImportQuestionsALVSAnswer: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        ImportHasALVSForm.bindFromRequest.fold(
          formWithErrors =>
            sessionStateService.currentSessionState.map {
              case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
              case _                          => Redirect(controller.showStart)
            },
          success =>
            sessionStateService
              .updateSessionState(Transitions.submittedImportQuestionsAnswerHasALVS(success))
              .map(sb => Redirect(getCallFor(sb._1)))
        )
      }
    }

  // GET /new/import/transport-type
  final val showAnswerImportQuestionsFreightType: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[AnswerImportQuestionsFreightType](Transitions.backToAnswerImportQuestionsFreightType)
      }
    }

  // POST /new/import/transport-type
  final val submitImportQuestionsFreightTypeAnswer: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        ImportFreightTypeForm.bindFromRequest.fold(
          formWithErrors =>
            sessionStateService.currentSessionState.map {
              case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
              case _                          => Redirect(controller.showStart)
            },
          success =>
            sessionStateService
              .updateSessionState(
                Transitions.submittedImportQuestionsAnswerFreightType(appConfig.requireOptionalTransportFeature)(
                  success
                )
              )
              .map(sb => Redirect(getCallFor(sb._1)))
        )
      }
    }

  // GET /new/import/transport-information-required
  final val showAnswerImportQuestionsMandatoryVesselInfo: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[AnswerImportQuestionsMandatoryVesselInfo](Transitions.backToAnswerImportQuestionsVesselInfo)
      }
    }

  // POST /new/import/transport-information-required
  final val submitImportQuestionsMandatoryVesselInfoAnswer: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        sessionStateService.currentSessionState.flatMap {
          case Some((state, breadcrumbs)) =>
            mandatoryImportVesselDetailsForm(extractArrivalDate(state)).bindFromRequest.fold(
              formWithErrors => Future.successful(renderState(state, breadcrumbs, Some(formWithErrors))),
              success =>
                sessionStateService
                  .updateSessionState(Transitions.submittedImportQuestionsMandatoryVesselDetails(success))
                  .map(sb => Redirect(getCallFor(sb._1)))
            )
          case _ => Future.successful(Redirect(controller.showStart))
        }
      }
    }

  // GET /new/import/transport-information
  final val showAnswerImportQuestionsOptionalVesselInfo: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[AnswerImportQuestionsOptionalVesselInfo](Transitions.backToAnswerImportQuestionsVesselInfo)
      }
    }

  // POST /new/import/transport-information
  final val submitImportQuestionsOptionalVesselInfoAnswer: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        sessionStateService.currentSessionState.flatMap {
          case Some((state, breadcrumbs)) =>
            optionalImportVesselDetailsForm(extractArrivalDate(state)).bindFromRequest.fold(
              formWithErrors => Future.successful(renderState(state, breadcrumbs, Some(formWithErrors))),
              success =>
                sessionStateService
                  .updateSessionState(Transitions.submittedImportQuestionsOptionalVesselDetails(success))
                  .map(sb => Redirect(getCallFor(sb._1)))
            )
          case _ => Future.successful(Redirect(controller.showStart))
        }
      }
    }

  // GET /new/import/contact-information
  final val showAnswerImportQuestionsContactInfo: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[AnswerImportQuestionsContactInfo](Transitions.backToAnswerImportQuestionsContactInfo)
      }
    }

  // POST /new/import/contact-information
  final val submitImportQuestionsContactInfoAnswer: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        ImportContactForm.bindFromRequest.fold(
          formWithErrors =>
            sessionStateService.currentSessionState.map {
              case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
              case _                          => Redirect(controller.showStart)
            },
          success =>
            sessionStateService
              .updateSessionState(
                Transitions.submittedImportQuestionsContactInfo(preferUploadMultipleFiles)(upscanRequest)(
                  upscanInitiateConnector.initiate(_)
                )(success)
              )
              .map(sb => Redirect(getCallFor(sb._1)))
        )
      }
    }

  // GET /new/import/check-your-answers
  final val showImportQuestionsSummary: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[ImportQuestionsSummary](Transitions.toSummary)
      }
    }

  // GET /new/import/missing-information
  final val showImportQuestionsMissingInformationError: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[ImportQuestionsMissingInformationError](Transitions.backToImportQuestionsMissingInformationError)
      }
    }

  // ----------------------- FILES UPLOAD -----------------------

  /** Initial time to wait for callback arrival. */
  final val INITIAL_CALLBACK_WAIT_TIME_SECONDS = 2
  final val intervalInMiliseconds: Long = 500

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

  final def successRedirectWhenUploadingMultipleFiles(journeyId: String)(implicit rh: RequestHeader) =
    appConfig.baseExternalCallbackUrl + controller.asyncMarkFileUploadAsPosted(journeyId)

  final def errorRedirect(journeyId: String)(implicit rh: RequestHeader) =
    appConfig.baseExternalCallbackUrl + (rh.cookies.get(COOKIE_JSENABLED) match {
      case Some(_) => controller.asyncMarkFileUploadAsRejected(journeyId)
      case None    => controller.markFileUploadAsRejected
    })

  final def upscanRequest(nonce: String)(implicit rh: RequestHeader) =
    UpscanInitiateRequest(
      callbackUrl = appConfig.baseInternalCallbackUrl + controller.callbackFromUpscan(currentJourneyId, nonce).url,
      successRedirect = Some(successRedirect(currentJourneyId)),
      errorRedirect = Some(errorRedirect(currentJourneyId)),
      minimumFileSize = Some(1),
      maximumFileSize = Some(appConfig.fileFormats.maxFileSizeMb * 1024 * 1024),
      expectedContentType = Some(appConfig.fileFormats.approvedFileTypes)
    )

  final def upscanRequestWhenUploadingMultipleFiles(nonce: String)(implicit rh: RequestHeader) =
    UpscanInitiateRequest(
      callbackUrl = appConfig.baseInternalCallbackUrl + controller.callbackFromUpscan(currentJourneyId, nonce).url,
      successRedirect = Some(successRedirectWhenUploadingMultipleFiles(currentJourneyId)),
      errorRedirect = Some(errorRedirect(currentJourneyId)),
      minimumFileSize = Some(1),
      maximumFileSize = Some(appConfig.fileFormats.maxFileSizeMb * 1024 * 1024),
      expectedContentType = Some(appConfig.fileFormats.approvedFileTypes)
    )

  // GET /new/upload-files
  final val showUploadMultipleFiles: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[FileUploadState.UploadMultipleFiles](FileUploadTransitions.toUploadMultipleFiles)
      }
    }

  // POST /new/upload-files/initialise/:uploadId
  final def initiateNextFileUpload(uploadId: String): Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        val sessionStateUpdate =
          FileUploadTransitions
            .initiateNextFileUpload(uploadId)(upscanRequestWhenUploadingMultipleFiles)(
              upscanInitiateConnector.initiate(_)
            )
        sessionStateService
          .updateSessionState(sessionStateUpdate)
          .map(renderUploadRequestJson(uploadId)(request, _))
      }
    }

  // GET /new/file-upload
  final val showFileUpload: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[FileUploadState.UploadFile](
          FileUploadTransitions
            .initiateFileUpload(upscanRequest)(upscanInitiateConnector.initiate(_))
        )
      }
    }

  // GET /new/file-rejected
  final val markFileUploadAsRejected: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        UpscanUploadErrorForm.bindFromRequest.fold(
          formWithErrors =>
            sessionStateService.currentSessionState.map {
              case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
              case _                          => Redirect(controller.showStart)
            },
          success =>
            sessionStateService
              .updateSessionState(FileUploadTransitions.markUploadAsRejected(success))
              .map(sb => Redirect(getCallFor(sb._1)))
        )
      }
    }

  // POST /new/file-rejected
  final val markFileUploadAsRejectedAsync: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        UpscanUploadErrorForm.bindFromRequest.fold(
          formWithErrors =>
            sessionStateService.currentSessionState.map {
              case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
              case _                          => Redirect(controller.showStart)
            },
          success =>
            sessionStateService
              .updateSessionState(FileUploadTransitions.markUploadAsRejected(success))
              .map(acknowledgeFileUploadRedirect)
        )
      }
    }

  // GET /new/journey/:journeyId/file-rejected
  final def asyncMarkFileUploadAsRejected(journeyId: String): Action[AnyContent] =
    Action.async { implicit request =>
      whenInSession(journeyId) {
        val journeyKeyHc: HeaderCarrier = hc.withExtraHeaders((sessionStateService.journeyKey, journeyId))
        UpscanUploadErrorForm.bindFromRequest.fold(
          formWithErrors =>
            sessionStateService.currentSessionState(journeyKeyHc, ec).map {
              case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
              case _                          => Redirect(controller.showStart)
            },
          success =>
            sessionStateService
              .updateSessionState(FileUploadTransitions.markUploadAsRejected(success))(journeyKeyHc, ec)
              .map(acknowledgeFileUploadRedirect)
        )
      }
    }

  // GET /new/file-verification
  final val showWaitingForFileVerification: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {

        /** Initial time to wait for callback arrival. */
        val intervalInMiliseconds: Long = 500
        val timeoutNanoTime: Long =
          System.nanoTime() + INITIAL_CALLBACK_WAIT_TIME_SECONDS * 1000000000L

        sessionStateService
          .waitForSessionState[FileUploadState.FileUploaded](intervalInMiliseconds, timeoutNanoTime) {
            sessionStateService.updateSessionState(FileUploadTransitions.waitForFileVerification)
          }
          .map(response => renderState(response._1, response._2, None))

      }
    }

  // GET /new/journey/:journeyId/file-verification
  final def asyncWaitingForFileVerification(journeyId: String): Action[AnyContent] =
    Action.async { implicit request =>
      whenInSession(journeyId) {

        /** Initial time to wait for callback arrival. */
        val intervalInMiliseconds: Long = 500
        val timeoutNanoTime: Long =
          System.nanoTime() + INITIAL_CALLBACK_WAIT_TIME_SECONDS * 1000000000L
        val journeyKeyHc: HeaderCarrier = hc.withExtraHeaders((sessionStateService.journeyKey, journeyId))
        sessionStateService
          .waitForSessionState[FileUploadState.FileUploaded](intervalInMiliseconds, timeoutNanoTime) {
            sessionStateService.updateSessionState(FileUploadTransitions.waitForFileVerification)(journeyKeyHc, ec)
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
        val journeyKeyHc: HeaderCarrier = hc.withExtraHeaders((sessionStateService.journeyKey, journeyId))
        UpscanUploadSuccessForm.bindFromRequest.fold(
          formWithErrors =>
            sessionStateService.currentSessionState(journeyKeyHc, ec).map {
              case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
              case _                          => Redirect(controller.showStart)
            },
          success =>
            sessionStateService
              .updateSessionState(FileUploadTransitions.markUploadAsPosted(success))(journeyKeyHc, ec)
              .map(acknowledgeFileUploadRedirect)
        )
      }
    }

  // POST /callback-from-upscan/new/journey/:journeyId/:nonce
  final def callbackFromUpscan(journeyId: String, nonce: String): Action[AnyContent] =
    Action.async { implicit request =>
      whenInSession(journeyId) {
        val journeyKeyHc: HeaderCarrier = hc.withExtraHeaders((sessionStateService.journeyKey, journeyId))
        Future(request.body.asJson.flatMap(_.asOpt[UpscanNotification]))
          .flatMap {
            case Some(payload) =>
              sessionStateService
                .updateSessionState(FileUploadTransitions.upscanCallbackArrived(Nonce(nonce))(payload))(
                  journeyKeyHc,
                  ec
                )
                .map(_ => NoContent)

            case None => BadRequest.asFuture
          }
          .recover {
            case e: JsonParseException => BadRequest(e.getMessage())
            case e                     => InternalServerError
          }
      }
    }

  // GET /new/file-uploaded
  final val showFileUploaded: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        handleGet[FileUploadState.FileUploaded](FileUploadTransitions.backToFileUploaded)
      }
    }

  // POST /new/file-uploaded
  final val submitUploadAnotherFileChoice: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        UploadAnotherFileChoiceForm.bindFromRequest.fold(
          formWithErrors =>
            sessionStateService.currentSessionState.map {
              case Some((state, breadcrumbs)) => renderState(state, breadcrumbs, Some(formWithErrors))
              case _                          => Redirect(controller.showStart)
            },
          success =>
            sessionStateService
              .updateSessionState(
                FileUploadTransitions.submitedUploadAnotherFileChoice(upscanRequest)(
                  upscanInitiateConnector.initiate(_)
                )(
                  Transitions.toSummary
                )(success)
              )
              .map(sb => Redirect(getCallFor(sb._1)))
        )
      }
    }

  // GET /new/file-uploaded/:reference/remove
  final def removeFileUploadByReference(reference: String): Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        sessionStateService
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

  // POST /new/file-uploaded/:reference/remove
  final def removeFileUploadByReferenceAsync(reference: String): Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        val sessionStateUpdate =
          FileUploadTransitions.removeFileUploadByReference(reference)(upscanRequest)(
            upscanInitiateConnector.initiate(_)
          )
        sessionStateService
          .updateSessionState(sessionStateUpdate)
          .map(renderFileRemovalStatusJson(reference))
      }
    }

  // GET /new/file-uploaded/:reference/:fileName
  final def previewFileUploadByReference(reference: String, fileName: String): Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        sessionStateService.currentSessionState.flatMap {
          case Some((state, _)) => streamFileFromUspcan(reference)(request, state)
          case None             => NotFound.asFuture
        }
      }
    }

  // GET /new/file-verification/:reference/status
  final def checkFileVerificationStatus(reference: String): Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        sessionStateService.currentSessionState.map {
          case Some(sab) =>
            renderFileVerificationStatusJson(reference)(request, sab)
          case None => NotFound
        }
      }
    }

  // ----------------------- CONFIRMATION -----------------------

  // POST /new/create-case
  final def createCase: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        withUidAndEori.flatMap { uidAndEori =>
          sessionStateService
            .updateSessionState(Transitions.createCase(traderServicesApiConnector.createCase(_))(uidAndEori))
            .map(sb => Redirect(getCallFor(sb._1)))
        }
      }
    }

  // GET /new/confirmation
  final def showCreateCaseConfirmation: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        sessionStateService
          .rollback[CreateCaseJourneyState.CreateCaseConfirmation]()
          .map { case (state, breadcrumbs) =>
            renderState(state, breadcrumbs, None)
          }
          .andThen { case _ => sessionStateService.cleanBreadcrumbs }
      }
    }

  // GET /new/confirmation/receipt
  final def downloadCreateCaseConfirmationReceipt: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        sessionStateService.currentSessionState.flatMap {
          case Some((state, _)) => renderConfirmationReceiptHtml(request, state)
          case None             => NotFound.asFuture
        }
      }
    }

  // GET /new/confirmation/receipt/pdf/:fileName
  final def downloadCreateCaseConfirmationReceiptAsPdf(fileName: String): Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        sessionStateService.currentSessionState.flatMap {
          case Some((state, _)) => renderConfirmationReceiptPdf(request, state)
          case None             => NotFound.asFuture
        }
      }
    }

  // GET /new/case-already-exists
  final val showCaseAlreadyExists: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        sessionStateService.rollback[CreateCaseJourneyState.CaseAlreadyExists]().map { case (state, breadcrumbs) =>
          renderState(state, breadcrumbs, None)
        }
      }
    }

  // GET /new/case-already-submitted
  final val showCaseAlreadySubmitted: Action[AnyContent] =
    Action.async { implicit request =>
      AsAuthorisedUser {
        sessionStateService.rollback[CreateCaseJourneyState.CaseAlreadySubmitted.type]().map {
          case (state, breadcrumbs) => renderState(state, breadcrumbs, None)
        }
      }
    }

  /** Function from the `State` to the `Call` (route), used by play-fsm internally to create redirects.
    */
  final override def getCallFor(state: State)(implicit request: Request[_]): Call =
    state match {
      case Start =>
        controller.showStart

      case _: ChooseNewOrExistingCase =>
        controller.showChooseNewOrExistingCase

      case TurnToAmendCaseJourney(continue) =>
        if (continue)
//          routes.AmendCaseJourneyController.showEnterCaseReferenceNumber temporarily commented for testing
          routes.CreateCaseJourneyController.showStart
        else
//          routes.AmendCaseJourneyController.showStart    temporarily commented for testing
          routes.CreateCaseJourneyController.showStart
      case _: EnterEntryDetails =>
        controller.showEnterEntryDetails

      case _: AnswerExportQuestionsRequestType =>
        controller.showAnswerExportQuestionsRequestType

      case _: AnswerExportQuestionsRouteType =>
        controller.showAnswerExportQuestionsRouteType

      case _: AnswerExportQuestionsReason =>
        controller.showAnswerExportQuestionsReason

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

      case _: AnswerImportQuestionsReason =>
        controller.showAnswerImportQuestionsReason

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

  import uk.gov.hmrc.traderservices.support.OptionalFormOps._

  /** Function from the `State` to the `Result`, to render the actual content.
    */
  final def renderState(state: State, breadcrumbs: List[State], formWithErrors: Option[Form[_]])(implicit
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
//          Redirect(routes.AmendCaseJourneyController.showEnterCaseReferenceNumber)  temporarily commented for testing
          Redirect(routes.CreateCaseJourneyController.showStart)
        else
//          Redirect(routes.AmendCaseJourneyController.showStart)  temporarily commented for testing
          Redirect(routes.CreateCaseJourneyController.showStart)

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

      case AnswerExportQuestionsReason(model) =>
        Ok(
          views.exportQuestionsReasonView(
            formWithErrors.or(ExportReasonForm, model.exportQuestionsAnswers.reason),
            controller.submitExportQuestionsReasonAnswer,
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

      case AnswerImportQuestionsReason(model) =>
        Ok(
          views.importQuestionsReasonView(
            formWithErrors.or(ImportReasonForm, model.importQuestionsAnswers.reason),
            controller.submitImportQuestionsReasonAnswer,
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
//            routes.AmendCaseJourneyController.showStart temporarily commented for testing
            routes.CreateCaseJourneyController.showStart
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

  private def renderFileVerificationStatusJson(
    reference: String
  ) =
    resultWithRequestOf(implicit request => {
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

  private def acknowledgeFileUploadRedirect =
    resultOf { case state =>
      (state match {
        case _: FileUploadState.UploadMultipleFiles        => Created
        case _: FileUploadState.FileUploaded               => Created
        case _: FileUploadState.WaitingForFileVerification => Accepted
        case _                                             => NoContent
      })
        .withHeaders(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    }

  private val renderConfirmationReceiptHtml =
    asyncResultWithRequestOf(implicit request => {
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
    asyncResultWithRequestOf(implicit request => {
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
    case s: CreateCaseJourneyState.HasEntryDetails => Some(s.entryDetails.entryDate)
    case _                                         => None
  }

  private val extractRequestType: State => Option[ExportRequestType] = {
    case s: FileUploadState =>
      s.hostData.questionsAnswers match {
        case eq: ExportQuestions => eq.requestType
        case _                   => None
      }
    case _ => None
  }

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

  private def asyncResultOf(
    f: PartialFunction[State, Future[Result]]
  ): State => Future[Result] = { (state: State) =>
    f(state)
  }

  private def asyncResultWithRequestOf(
    f: Request[_] => PartialFunction[State, Future[Result]]
  ): (Request[_], State) => Future[Result] = { (request: Request[_], state: State) =>
    f(request)(state)
  }
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

  val ExportReasonForm = Form[String](
    mapping("reasonText" -> exportReasonTextMapping)(identity)(Some(_))
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

  val ImportReasonForm = Form[String](
    mapping("reasonText" -> importReasonTextMapping)(identity)(Some(_))
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
