/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.libs.json.{Format, Json}
import java.time.LocalDateTime

/** An expected case response time.
  *
  * Route = Hold - do not show an SLA Route = anything other than Hold - show SLA
  *
  * Air transportation exports = 2 hours RORO transportation exports = 2 hours Maritime exports = 2 hours
  *
  * Air transportation imports = 2 hours RORO transportation imports = 2 hours Maritime transportation imports (received
  * before 15:00) = 3 hours Maritime transportation imports (received after 15:00) = 08:00 the next day
  */
case class CaseSLA(dateTime: Option[LocalDateTime])

object CaseSLA {

  def calculateFrom(sumbissionDateTime: LocalDateTime, questionsAnswers: QuestionsAnswers): CaseSLA = {
    val slaDateTime: Option[LocalDateTime] = questionsAnswers match {
      case answers: ExportQuestions =>
        answers.routeType.flatMap {
          case ExportRouteType.Hold => None
          case _ =>
            answers.freightType.map {
              case ExportFreightType.Air      => sumbissionDateTime.plusHours(2)
              case ExportFreightType.RORO     => sumbissionDateTime.plusHours(2)
              case ExportFreightType.Maritime => sumbissionDateTime.plusHours(2)
            }
        }
      case answers: ImportQuestions =>
        answers.routeType.flatMap {
          case ImportRouteType.Hold => None
          case _ =>
            answers.freightType.map {
              case ImportFreightType.Air  => sumbissionDateTime.plusHours(2)
              case ImportFreightType.RORO => sumbissionDateTime.plusHours(2)
              case ImportFreightType.Maritime =>
                if (sumbissionDateTime.isAfter(withHour(15, sumbissionDateTime)))
                  withHour(8, sumbissionDateTime).plusDays(1)
                else if (sumbissionDateTime.isBefore(withHour(5, sumbissionDateTime)))
                  withHour(8, sumbissionDateTime)
                else
                  sumbissionDateTime.plusHours(3)
            }
        }
    }
    CaseSLA(slaDateTime)
  }

  private def withHour(hour: Int, datetime: LocalDateTime): LocalDateTime =
    datetime
      .withHour(hour)
      .withMinute(0)
      .withSecond(0)
      .withNano(0)

  implicit val formats: Format[CaseSLA] = Json.format[CaseSLA]
}
