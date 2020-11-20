package uk.gov.hmrc.traderservices.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.json.Json
import uk.gov.hmrc.traderservices.services.TraderServicesFrontendEvent.TraderServicesFrontendEvent
import uk.gov.hmrc.traderservices.support.WireMockSupport

trait DataStreamStubs extends Eventually {
  me: WireMockSupport =>

  override implicit val patienceConfig =
    PatienceConfig(scaled(Span(5, Seconds)), scaled(Span(500, Millis)))

  def verifyAuditRequestSent(
    count: Int,
    event: TraderServicesFrontendEvent,
    tags: Map[String, String] = Map.empty,
    detail: Map[String, String] = Map.empty
  ): Unit =
    eventually {
      verify(
        count,
        postRequestedFor(urlPathEqualTo(auditUrl))
          .withRequestBody(similarToJson(s"""{
          |  "auditSource": "trader-services-frontend",
          |  "auditType": "$event",
          |  "tags": ${Json.toJson(tags)},
          |  "detail": ${Json.toJson(detail)}
          |}"""))
      )
    }

  def verifyAuditRequestNotSent(event: TraderServicesFrontendEvent): Unit =
    eventually {
      verify(
        0,
        postRequestedFor(urlPathEqualTo(auditUrl))
          .withRequestBody(similarToJson(s"""{
          |  "auditSource": "trader-services-frontend",
          |  "auditType": "$event"
          |}"""))
      )
    }

  def givenAuditConnector(): Unit = {
    stubFor(post(urlPathEqualTo(auditUrl)).willReturn(aResponse().withStatus(204)))
    stubFor(post(urlPathEqualTo(auditUrl + "/merged")).willReturn(aResponse().withStatus(204)))
  }

  def givenTimedOut(): Unit =
    stubFor(
      get("/?continue=http%3A%2F%2Flocalhost%3A9379%2Ftrader-services%2Ftimedout")
        .withQueryParam("continue", matching("http://localhost:9379/trader-services/timedout"))
        .willReturn(aResponse().withStatus(200))
    )

  def givenSignOut(): Unit =
    stubFor(
      get(s"/?continue=http%3A%2F%2F$wireMockHost%3A$wireMockPort")
        .withQueryParam("continue", matching(wireMockBaseUrlAsString))
        .willReturn(aResponse().withStatus(200))
    )

  private def auditUrl = "/write/audit"

  private def similarToJson(value: String) = equalToJson(value.stripMargin, true, true)

}
