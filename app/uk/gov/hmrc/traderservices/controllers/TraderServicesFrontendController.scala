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
import uk.gov.hmrc.traderservices.models.{DeclarationDetails, ExportFreightType, ExportPriorityGoods, ExportRequestType, ExportRouteType, ImportContactInfo, ImportFreightType, ImportPriorityGoods, ImportRequestType, ImportRouteType, VesselDetails}
import uk.gov.hmrc.traderservices.services.TraderServicesFrontendJourneyServiceWithHeaderCarrier
import uk.gov.hmrc.traderservices.wiring.AppConfig

import scala.concurrent.ExecutionContext

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

  /** AsUser authorisation request */
  val AsUser: WithAuthorised[String] = { implicit request =>
    authorisedWithEnrolment(appConfig.authorisedServiceName, appConfig.authorisedIdentifierKey)
  }

  /** Base authorized action builder */
  val whenAuthorisedAsUser = actions.whenAuthorised(AsUser)

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

  // GET /pre-clearance/export-questions/request-type
  val showAnswerExportQuestionsRequestType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsRequestType]
      .using(Mergers.copyExportQuestions[AnswerExportQuestionsRequestType])

  // POST /pre-clearance/export-questions/request-type
  val submitExportQuestionsRequestTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportRequestTypeForm)
      .apply(Transitions.submittedExportQuestionsAnswerRequestType)

  // GET /pre-clearance/export-questions/route-type
  val showAnswerExportQuestionsRouteType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsRouteType]
      .using(Mergers.copyExportQuestions[AnswerExportQuestionsRouteType])

  // POST /pre-clearance/export-questions/route-type
  val submitExportQuestionsRouteTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportRouteTypeForm)
      .apply(Transitions.submittedExportQuestionsAnswerRouteType)

  // GET /pre-clearance/export-questions/has-priority-goods
  val showAnswerExportQuestionsHasPriorityGoods: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsHasPriorityGoods]
      .using(Mergers.copyExportQuestions[AnswerExportQuestionsHasPriorityGoods])

  // POST /pre-clearance/export-questions/has-priority-goods
  val submitExportQuestionsHasPriorityGoodsAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportHasPriorityGoodsForm)
      .apply(Transitions.submittedExportQuestionsAnswerHasPriorityGoods)

  // GET /pre-clearance/export-questions/which-priority-goods
  val showAnswerExportQuestionsWhichPriorityGoods: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsWhichPriorityGoods]
      .using(Mergers.copyExportQuestions[AnswerExportQuestionsWhichPriorityGoods])

  // POST /pre-clearance/export-questions/which-priority-goods
  val submitExportQuestionsWhichPriorityGoodsAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportPriorityGoodsForm)
      .apply(Transitions.submittedExportQuestionsAnswerWhichPriorityGoods)

  // GET /pre-clearance/export-questions/transport-type
  val showAnswerExportQuestionsFreightType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsFreightType]
      .using(Mergers.copyExportQuestions[AnswerExportQuestionsFreightType])

  // POST /pre-clearance/export-questions/transport-type
  val submitExportQuestionsFreightTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ExportFreightTypeForm)
      .apply(Transitions.submittedExportQuestionsAnswerFreightType)

  // GET /pre-clearance/export-questions/vessel-info-required
  val showAnswerExportQuestionsMandatoryVesselInfo: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsMandatoryVesselInfo]
      .using(Mergers.copyExportQuestions[AnswerExportQuestionsMandatoryVesselInfo])

  // POST /pre-clearance/export-questions/vessel-info-required
  val submitExportQuestionsMandatoryVesselInfoAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(MandatoryVesselDetailsForm)
      .apply(Transitions.submittedExportQuestionsMandatoryVesselDetails)

  // GET /pre-clearance/export-questions/vessel-info
  val showAnswerExportQuestionsOptionalVesselInfo: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerExportQuestionsOptionalVesselInfo]
      .using(Mergers.copyExportQuestions[AnswerExportQuestionsOptionalVesselInfo])

  // POST /pre-clearance/export-questions/vessel-info
  val submitExportQuestionsOptionalVesselInfoAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(OptionalVesselDetailsForm)
      .apply(Transitions.submittedExportQuestionsOptionalVesselDetails)

  // GET /pre-clearance/export-questions/contact-info
  val showAnswerExportQuestionsContactInfo: Action[AnyContent] =
    actionNotYetImplemented

  // POST /pre-clearance/export-questions/contact-info
  val submitExportQuestionsContactInfoAnswer: Action[AnyContent] =
    actionNotYetImplemented

  // GET /pre-clearance/import-questions/request-type
  val showAnswerImportQuestionsRequestType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsRequestType]
      .using(Mergers.copyImportQuestions[AnswerImportQuestionsRequestType])

  // POST /pre-clearance/import-questions/request-type
  val submitImportQuestionsRequestTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportRequestTypeForm)
      .apply(Transitions.submittedImportQuestionsAnswersRequestType)

  // GET /pre-clearance/import-questions/route-type
  val showAnswerImportQuestionsRouteType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsRouteType]
      .using(Mergers.copyImportQuestions[AnswerImportQuestionsRouteType])

  // POST /pre-clearance/import-questions/route-type
  val submitImportQuestionsRouteTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportRouteTypeForm)
      .apply(Transitions.submittedImportQuestionsAnswerRouteType)

  // GET /pre-clearance/import-questions/has-priority-goods
  val showAnswerImportQuestionsHasPriorityGoods: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsHasPriorityGoods]
      .using(Mergers.copyImportQuestions[AnswerImportQuestionsHasPriorityGoods])

  // POST /pre-clearance/import-questions/has-priority-goods
  val submitImportQuestionsHasPriorityGoodsAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportHasPriorityGoodsForm)
      .apply(Transitions.submittedImportQuestionsAnswerHasPriorityGoods)

  // GET /pre-clearance/import-questions/which-priority-goods
  val showAnswerImportQuestionsWhichPriorityGoods: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsWhichPriorityGoods]
      .using(Mergers.copyImportQuestions[AnswerImportQuestionsWhichPriorityGoods])

  // POST /pre-clearance/import-questions/which-priority-goods
  val submitImportQuestionsWhichPriorityGoodsAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportPriorityGoodsForm)
      .apply(Transitions.submittedImportQuestionsAnswerWhichPriorityGoods)

  // GET /pre-clearance/import-questions/automatic-licence-verification
  val showAnswerImportQuestionsALVS: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsALVS]
      .using(Mergers.copyImportQuestions[AnswerImportQuestionsALVS])

  // POST /pre-clearance/import-questions/automatic-licence-verification
  val submitImportQuestionsALVSAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportHasALVSForm)
      .apply(Transitions.submittedImportQuestionsAnswerHasALVS)

  // GET /pre-clearance/import-questions/transport-type
  val showAnswerImportQuestionsFreightType: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsFreightType]
      .using(Mergers.copyImportQuestions[AnswerImportQuestionsFreightType])

  // POST /pre-clearance/import-questions/transport-type
  val submitImportQuestionsFreightTypeAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportFreightTypeForm)
      .apply(Transitions.submittedImportQuestionsAnswerFreightType)

  // GET /pre-clearance/import-questions/vessel-info
  val showAnswerImportQuestionsOptionalVesselInfo: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsOptionalVesselInfo]
      .using(Mergers.copyImportQuestions[AnswerImportQuestionsOptionalVesselInfo])

  // POST /pre-clearance/import-questions/vessel-info
  val submitImportQuestionsOptionalVesselInfoAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(OptionalVesselDetailsForm)
      .apply(Transitions.submittedImportQuestionsOptionalVesselDetails)

  // GET /pre-clearance/import-questions/contact-info
  val showAnswerImportQuestionsContactInfo: Action[AnyContent] =
    whenAuthorisedAsUser
      .show[State.AnswerImportQuestionsContactInfo]
      .using(Mergers.copyImportQuestions[AnswerImportQuestionsContactInfo])

  // POST /pre-clearance/import-questions/contact-info
  val submitImportQuestionsContactInfoAnswer: Action[AnyContent] =
    whenAuthorisedAsUser
      .bindForm(ImportContactForm)
      .apply(Transitions.submittedImportQuestionsContactInfo)

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

      case _: AnswerImportQuestionsOptionalVesselInfo =>
        routes.TraderServicesFrontendController.showAnswerImportQuestionsOptionalVesselInfo()

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

      case EnterDeclarationDetails(declarationDetailsOpt, _, _) =>
        Ok(
          views.declarationDetailsEntryView(
            formWithErrors.or(DeclarationDetailsForm, declarationDetailsOpt),
            routes.TraderServicesFrontendController.submitDeclarationDetails(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsRequestType(_, exportQuestions) =>
        Ok(
          views.exportQuestionsRequestTypeView(
            formWithErrors.or(ExportRequestTypeForm, exportQuestions.requestType),
            routes.TraderServicesFrontendController.submitExportQuestionsRequestTypeAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsRouteType(_, exportQuestions) =>
        Ok(
          views.exportQuestionsRouteTypeView(
            formWithErrors.or(ExportRouteTypeForm, exportQuestions.routeType),
            routes.TraderServicesFrontendController.submitExportQuestionsRouteTypeAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsHasPriorityGoods(_, exportQuestions) =>
        Ok(
          views.exportQuestionsHasPriorityGoodsView(
            formWithErrors.or(ExportHasPriorityGoodsForm, exportQuestions.hasPriorityGoods),
            routes.TraderServicesFrontendController.submitExportQuestionsHasPriorityGoodsAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsWhichPriorityGoods(_, exportQuestions) =>
        Ok(
          views.exportQuestionsWhichPriorityGoodsView(
            formWithErrors.or(ExportPriorityGoodsForm, exportQuestions.priorityGoods),
            routes.TraderServicesFrontendController.submitExportQuestionsWhichPriorityGoodsAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsFreightType(_, exportQuestions) =>
        Ok(
          views.exportQuestionsFreightTypeView(
            formWithErrors.or(ExportFreightTypeForm, exportQuestions.freightType),
            routes.TraderServicesFrontendController.submitExportQuestionsFreightTypeAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsMandatoryVesselInfo(_, exportQuestions) =>
        Ok(
          views.exportQuestionsMandatoryVesselDetailsView(
            formWithErrors.or(MandatoryVesselDetailsForm, exportQuestions.vesselDetails),
            routes.TraderServicesFrontendController.submitExportQuestionsMandatoryVesselInfoAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerExportQuestionsOptionalVesselInfo(_, exportQuestions) =>
        Ok(
          views.exportQuestionsOptionalVesselDetailsView(
            formWithErrors.or(OptionalVesselDetailsForm, exportQuestions.vesselDetails),
            routes.TraderServicesFrontendController.submitExportQuestionsOptionalVesselInfoAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsRequestType(_, importQuestions) =>
        Ok(
          views.importQuestionsRequestTypeView(
            formWithErrors.or(ImportRequestTypeForm, importQuestions.requestType),
            routes.TraderServicesFrontendController.submitImportQuestionsRequestTypeAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsRouteType(_, importQuestions) =>
        Ok(
          views.importQuestionsRouteTypeView(
            formWithErrors.or(ImportRouteTypeForm, importQuestions.routeType),
            routes.TraderServicesFrontendController.submitImportQuestionsRouteTypeAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsHasPriorityGoods(_, importQuestions) =>
        Ok(
          views.importQuestionsHasPriorityGoodsView(
            formWithErrors.or(ImportHasPriorityGoodsForm, importQuestions.hasPriorityGoods),
            routes.TraderServicesFrontendController.submitImportQuestionsHasPriorityGoodsAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsWhichPriorityGoods(_, importQuestions) =>
        Ok(
          views.importQuestionsWhichPriorityGoodsView(
            formWithErrors.or(ImportPriorityGoodsForm, importQuestions.priorityGoods),
            routes.TraderServicesFrontendController.submitImportQuestionsWhichPriorityGoodsAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsALVS(_, importQuestions) =>
        Ok(
          views.importQuestionsALVSView(
            formWithErrors.or(ImportHasALVSForm, importQuestions.hasALVS),
            routes.TraderServicesFrontendController.submitImportQuestionsALVSAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsFreightType(_, importQuestions) =>
        Ok(
          views.importQuestionsFreightTypeView(
            formWithErrors.or(ImportFreightTypeForm, importQuestions.freightType),
            routes.TraderServicesFrontendController.submitImportQuestionsFreightTypeAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsOptionalVesselInfo(_, importQuestions) =>
        Ok(
          views.importQuestionsOptionalVesselDetailsView(
            formWithErrors.or(OptionalVesselDetailsForm, importQuestions.vesselDetails),
            routes.TraderServicesFrontendController.submitImportQuestionsOptionalVesselInfoAnswer(),
            backLinkFor(breadcrumbs)
          )
        )

      case AnswerImportQuestionsContactInfo(_, importQuestions) =>
        Ok(
          views.importQuestionsContactInfoView(
            formWithErrors.or(ImportContactForm, importQuestions.contactInfo),
            routes.TraderServicesFrontendController.submitImportQuestionsContactInfoAnswer(),
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
}
