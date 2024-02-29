package test.uk.gov.hmrc.traderservices.support

import org.scalatestplus.play.guice.GuiceOneAppPerSuite

abstract class AppISpec extends BaseISpec with GuiceOneAppPerSuite {

  override def uploadMultipleFilesFeature: Boolean = false
  override def requireEnrolmentFeature: Boolean = false
  override def requireOptionalTransportFeature: Boolean = false

}
