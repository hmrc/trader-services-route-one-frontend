package uk.gov.hmrc.traderservices.support

import akka.stream.Materializer
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.traderservices.stubs.{AuthStubs, DataStreamStubs}
import uk.gov.hmrc.traderservices.wiring.AppConfig
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

abstract class BaseISpec
    extends UnitSpec with WireMockSupport with AuthStubs with DataStreamStubs with MetricsTestSupport {

  import scala.concurrent.duration._
  override implicit val defaultTimeout: FiniteDuration = 5 seconds

  val uploadMultipleFilesFeature: Boolean = false

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.enabled"                      -> true,
        "auditing.enabled"                     -> true,
        "auditing.consumer.baseUri.host"       -> wireMockHost,
        "auditing.consumer.baseUri.port"       -> wireMockPort,
        "play.filters.csrf.method.whiteList.0" -> "POST",
        "play.filters.csrf.method.whiteList.1" -> "GET"
      )
      .overrides(
        bind[AppConfig]
          .toInstance(
            TestAppConfig(wireMockBaseUrlAsString, wireMockPort, uploadMultipleFilesFeature)
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

  implicit def hc(implicit request: FakeRequest[_]): HeaderCarrier =
    HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

}
