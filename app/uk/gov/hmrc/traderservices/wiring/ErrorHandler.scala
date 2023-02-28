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

package uk.gov.hmrc.traderservices.wiring

import com.google.inject.name.Named
import play.api._
import play.api.i18n.MessagesApi
import play.api.mvc.Results._
import play.api.mvc.{Request, RequestHeader, Result}
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.auth.core.{InsufficientEnrolments, NoActiveSession}
import uk.gov.hmrc.http.{JsValidationException, NotFoundException}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.{AuthRedirects, HttpAuditEvent}
import uk.gov.hmrc.play.bootstrap.frontend.http.FrontendErrorHandler
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.traderservices.connectors.TraderServicesAmendApiError
import uk.gov.hmrc.traderservices.models.DateTimeHelper
import uk.gov.hmrc.traderservices.views.html.templates.{ErrorTemplate, GovukLayoutWrapper}
import uk.gov.hmrc.traderservices.views.html.{AmendCaseErrorView, ErrorOutOfHoursView, ErrorView, PageNotFoundErrorView}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ErrorHandler @Inject() (
  val env: Environment,
  val messagesApi: MessagesApi,
  val auditConnector: AuditConnector,
  @Named("appName") val appName: String,
  govUkWrapper: GovukLayoutWrapper,
  html: uk.gov.hmrc.traderservices.views.components.html,
  pageNotFoundErrorView: PageNotFoundErrorView,
  errorView: ErrorView,
  errorOutOfHoursView: ErrorOutOfHoursView
)(implicit val config: Configuration, ec: ExecutionContext, appConfig: uk.gov.hmrc.traderservices.wiring.AppConfig)
    extends FrontendErrorHandler with AuthRedirects with ErrorAuditing {
  private val logger = Logger(getClass)

  private val isDevEnv =
    if (env.mode.equals(Mode.Test)) false
    else config.get[String]("run.mode").forall(Mode.Dev.toString.equals)

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    auditClientError(request, statusCode, message)
    super.onClientError(request, statusCode, message)
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] =
    exception match {
      case TraderServicesAmendApiError(ex) =>
        logger.warn(
          """
            |
            |! %sInternal server error, for (%s) [%s] ->
            | """.stripMargin.format(
            ex match {
              case p: PlayException => "@" + p.id + " - "
              case _                => ""
            },
            request.method,
            request.uri
          ),
          ex
        )
        Future.successful(resolveError(request, exception))
      case _ => super.onServerError(request, exception)
    }

  override def resolveError(request: RequestHeader, exception: Throwable): Result = {
    auditServerError(request, exception)
    implicit val r: Request[String] = Request(request, "")
    exception match {
      case _: NoActiveSession => toGGLogin(if (isDevEnv) s"http://${request.host}${request.uri}" else s"${request.uri}")
      case _: InsufficientEnrolments => Forbidden
//      case _: TraderServicesAmendApiError => Ok(externalAmendErrorTemplate())
      case _ =>
        Ok(
          if (
            DateTimeHelper.isWorkingHours(
              DateTimeHelper.londonTime,
              appConfig.workingHourStart,
              appConfig.workingHourEnd
            )
          )
            errorView()
          else
            errorOutOfHoursView()
        )
    }
  }

  override def standardErrorTemplate(pageTitle: String, heading: String, message: String)(implicit
    request: Request[_]
  ) =
    new ErrorTemplate(govUkWrapper, html)(pageTitle, heading, message)

  override def notFoundTemplate(implicit request: Request[_]): HtmlFormat.Appendable = pageNotFoundErrorView()

//  def externalAmendErrorTemplate()(implicit request: Request[_]): HtmlFormat.Appendable = amendCaseErrorView()
}

object EventTypes {

  val RequestReceived: String = "RequestReceived"
  val TransactionFailureReason: String = "transactionFailureReason"
  val ServerInternalError: String = "ServerInternalError"
  val ResourceNotFound: String = "ResourceNotFound"
  val ServerValidationError: String = "ServerValidationError"
}

trait ErrorAuditing extends HttpAuditEvent {

  import EventTypes._

  def auditConnector: AuditConnector

  private val unexpectedError = "Unexpected error"
  private val notFoundError = "Resource Endpoint Not Found"
  private val badRequestError = "Request bad format exception"

  def auditServerError(request: RequestHeader, ex: Throwable)(implicit ec: ExecutionContext): Unit = {
    val eventType = ex match {
      case _: NotFoundException     => ResourceNotFound
      case _: JsValidationException => ServerValidationError
      case _                        => ServerInternalError
    }
    val transactionName = ex match {
      case _: NotFoundException => notFoundError
      case _                    => unexpectedError
    }
    auditConnector.sendEvent(
      dataEvent(eventType, transactionName, request, Map(TransactionFailureReason -> ex.getMessage))(
        HeaderCarrierConverter.fromRequestAndSession(request, request.session)
      )
    )
  }

  def auditClientError(request: RequestHeader, statusCode: Int, message: String)(implicit
    ec: ExecutionContext
  ): Unit = {
    import play.api.http.Status._
    statusCode match {
      case NOT_FOUND =>
        auditConnector.sendEvent(
          dataEvent(ResourceNotFound, notFoundError, request, Map(TransactionFailureReason -> message))(
            HeaderCarrierConverter.fromRequestAndSession(request, request.session)
          )
        )
      case BAD_REQUEST =>
        auditConnector.sendEvent(
          dataEvent(ServerValidationError, badRequestError, request, Map(TransactionFailureReason -> message))(
            HeaderCarrierConverter.fromRequestAndSession(request, request.session)
          )
        )
      case _ =>
    }
  }
}
