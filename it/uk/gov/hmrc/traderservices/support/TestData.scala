package uk.gov.hmrc.traderservices.support

import uk.gov.hmrc.traderservices.models._
import java.time.LocalDateTime
import java.time.LocalDate

object TestData {

  val exportEntryDetails = EntryDetails(EPU(123), EntryNumber("Z00000Z"), LocalDate.parse("2020-09-23"))
  val importEntryDetails = EntryDetails(EPU(123), EntryNumber("000000Z"), LocalDate.parse("2020-09-23"))
  val invalidEntryDetails = EntryDetails(EPU(123), EntryNumber("0000000"), LocalDate.parse("2020-09-23"))

  def fullExportQuestions(dateTimeOfArrival: LocalDateTime) =
    ExportQuestions(
      requestType = Some(ExportRequestType.New),
      routeType = Some(ExportRouteType.Route3),
      hasPriorityGoods = Some(true),
      priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
      freightType = Some(ExportFreightType.Air),
      vesselDetails = Some(
        VesselDetails(
          vesselName = Some("Foo"),
          dateOfArrival = Some(dateTimeOfArrival.toLocalDate()),
          timeOfArrival = Some(dateTimeOfArrival.toLocalTime())
        )
      ),
      contactInfo = Some(ExportContactInfo(contactEmail = "name@somewhere.com"))
    )

  def fullImportQuestions(dateTimeOfArrival: LocalDateTime) =
    ImportQuestions(
      requestType = Some(ImportRequestType.New),
      routeType = Some(ImportRouteType.Route3),
      hasPriorityGoods = Some(true),
      priorityGoods = Some(ImportPriorityGoods.ExplosivesOrFireworks),
      hasALVS = Some(true),
      freightType = Some(ImportFreightType.Air),
      contactInfo = Some(ImportContactInfo(contactEmail = "name@somewhere.com")),
      vesselDetails = Some(
        VesselDetails(
          vesselName = Some("Foo"),
          dateOfArrival = Some(dateTimeOfArrival.toLocalDate()),
          timeOfArrival = Some(dateTimeOfArrival.toLocalTime())
        )
      )
    )

}
