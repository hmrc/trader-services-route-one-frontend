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
import uk.gov.hmrc.traderservices.models.{DeclarationDetails, ExportGoodsPriority, ExportQuestions, ExportRequestType, ExportRouteType, ImportQuestions}
import uk.gov.hmrc.traderservices.services.TraderServicesFrontendJourneyServiceWithHeaderCarrier
import uk.gov.hmrc.traderservices.wiring.AppConfig

import scala.concurrent.ExecutionContext
import scala.util.Success
import uk.gov.hmrc.traderservices.models.ExportGoodsPriority

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

  // GET /pre-clearance
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
      whenAuthorisedWithForm(AsUser)(ExportRequestTypeForm)(Transitions.submittedExportQuestionsAnswersRequestType)
    }

  // GET /pre-clearance/import-questions
  val showAnswerImportQuestions: Action[AnyContent] =
    actionShowStateWhenAuthorised(AsUser) {
      case _: AnswerImportQuestions =>
    }

  // POST /pre-clearance/import-questions
  val submitImportQuestionsAnswers: Action[AnyContent] =
    action { implicit request =>
      whenAuthorisedWithForm(AsUser)(ImportQuestionsForm)(Transitions.submittedImportQuestionsAnswers)
    }

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

      case _: AnswerImportQuestions =>
        routes.TraderServicesFrontendController.showAnswerImportQuestions()

      case WorkInProgressDeadEnd =>
        Call("GET", "/trader-services/work-in-progress")

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
            routes.TraderServicesFrontendController.submitDeclarationDetails()
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
            routes.TraderServicesFrontendController.submitExportQuestionsAnswers()
          )
        )

      case AnswerImportQuestions(_, importQuestionsOpt) =>
        Ok(
          views.importQuestionsView(
            formWithErrors.or(
              importQuestionsOpt
                .map(query => ImportQuestionsForm.fill(query))
                .getOrElse(ImportQuestionsForm)
            ),
            routes.TraderServicesFrontendController.submitImportQuestionsAnswers()
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
    requestTypeMapping
  )

  val ExportRouteTypeForm = Form[ExportRouteType](
    routeTypeMapping
  )

  val ExportGoodsPriorityForm = Form[ExportGoodsPriority](
    goodPriorityMapping
  )

  val ImportQuestionsForm = Form[ImportQuestions](
    mapping(
      "requestType"   -> importRequestTypeMapping,
      "routeType"     -> importRouteTypeMapping,
      "goodsPriority" -> importGoodPriorityMapping,
      "freightType"   -> freightTypeMapping
    )(ImportQuestions.apply)(ImportQuestions.unapply)
  )
}
