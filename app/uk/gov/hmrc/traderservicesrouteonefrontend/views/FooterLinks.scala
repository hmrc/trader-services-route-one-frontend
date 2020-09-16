package uk.gov.hmrc.traderservicesrouteonefrontend.views

import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.viewmodels.footer.FooterItem
import uk.gov.hmrc.traderservicesrouteonefrontend.config.AppConfig

object FooterLinks {
  def apply()(implicit messages: Messages, appConfig: AppConfig): Seq[FooterItem] = appConfig.footerLinkItems.flatMap { item =>
    val keyPrefix = s"footer.$item"
    val textKey = s"$keyPrefix.text"
    val urlKey = s"$keyPrefix.url"
    if (
      messages.isDefinedAt(textKey) && messages.isDefinedAt(urlKey)
    ) Some(FooterItem(
      text = Some(messages(textKey)),
      href = Some(messages(urlKey))
    )) else None
  }
}
