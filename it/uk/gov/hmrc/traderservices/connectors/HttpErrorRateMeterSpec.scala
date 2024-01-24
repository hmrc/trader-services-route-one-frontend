package uk.gov.hmrc.traderservices.connectors

import com.codahale.metrics.MetricRegistry
import uk.gov.hmrc.traderservices.support.UnitSpec

class HttpErrorRateMeterSpec extends UnitSpec {

  class TestHttpErrorRateMeter extends HttpErrorRateMeter {
    override val metricRegistry: MetricRegistry = new MetricRegistry()
  }

  val testHttpErrorRateMeter = new TestHttpErrorRateMeter()

  "meterName" should {

    "return Http4xxErrorCount-serviceName for status code 403" in {
      val serviceName = "ConsumedAPI-trader-services-create-case-api-POST"
      val statusCode = 403

      testHttpErrorRateMeter.meterName(serviceName, statusCode) shouldBe s"Http4xxErrorCount-$serviceName"
    }

    "return Http5xxErrorCount-serviceName for status code 500" in {
      val serviceName = "ConsumedAPI-trader-services-create-case-api-POST"
      val statusCode = 500

      testHttpErrorRateMeter.meterName(serviceName, statusCode) shouldBe s"Http5xxErrorCount-$serviceName"
    }
  }
}
