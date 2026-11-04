/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.traderservices.views

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.BeforeAndAfterAll
import play.api.Application
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.twirl.api.Html
import uk.gov.hmrc.traderservices.support.UnitSpec
import uk.gov.hmrc.traderservices.views.html.templates.{GovukLayoutWrapper, GovukLayoutWrapper2}

class UserResearchBannerSpec extends UnitSpec with BeforeAndAfterAll {

  private val bannerUrlEn = "https://banner-en"
  private val bannerUrlCy = "https://banner-cy"
  private val applications = scala.collection.mutable.ArrayBuffer.empty[Application]

  private def appWith(bannerEnabled: Boolean): Application = {
    val application = GuiceApplicationBuilder()
      .configure(
        "metrics.enabled"               -> false,
        "features.user-research-banner" -> bannerEnabled,
        "urls.user-research-banner-en"  -> bannerUrlEn,
        "urls.user-research-banner-cy"  -> bannerUrlCy
      )
      .build()
    applications += application
    application
  }

  private lazy val enabledApp = appWith(bannerEnabled = true)
  private lazy val disabledApp = appWith(bannerEnabled = false)

  private def messagesFor(application: Application, lang: String): Messages =
    application.injector.instanceOf[MessagesApi].preferred(Seq(Lang(lang)))

  private def wrapper1(application: Application, displayBanner: Boolean, messages: Messages): Html =
    application.injector
      .instanceOf[GovukLayoutWrapper]
      .apply(displayUserResearchBanner = displayBanner)(Html("<p>Test content</p>"))(FakeRequest(), messages)

  private def wrapper2(application: Application, displayBanner: Boolean, messages: Messages): Html =
    application.injector
      .instanceOf[GovukLayoutWrapper2]
      .apply(displayUserResearchBanner = displayBanner)(Html("<p>Test content</p>"))(FakeRequest(), messages)

  private def banners(view: Html): org.jsoup.select.Elements = parse(view).select(".hmrc-user-research-banner")

  private def parse(view: Html): Document = Jsoup.parse(view.body)

  override protected def afterAll(): Unit = {
    applications.foreach(_.stop())
    super.afterAll()
  }

  "The user research banner" should {

    "be shown once in the header with the English link when enabled in either layout" in {
      val messages = messagesFor(enabledApp, "en")
      Seq(
        "GovukLayoutWrapper"  -> wrapper1(enabledApp, displayBanner = true, messages),
        "GovukLayoutWrapper2" -> wrapper2(enabledApp, displayBanner = true, messages)
      ).foreach { case (name, view) =>
        withClue(s"$name: ") {
          val banner = banners(view)
          banner.size shouldBe 1
          banner.first.parent.tagName shouldBe "header"
          banner.select("a.hmrc-user-research-banner__link").attr("href") shouldBe bannerUrlEn
          banner.select("a.hmrc-user-research-banner__link").text shouldBe "Join our research panel (opens in new tab)"
        }
      }
    }

    "use the Welsh link and copy when the language is Welsh" in {
      val messages = messagesFor(enabledApp, "cy")
      Seq(
        "GovukLayoutWrapper"  -> wrapper1(enabledApp, displayBanner = true, messages),
        "GovukLayoutWrapper2" -> wrapper2(enabledApp, displayBanner = true, messages)
      ).foreach { case (name, view) =>
        withClue(s"$name: ") {
          val link = banners(view).select("a.hmrc-user-research-banner__link")
          link.attr("href") shouldBe bannerUrlCy
          link.text shouldBe "Ymunwch â’n panel ymchwil (yn agor tab newydd)"
        }
      }
    }

    "not be shown when disabled globally or opted out by a page" in {
      val englishMessages = messagesFor(enabledApp, "en")
      Seq(
        "GovukLayoutWrapper"  -> wrapper1(enabledApp, displayBanner = false, englishMessages),
        "GovukLayoutWrapper2" -> wrapper2(enabledApp, displayBanner = false, englishMessages),
        "feature disabled"    -> wrapper2(disabledApp, displayBanner = true, messagesFor(disabledApp, "en"))
      ).foreach { case (name, view) =>
        withClue(s"$name: ") {
          banners(view).size shouldBe 0
        }
      }
    }
  }
}
