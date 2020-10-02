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
import uk.gov.hmrc.traderservices.models.{DeclarationDetails, ExportFreightType, ExportGoodsPriority, ExportRequestType, ExportRouteType, ImportFreightType, ImportGoodsPriority, ImportRequestType, ImportRouteType}
import uk.gov.hmrc.traderservices.services.TraderServicesFrontendJourneyServiceWithHeaderCarrier
import uk.gov.hmrc.traderservices.wiring.AppConfig

import scala.concurrent.ExecutionContext
import scala.util.Success

@Singleton
class TraderServicesFrontendController @Inject() (
  appConfig: AppConfig,
  override val messagesApi: MessagesApi,
  traderServicesApiConnector: TraderServicesApiConnector,
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

  val AsStrideUser: WithAuthorised[String] = { implicit request =>
    authorisedWithStrideGroup(appConfig.authorisedStrideGroup)
  }

  val AsUser: WithAuthorised[String] = { implicit request =>
    authorisedWithEnrolment(appConfig.authorisedServiceName, appConfig.authorisedIdentifierKey)
  }

  def toSubscriptionJourney(continueUrl: String): Result =
    Redirect(
      appConfig.subscriptionJourneyUrl,
      Map(
        "continue" -> Seq(continueUrl)
      )
    )

  // GET /
  val showStart: Action[AnyContent] =
    action { implicit request =>
      whenAuthorised(AsUser)(Transitions.start)(display)
        .andThen {
          // reset navigation history
          case Success(_) => journeyService.cleanBreadcrumbs()
        }
    }

  // GET /pre-clearance/declaration-details
  val showEnterDeclarationDetails: Action[AnyContent] =
    action { implicit request =>
      whenAuthorised(AsUser)(Transitions.enterDeclarationDetails)(display)
    }

  // POST /pre-clearance/declaration-details
  val submitDeclarationDetails: Action[AnyContent] =
    action { implicit request =>
      whenAuthorisedWithForm(AsUser)(DeclarationDetailsForm)(Transitions.submittedDeclarationDetails)
    }

  // GET /pre-clearance/export-questions/request-type
  val showAnswerExportQuestionsRequestType: Action[AnyContent] =
    actionShowStateWhenAuthorised(AsUser) {
      case _: AnswerExportQuestionsRequestType =>
    }

  // POST /pre-clearance/export-questions/request-type
  val submitExportQuestionsRequestTypeAnswer: Action[AnyContent] =
    action { implicit request =>
      whenAuthorisedWithForm(AsUser)(ExportRequestTypeForm)(Transitions.submittedExportQuestionsAnswerRequestType)
    }

  // GET /pre-clearance/export-questions/route-type
  val showAnswerExportQuestionsRouteType: Action[AnyContent] =
    actionShowStateWhenAuthorised(AsUser) {
      case _: AnswerExportQuestionsRouteType =>
    }

  // POST /pre-clearance/export-questions/route-type
  val submitExportQuestionsRouteTypeAnswer: Action[AnyContent] =
    action { implicit request =>
      whenAuthorisedWithForm(AsUser)(ExportRouteTypeForm)(Transitions.submittedExportQuestionsAnswerRouteType)
    }

  // GET /pre-clearance/export-questions/priority-goods
  val showAnswerExportQuestionsGoodsPriority: Action[AnyContent] =
    actionShowStateWhenAuthorised(AsUser) {
      case _: AnswerExportQuestionsGoodsPriority =>
    }

  // POST /pre-clearance/export-questions/priority-goods
  val submitExportQuestionsGoodsPriorityAnswer: Action[AnyContent] =
    action { implicit request =>
      whenAuthorisedWithForm(AsUser)(ExportGoodsPriorityForm)(Transitions.submittedExportQuestionsAnswerGoodsPriority)
    }

  // GET /pre-clearance/export-questions/transport-type
  val showAnswerExportQuestionsFreightType: Action[AnyContent] =
    actionShowStateWhenAuthorised(AsUser) {
      case _: AnswerExportQuestionsFreightType =>
    }

  // POST /pre-clearance/export-questions/transport-type
  val submitExportQuestionsFreightTypeAnswer: Action[AnyContent] =
    action { implicit request =>
      whenAuthorisedWithForm(AsUser)(ExportFreightTypeForm)(Transitions.submittedExportQuestionsAnswerFreightType)
    }

  // GET /pre-clearance/import-questions
  val showAnswerImportQuestionsRequestType: Action[AnyContent] =
    actionShowStateWhenAuthorised(AsUser) {
      case _: AnswerImportQuestionsRequestType =>
    }

  // POST /pre-clearance/import-questions
  val submitImportQuestionsRequestTypeAnswer: Action[AnyContent] =
    action { implicit request =>
      whenAuthorisedWithForm(AsUser)(ImportRequestTypeForm)(Transitions.submittedImportQuestionsAnswersRequestType)
    }

  val workInProgresDeadEndCall = Call("GET", "/trader-services/work-in-progress")

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

      case _: AnswerExportQuestionsGoodsPriority =>
        routes.TraderServicesFrontendController.showAnswerExportQuestionsGoodsPriority()

      case _: AnswerExportQuestionsFreightType =>
        routes.TraderServicesFrontendController.showAnswerExportQuestionsFreightType()

      case _: AnswerImportQuestionsRequestType =>
        routes.TraderServicesFrontendController.showAnswerImportQuestionsRequestType()

      case WorkInProgressDeadEnd =>
        workInProgresDeadEndCall

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

      case EnterDeclarationDetails(declarationDetailsOpt) =>
        Ok(
          views.declarationDetailsEntryView(
            formWithErrors.or(
              declarationDetailsOpt
                .map(query => DeclarationDetailsForm.fill(query))
                .getOrElse(DeclarationDetailsForm)
            ),
            routes.TraderServicesFrontendController.submitDeclarationDetails(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsRequestType(_, exportQuestions) =>
        Ok(
          views.exportQuestionsRequestTypeView(
            formWithErrors.or(
              exportQuestions.requestType
                .map(query => ExportRequestTypeForm.fill(query))
                .getOrElse(ExportRequestTypeForm)
            ),
            routes.TraderServicesFrontendController.submitExportQuestionsRequestTypeAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsRouteType(_, exportQuestions) =>
        Ok(
          views.exportQuestionsRouteTypeView(
            formWithErrors.or(
              exportQuestions.routeType
                .map(query => ExportRouteTypeForm.fill(query))
                .getOrElse(ExportRouteTypeForm)
            ),
            workInProgresDeadEndCall,
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsGoodsPriority(_, exportQuestions) =>
        Ok(
          views.exportQuestionsGoodsPriorityView(
            formWithErrors.or(
              exportQuestions.goodsPriority
                .map(query => ExportGoodsPriorityForm.fill(query))
                .getOrElse(ExportGoodsPriorityForm)
            ),
            workInProgresDeadEndCall,
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsFreightType(_, exportQuestions) =>
        Ok(
          views.exportQuestionsFreightTypeView(
            formWithErrors.or(
              exportQuestions.freightType
                .map(query => ExportFreightTypeForm.fill(query))
                .getOrElse(ExportFreightTypeForm)
            ),
            workInProgresDeadEndCall,
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsRequestType(_, importQuestions) =>
        Ok(
          views.importQuestionsRequestTypeView(
            formWithErrors.or(
              importQuestions.requestType
                .map(query => ImportRequestTypeForm.fill(query))
                .getOrElse(ImportRequestTypeForm)
            ),
            routes.TraderServicesFrontendController.submitImportQuestionsRequestTypeAnswer()
          )
        )

      case AnswerImportQuestionsRouteType(_, importQuestions) =>
        Ok(
          views.importQuestionsRouteTypeView(
            formWithErrors.or(
              importQuestions.routeType
                .map(query => ImportRouteTypeForm.fill(query))
                .getOrElse(ImportRouteTypeForm)
            ),
            workInProgresDeadEndCall
          )
        )

      case AnswerImportQuestionsGoodsPriority(_, importQuestions) =>
        Ok(
          views.importQuestionsGoodsPriorityView(
            formWithErrors.or(
              importQuestions.goodsPriority
                .map(query => ImportGoodsPriorityForm.fill(query))
                .getOrElse(ImportGoodsPriorityForm)
            ),
            workInProgresDeadEndCall
          )
        )

      case AnswerImportQuestionsFreightType(_, importQuestions) =>
        Ok(
          views.importQuestionsFreightTypeView(
            formWithErrors.or(
              importQuestions.freightType
                .map(query => ImportFreightTypeForm.fill(query))
                .getOrElse(ImportFreightTypeForm)
            ),
            workInProgresDeadEndCall
          )
        )

      case WorkInProgressDeadEnd => NotImplemented

    }

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

  val ExportGoodsPriorityForm = Form[ExportGoodsPriority](
    mapping("goodsPriority" -> exportGoodsPriorityMapping)(identity)(Option.apply)
  )

  val ExportFreightTypeForm = Form[ExportFreightType](
    mapping("freightType" -> exportFreightTypeMapping)(identity)(Option.apply)
  )

  val ImportRequestTypeForm = Form[ImportRequestType](
    mapping("requestType" -> importRequestTypeMapping)(identity)(Option.apply)
  )

  val ImportRouteTypeForm = Form[ImportRouteType](
    mapping("routeType" -> importRouteTypeMapping)(identity)(Option.apply)
  )

  val ImportGoodsPriorityForm = Form[ImportGoodsPriority](
    mapping("goodsPriority" -> importGoodsPriorityMapping)(identity)(Option.apply)
  )

  val ImportFreightTypeForm = Form[ImportFreightType](
    mapping("freightType" -> importFreightTypeMapping)(identity)(Option.apply)
  )
}
