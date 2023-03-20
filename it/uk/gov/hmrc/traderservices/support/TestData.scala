//package uk.gov.hmrc.traderservices.support
//
//import uk.gov.hmrc.traderservices.models._
//import java.time.LocalDateTime
//import java.time.LocalDate
//import java.time.ZonedDateTime
//
//object TestData {
//
//  val today = LocalDate.now
//
//  val exportEntryDetails = EntryDetails(EPU(123), EntryNumber("Z00000Z"), today)
//  val importEntryDetails = EntryDetails(EPU(123), EntryNumber("000000Z"), today)
//  val invalidEntryDetails = EntryDetails(EPU(123), EntryNumber("0000000"), today)
//
//  def fullExportQuestions(dateTimeOfArrival: LocalDateTime) =
//    ExportQuestions(
//      requestType = Some(ExportRequestType.New),
//      routeType = Some(ExportRouteType.Route2),
//      hasPriorityGoods = Some(true),
//      priorityGoods = Some(ExportPriorityGoods.ExplosivesOrFireworks),
//      freightType = Some(ExportFreightType.Air),
//      vesselDetails = Some(
//        VesselDetails(
//          vesselName = Some("Foo"),
//          dateOfArrival = Some(dateTimeOfArrival.toLocalDate()),
//          timeOfArrival = Some(dateTimeOfArrival.toLocalTime())
//        )
//      ),
//      contactInfo = Some(ExportContactInfo(contactEmail = "name@somewhere.com"))
//    )
//
//  def fullImportQuestions(dateTimeOfArrival: LocalDateTime) =
//    ImportQuestions(
//      requestType = Some(ImportRequestType.New),
//      routeType = Some(ImportRouteType.Route2),
//      hasPriorityGoods = Some(true),
//      priorityGoods = Some(ImportPriorityGoods.ExplosivesOrFireworks),
//      hasALVS = Some(true),
//      freightType = Some(ImportFreightType.Air),
//      contactInfo = Some(ImportContactInfo(contactEmail = "name@somewhere.com")),
//      vesselDetails = Some(
//        VesselDetails(
//          vesselName = Some("Foo"),
//          dateOfArrival = Some(dateTimeOfArrival.toLocalDate()),
//          timeOfArrival = Some(dateTimeOfArrival.toLocalTime())
//        )
//      )
//    )
//
//  final val acceptedFileUpload =
//    FileUpload.Accepted(
//      Nonce(1),
//      Timestamp.Any,
//      "foo-bar-ref-1",
//      "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
//      ZonedDateTime.parse("2018-04-24T09:30:00Z"),
//      "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
//      "test.pdf",
//      "application/pdf",
//      Some(4567890)
//    )
//
//}
