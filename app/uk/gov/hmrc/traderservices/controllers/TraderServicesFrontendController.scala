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
import uk.gov.hmrc.traderservices.models.{DeclarationDetails, ExportFreightType, ExportPriorityGoods, ExportRequestType, ExportRouteType, ImportFreightType, ImportPriorityGoods, ImportRequestType, ImportRouteType}
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

  /** Dummy action to use when developing with loose-ends. */
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

  // GET /pre-clearance/export-questions/has-priority-goods
  val showAnswerExportQuestionsHasPriorityGoods: Action[AnyContent] =
    actionShowStateWhenAuthorised(AsUser) {
      case _: AnswerExportQuestionsHasPriorityGoods =>
    }

  // POST /pre-clearance/export-questions/has-priority-goods
  val submitExportQuestionsHasPriorityGoodsAnswer: Action[AnyContent] =
    action { implicit request =>
      whenAuthorisedWithForm(AsUser)(ExportHasPriorityGoodsForm)(
        Transitions.submittedExportQuestionsAnswerHasPriorityGoods
      )
    }

  // GET /pre-clearance/export-questions/which-priority-goods
  val showAnswerExportQuestionsWhichPriorityGoods: Action[AnyContent] =
    actionShowStateWhenAuthorised(AsUser) {
      case _: AnswerExportQuestionsWhichPriorityGoods =>
    }

  // POST /pre-clearance/export-questions/which-priority-goods
  val submitExportQuestionsWhichPriorityGoodsAnswer: Action[AnyContent] =
    action { implicit request =>
      whenAuthorisedWithForm(AsUser)(ExportPriorityGoodsForm)(
        Transitions.submittedExportQuestionsAnswerWhichPriorityGoods
      )
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

  // GET /pre-clearance/export-questions/vessel-info
  val showAnswerExportQuestionsVesselInfo: Action[AnyContent] =
    actionNotYetImplemented

  // POST /pre-clearance/export-questions/vessel-info
  val submitExportQuestionsVesselInfoAnswer: Action[AnyContent] =
    actionNotYetImplemented

  // GET /pre-clearance/export-questions/contact-info
  val showAnswerExportQuestionsContactInfo: Action[AnyContent] =
    actionNotYetImplemented

  // POST /pre-clearance/export-questions/contact-info
  val submitExportQuestionsContactInfoAnswer: Action[AnyContent] =
    actionNotYetImplemented

  // GET /pre-clearance/import-questions/request-type
  val showAnswerImportQuestionsRequestType: Action[AnyContent] =
    actionShowStateWhenAuthorised(AsUser) {
      case _: AnswerImportQuestionsRequestType =>
    }

  // POST /pre-clearance/import-questions/request-type
  val submitImportQuestionsRequestTypeAnswer: Action[AnyContent] =
    action { implicit request =>
      whenAuthorisedWithForm(AsUser)(ImportRequestTypeForm)(Transitions.submittedImportQuestionsAnswersRequestType)
    }

  // GET /pre-clearance/import-questions/route-type
  val showAnswerImportQuestionsRouteType: Action[AnyContent] =
    actionNotYetImplemented

  // POST /pre-clearance/import-questions/route-type
  val submitImportQuestionsRouteTypeAnswer: Action[AnyContent] =
    actionNotYetImplemented

  // GET /pre-clearance/import-questions/has-priority-goods
  val showAnswerImportQuestionsHasPriorityGoods: Action[AnyContent] =
    actionNotYetImplemented

  // POST /pre-clearance/import-questions/has-priority-goods
  val submitImportQuestionsHasPriorityGoodsAnswer: Action[AnyContent] =
    actionNotYetImplemented

  // GET /pre-clearance/import-questions/which-priority-goods
  val showAnswerImportQuestionsWhichPriorityGoods: Action[AnyContent] =
    actionNotYetImplemented

  // POST /pre-clearance/import-questions/which-priority-goods
  val submitImportQuestionsWhichPriorityGoodsAnswer: Action[AnyContent] =
    actionNotYetImplemented

  // GET /pre-clearance/import-questions/automatic-licence-verification
  val showAnswerImportQuestionsALVS: Action[AnyContent] =
    actionShowStateWhenAuthorised(AsUser) {
      case _: AnswerImportQuestionsALVS =>
    }

  // POST /pre-clearance/import-questions/automatic-licence-verification
  val submitImportQuestionsALVSAnswer: Action[AnyContent] =
    action { implicit request =>
      whenAuthorisedWithForm(AsUser)(ImportHasALVSForm)(
        Transitions.submittedImportQuestionsAnswerHasALVS
      )
    }

  // GET /pre-clearance/import-questions/transport-type
  val showAnswerImportQuestionsFreightType: Action[AnyContent] =
    actionShowStateWhenAuthorised(AsUser) {
      case _: AnswerImportQuestionsFreightType =>
    }

  // POST /pre-clearance/import-questions/transport-type
  val submitImportQuestionsFreightTypeAnswer: Action[AnyContent] =
    action { implicit request =>
      whenAuthorisedWithForm(AsUser)(ImportFreightTypeForm)(Transitions.submittedImportQuestionsAnswerFreightType)
    }

  // GET /pre-clearance/import-questions/vessel-info
  val showAnswerImportQuestionsVesselInfo: Action[AnyContent] =
    actionNotYetImplemented

  // POST /pre-clearance/import-questions/vessel-info
  val submitImportQuestionsVesselInfoAnswer: Action[AnyContent] =
    actionNotYetImplemented

  // GET /pre-clearance/import-questions/contact-info
  val showAnswerImportQuestionsContactInfo: Action[AnyContent] =
    actionNotYetImplemented

  // POST /pre-clearance/import-questions/contact-info
  val submitImportQuestionsContactInfoAnswer: Action[AnyContent] =
    actionNotYetImplemented

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

      case _: AnswerExportQuestionsVesselInfo =>
        routes.TraderServicesFrontendController.showAnswerExportQuestionsVesselInfo()

      case _: AnswerExportQuestionsContactInfo =>
        routes.TraderServicesFrontendController.showAnswerExportQuestionsContactInfo()

      case _: AnswerImportQuestionsRequestType =>
        routes.TraderServicesFrontendController.showAnswerImportQuestionsRequestType()

      case _: AnswerImportQuestionsRouteType =>
        routes.TraderServicesFrontendController.showAnswerImportQuestionsRouteType()

      case _: AnswerImportQuestionsALVS =>
        routes.TraderServicesFrontendController.showAnswerImportQuestionsALVS()

      case _: AnswerImportQuestionsFreightType =>
        routes.TraderServicesFrontendController.showAnswerImportQuestionsFreightType()

      case _: AnswerImportQuestionsVesselInfo =>
        routes.TraderServicesFrontendController.showAnswerImportQuestionsVesselInfo()

      case _: AnswerImportQuestionsContactInfo =>
        routes.TraderServicesFrontendController.showAnswerImportQuestionsContactInfo()

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
            routes.TraderServicesFrontendController.showStart()
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
            routes.TraderServicesFrontendController.submitExportQuestionsRouteTypeAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsHasPriorityGoods(_, exportQuestions) =>
        Ok(
          views.exportQuestionsHasPriorityGoodsView(
            formWithErrors.or(
              exportQuestions.priorityGoods
                .map(query => ExportHasPriorityGoodsForm.fill(true))
                .getOrElse(ExportHasPriorityGoodsForm)
            ),
            routes.TraderServicesFrontendController.submitExportQuestionsHasPriorityGoodsAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsWhichPriorityGoods(_, exportQuestions) =>
        Ok(
          views.exportQuestionsWhichPriorityGoodsView(
            formWithErrors.or(
              exportQuestions.priorityGoods
                .map(query => ExportPriorityGoodsForm.fill(query))
                .getOrElse(ExportPriorityGoodsForm)
            ),
            routes.TraderServicesFrontendController.submitExportQuestionsWhichPriorityGoodsAnswer(),
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
            routes.TraderServicesFrontendController.submitExportQuestionsFreightTypeAnswer(),
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

      case AnswerImportQuestionsHasPriorityGoods(_, importQuestions) =>
        Ok(
          views.importQuestionsGoodsPriorityView(
            formWithErrors.or(
              importQuestions.priorityGoods
                .map(query => ImportGoodsPriorityForm.fill(query))
                .getOrElse(ImportGoodsPriorityForm)
            ),
            workInProgresDeadEndCall
          )
        )

      case AnswerImportQuestionsALVS(_, importQuestions) =>
        Ok(
          views.importQuestionsALVSView(
            formWithErrors.or(
              importQuestions.hasALVS
                .map(query => ImportHasALVSForm.fill(true))
                .getOrElse(ImportHasALVSForm)
            ),
            routes.TraderServicesFrontendController.submitImportQuestionsALVSAnswer(),
            backLinkFor(breadcrumbs)
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
            routes.TraderServicesFrontendController.submitImportQuestionsFreightTypeAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case _ => NotImplemented

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

  val ExportHasPriorityGoodsForm = Form[Boolean](
    mapping("hasPriorityGoods" -> exportHasPriorityGoodsMapping)(identity)(Option.apply)
  )

  val ExportPriorityGoodsForm = Form[ExportPriorityGoods](
    mapping("priorityGoods" -> exportPriorityGoodsMapping)(identity)(Option.apply)
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

  val ImportGoodsPriorityForm = Form[ImportPriorityGoods](
    mapping("hasPriorityGoods" -> importGoodsPriorityMapping)(identity)(Option.apply)
  )

  val ImportFreightTypeForm = Form[ImportFreightType](
    mapping("freightType" -> importFreightTypeMapping)(identity)(Option.apply)
  )

  val ImportHasALVSForm = Form[Boolean](
    mapping("hasALVS" -> importHasALVSMapping)(identity)(Option.apply)
  )
}
