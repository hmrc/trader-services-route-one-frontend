/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.traderservices.support

import org.apache.pekko.stream.Materializer
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{RequestHeader, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.traderservices.stubs.{AuthStubs, DataStreamStubs}
import uk.gov.hmrc.traderservices.support
import uk.gov.hmrc.traderservices.wiring.AppConfig
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.language.{implicitConversions, postfixOps}

abstract class BaseISpec
    extends support.UnitSpec with WireMockSupport with AuthStubs with DataStreamStubs with MetricsTestSupport {

  import scala.concurrent.duration._
  override implicit val defaultTimeout: FiniteDuration = 5 seconds

  def uploadMultipleFilesFeature: Boolean
  def requireEnrolmentFeature: Boolean
  def requireOptionalTransportFeature: Boolean

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.enabled"                      -> true,
        "auditing.enabled"                     -> true,
        "auditing.consumer.baseUri.host"       -> wireMockHost,
        "auditing.consumer.baseUri.port"       -> wireMockPort,
        "play.filters.csrf.method.whiteList.0" -> "POST",
        "play.filters.csrf.method.whiteList.1" -> "GET",
        "play.filters.csrf.method.whiteList.2" -> "OPTIONS",
        "features.workingHours.start"          -> 0,
        "features.workingHours.end"            -> 24
      )
      .overrides(
        bind[AppConfig]
          .toInstance(
            TestAppConfig(
              wireMockBaseUrlAsString,
              wireMockPort,
              uploadMultipleFilesFeature,
              requireEnrolmentFeature,
              requireOptionalTransportFeature
            )
          )
      )

  override def commonStubs(): Unit = {
    givenCleanMetricRegistry()
    givenAuditConnector()
  }

  final implicit val materializer: Materializer = app.materializer

  final def checkHtmlResultWithBodyText(result: Result, expectedSubstring: String): Unit = {
    status(result) shouldBe 200
    contentType(result) shouldBe Some("text/html")
    charset(result) shouldBe Some("utf-8")
    bodyOf(result) should include(expectedSubstring)
  }

  private lazy val messagesApi = app.injector.instanceOf[MessagesApi]
  private implicit val messages: Messages = messagesApi.preferred(Seq.empty[Lang])

  final def htmlEscapedMessage(key: String, args: String*): String = HtmlFormat.escape(Messages(key, args: _*)).toString
  final def htmlEscapedPageTitle(key: String, args: String*): String =
    htmlEscapedMessage(key, args: _*) + " - " + htmlEscapedMessage("site.serviceName") + " - " + htmlEscapedMessage(
      "site.govuk"
    )
  final def htmlEscapedPageTitleWithError(key: String, args: String*): String =
    htmlEscapedMessage("error.browser.title.prefix", args: _*) + " " + htmlEscapedPageTitle(key)

  implicit def hc(implicit request: RequestHeader = FakeRequest()): HeaderCarrier =
    HeaderCarrierConverter.fromRequestAndSession(request, request.session)

}
