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

import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{authorisedEnrolments, credentials}
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.{AuthProviders, Enrolment}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.WithUnsafeDefaultFormBinding
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.traderservices.connectors.FrontendAuthConnector
import uk.gov.hmrc.traderservices.journeys.State
import uk.gov.hmrc.traderservices.services.SessionStateService
import uk.gov.hmrc.traderservices.utils.SHA256
import uk.gov.hmrc.traderservices.wiring.AppConfig

import scala.concurrent.{ExecutionContext, Future}

/** Base controller class for a journey. */
abstract class BaseJourneyController[S <: SessionStateService](
  val journeyService: S,
  val controllerComponents: MessagesControllerComponents,
  appConfig: AppConfig,
  val authConnector: FrontendAuthConnector,
  val env: Environment,
  val config: Configuration
) extends FrontendBaseController with MessagesBaseController with WithUnsafeDefaultFormBinding with I18nSupport
    with AuthActions {

  import journeyService.Breadcrumbs

  final override val messagesApi = controllerComponents.messagesApi

  implicit val ec: ExecutionContext = controllerComponents.executionContext

  implicit class FutureOps[A](value: A) {
    def asFuture: Future[A] = Future.successful(value)
  }

  /** Provide response when user have no required enrolment. */
  final def toSubscriptionJourney(continueUrl: String): Result =
    Redirect(appConfig.subscriptionJourneyUrl)

  final def AsAuthorisedUser(body: => Future[Result])(implicit request: Request[_]): Future[Result] =
    if (appConfig.requireEnrolmentFeature) {
      authorisedWithEnrolment(appConfig.authorisedServiceName, appConfig.authorisedIdentifierKey)(body)
    } else {
      authorised(AuthProviders(GovernmentGateway))
        .retrieve(credentials)(_ => body)
        .recover(handleFailure)
    }

  final def withUidAndEori(implicit request: Request[_]): Future[(Option[String], Option[String])] =
    if (appConfig.requireEnrolmentFeature) {
      authorised(
        Enrolment(appConfig.authorisedServiceName)
          and AuthProviders(GovernmentGateway)
      )
        .retrieve(credentials and authorisedEnrolments) {
          case credentials ~ enrolments =>
            val id = for {
              enrolment  <- enrolments.getEnrolment(appConfig.authorisedServiceName)
              identifier <- enrolment.getIdentifier(appConfig.authorisedIdentifierKey)
            } yield identifier.value

            Future.successful(credentials.map(_.providerId), id)
          case _ => Future.successful(None, None)
        }
    } else {
      authorised(AuthProviders(GovernmentGateway))
        .retrieve(credentials) { case _ =>
          Future.successful(None, None)
        }
    }

  /** Dummy action to use only when developing to fill loose-ends. */
  final val actionNotYetImplemented = Action(NotImplemented)

  // Dummy URL to use when developing the journey
  final val workInProgresDeadEndCall = Call("GET", "/send-documents-for-customs-check/work-in-progress")

  // ------------------------------------
  // Retrieval of journeyId configuration
  // ------------------------------------

  private val journeyIdPathParamRegex = ".*?/journey/([A-Za-z0-9-]{36})/.*".r

  final def journeyId(implicit rh: RequestHeader): Option[String] =
    journeyId(decodeHeaderCarrier(rh), rh)

  private def journeyId(hc: HeaderCarrier, rh: RequestHeader): Option[String] =
    (rh.path match {
      case journeyIdPathParamRegex(id) => Some(id)
      case _                           => None
    })
      .orElse(hc.sessionId.map(_.value).map(SHA256.compute))

  final def currentJourneyId(implicit rh: RequestHeader): String = journeyId.get

  final implicit def context(implicit rh: RequestHeader): HeaderCarrier = {
    val hc = decodeHeaderCarrier(rh)
    journeyId(rh)
      .map(jid => hc.withExtraHeaders(journeyService.journeyKey -> jid))
      .getOrElse(hc)
  }

  private def decodeHeaderCarrier(rh: RequestHeader): HeaderCarrier =
    HeaderCarrierConverter.fromRequestAndSession(rh, rh.session)

  final def amendContext(headerCarrier: HeaderCarrier)(key: String, value: String): HeaderCarrier =
    headerCarrier.withExtraHeaders(key -> value)

  /** Function mapping FSM states to the endpoint calls. This function is invoked internally when the result of an
    * action is to *redirect* to some state.
    */
  def getCallFor(state: State)(implicit request: Request[_]): Call

  /** Returns a call to the most recent state found in breadcrumbs, otherwise returns a call to the root state.
    */
  final def backLinkFor(breadcrumbs: Breadcrumbs)(implicit request: Request[_]): Call =
    breadcrumbs.headOption
      .map(getCallFor)
      .getOrElse(getCallFor(journeyService.root))

  type Renderer = Request[_] => (State, Breadcrumbs, Option[Form[_]]) => Result
  type AsyncRenderer = Request[_] => (State, Breadcrumbs, Option[Form[_]]) => Future[Result]

  object Renderer {
    final def simple(f: PartialFunction[State, Result]): Renderer = {
      (request: Request[_]) => (state: State, breadcrumbs: Breadcrumbs, formWithErrors: Option[Form[_]]) =>
        f.applyOrElse(state, (_: State) => play.api.mvc.Results.NotImplemented)
    }

    final def withRequest(f: Request[_] => PartialFunction[State, Result]): Renderer = {
      (request: Request[_]) => (state: State, breadcrumbs: Breadcrumbs, formWithErrors: Option[Form[_]]) =>
        f(request).applyOrElse(state, (_: State) => play.api.mvc.Results.NotImplemented)
    }

    final def withRequestAndForm(
      f: Request[_] => Option[Form[_]] => PartialFunction[State, Result]
    ): Renderer = {
      (request: Request[_]) => (state: State, breadcrumbs: Breadcrumbs, formWithErrors: Option[Form[_]]) =>
        f(request)(formWithErrors)(state)
    }

    final def apply(
      f: Request[_] => Breadcrumbs => Option[Form[_]] => PartialFunction[State, Result]
    ): Renderer = {
      (request: Request[_]) => (state: State, breadcrumbs: Breadcrumbs, formWithErrors: Option[Form[_]]) =>
        f(request)(breadcrumbs)(formWithErrors)(state)
    }
  }

  object AsyncRenderer {
    final def simple(f: PartialFunction[State, Future[Result]]): AsyncRenderer = {
      (request: Request[_]) => (state: State, breadcrumbs: Breadcrumbs, formWithErrors: Option[Form[_]]) =>
        f(state)
    }

    final def withRequest(
      f: Request[_] => PartialFunction[State, Future[Result]]
    ): AsyncRenderer = {
      (request: Request[_]) => (state: State, breadcrumbs: Breadcrumbs, formWithErrors: Option[Form[_]]) =>
        f(request)(state)
    }

    final def withRequestAndForm(
      f: Request[_] => Option[Form[_]] => PartialFunction[State, Future[Result]]
    ): AsyncRenderer = {
      (request: Request[_]) => (state: State, breadcrumbs: Breadcrumbs, formWithErrors: Option[Form[_]]) =>
        f(request)(formWithErrors)(state)
    }

    final def apply(
      f: Request[_] => Breadcrumbs => Option[Form[_]] => PartialFunction[State, Future[Result]]
    ): AsyncRenderer = {
      (request: Request[_]) => (state: State, breadcrumbs: Breadcrumbs, formWithErrors: Option[Form[_]]) =>
        f(request)(breadcrumbs)(formWithErrors)(state)
    }
  }

  final def whenInSession(journeyId: String)(
    body: => Future[Result]
  )(implicit rh: RequestHeader, rc: HeaderCarrier): Future[Result] =
    journeyId match {
      case jid if jid.isEmpty => Future.successful(Redirect(appConfig.govukStartUrl))
      case _                  => body
    }
}
