/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.traderservices.connectors

import com.codahale.metrics.MetricRegistry
import uk.gov.hmrc.traderservices.support.UnitSpec
import uk.gov.hmrc.traderservices.connectors.HttpErrorRateMeter

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
