/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.traderservices.models

import java.time.LocalDateTime

import uk.gov.hmrc.traderservices.support.UnitSpec

class CaseSLASpec extends UnitSpec {

  "CaseSLA" when {
    for (
      sumbissionDateTime <- Set(
                              LocalDateTime.of(2019, 1, 1, 10, 11, 12),
                              LocalDateTime.of(2020, 6, 30, 12, 0, 0),
                              LocalDateTime.of(2021, 12, 31, 0, 0, 0),
                              LocalDateTime.of(2022, 12, 31, 23, 59, 59)
                            )
    ) {
      val cutoutDateTime = sumbissionDateTime.withHour(15).withMinute(0).withSecond(0)
      s"submission date is $sumbissionDateTime" should {
        for (freightType <- ExportFreightType.values)
          s"calculate no SLA for export case if route type is HOLD, and freightType is $freightType" in {
            CaseSLA.calculateFrom(
              sumbissionDateTime,
              ExportQuestions(
                routeType = Some(ExportRouteType.Hold),
                freightType = Some(freightType)
              )
            ) shouldBe CaseSLA(None)
          }

        for (freightType <- ImportFreightType.values)
          s"calculate no SLA for import case if route type is HOLD, and freightType is $freightType" in {
            CaseSLA.calculateFrom(
              sumbissionDateTime,
              ImportQuestions(
                routeType = Some(ImportRouteType.Hold),
                freightType = Some(freightType)
              )
            ) shouldBe CaseSLA(None)
          }

        for (routeType <- ImportRouteType.values.filterNot(_ == ImportRouteType.Hold))
          s"calculate two hours SLA for import case if route type is $routeType, and freightType is Air" in {
            CaseSLA.calculateFrom(
              sumbissionDateTime,
              ImportQuestions(
                routeType = Some(routeType),
                freightType = Some(ImportFreightType.Air)
              )
            ) shouldBe CaseSLA(Some(sumbissionDateTime.plusHours(2)))
          }

        for (routeType <- ExportRouteType.values.filterNot(_ == ExportRouteType.Hold))
          s"calculate two hours SLA for export case if route type is $routeType, and freightType is Air" in {
            CaseSLA.calculateFrom(
              sumbissionDateTime,
              ExportQuestions(
                routeType = Some(routeType),
                freightType = Some(ExportFreightType.Air)
              )
            ) shouldBe CaseSLA(Some(sumbissionDateTime.plusHours(2)))
          }

        for (routeType <- ImportRouteType.values.filterNot(_ == ImportRouteType.Hold))
          s"calculate two hours SLA for import case if route type is $routeType, and freightType is RORO" in {
            CaseSLA.calculateFrom(
              sumbissionDateTime,
              ImportQuestions(
                routeType = Some(routeType),
                freightType = Some(ImportFreightType.RORO)
              )
            ) shouldBe CaseSLA(Some(sumbissionDateTime.plusHours(2)))
          }

        for (routeType <- ExportRouteType.values.filterNot(_ == ExportRouteType.Hold))
          s"calculate two hours SLA for export case if route type is $routeType, and freightType is RORO" in {
            CaseSLA.calculateFrom(
              sumbissionDateTime,
              ExportQuestions(
                routeType = Some(routeType),
                freightType = Some(ExportFreightType.RORO)
              )
            ) shouldBe CaseSLA(Some(sumbissionDateTime.plusHours(2)))
          }

        for (routeType <- ImportRouteType.values.filterNot(_ == ImportRouteType.Hold))
          s"calculate three hours SLA for import case if route type is $routeType, and freightType is Maritime" in {
            val morningDateTime =
              if (sumbissionDateTime.isBefore(cutoutDateTime)) sumbissionDateTime
              else sumbissionDateTime.withHour(14).withMinute(59).withSecond(59)
            CaseSLA.calculateFrom(
              morningDateTime,
              ImportQuestions(
                routeType = Some(routeType),
                freightType = Some(ImportFreightType.Maritime)
              )
            ) shouldBe CaseSLA(Some(morningDateTime.plusHours(3)))
          }

        for (routeType <- ImportRouteType.values.filterNot(_ == ImportRouteType.Hold))
          s"calculate SLA next morning at 08:00 for import case if route type is $routeType, and freightType is Maritime" in {
            val afternoonDateTime =
              if (sumbissionDateTime.isAfter(cutoutDateTime)) sumbissionDateTime
              else sumbissionDateTime.withHour(15).withMinute(0).withSecond(1)
            CaseSLA.calculateFrom(
              afternoonDateTime,
              ImportQuestions(
                routeType = Some(routeType),
                freightType = Some(ImportFreightType.Maritime)
              )
            ) shouldBe CaseSLA(Some(afternoonDateTime.plusDays(1).withHour(8).withMinute(0).withSecond(0)))
          }

        for (routeType <- ExportRouteType.values.filterNot(_ == ExportRouteType.Hold))
          s"calculate two hours SLA for export case if route type is $routeType, and freightType is Maritime" in {
            CaseSLA.calculateFrom(
              sumbissionDateTime,
              ExportQuestions(
                routeType = Some(routeType),
                freightType = Some(ExportFreightType.Maritime)
              )
            ) shouldBe CaseSLA(Some(sumbissionDateTime.plusHours(2)))
          }
      }
    }
  }

}
