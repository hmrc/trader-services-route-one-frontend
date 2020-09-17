package uk.gov.hmrc.traderservices.stubs

import java.time.LocalDate

import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.traderservices.models._

trait JourneyTestData {

  val correlationId: String = scala.util.Random.alphanumeric.take(64).mkString

  val validModel =
    TraderServicesModel(Nino("RJ301829A"), "Doe", "Jane", "2001-01-31")

}
