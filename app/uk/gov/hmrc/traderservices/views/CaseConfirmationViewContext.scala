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

package uk.gov.hmrc.traderservices.views

import javax.inject.Singleton
import play.api.i18n.Messages
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import java.time.ZoneId
import uk.gov.hmrc.traderservices.models.EntryDetails
import uk.gov.hmrc.traderservices.models.UploadedFile
import uk.gov.hmrc.traderservices.models.QuestionsAnswers
import uk.gov.hmrc.traderservices.models.ExportQuestions
import uk.gov.hmrc.traderservices.models.ExportRequestType
import uk.gov.hmrc.traderservices.models.ImportQuestions
import uk.gov.hmrc.traderservices.models.ImportRequestType

@Singleton
class CaseConfirmationViewContext extends EntryDetailsHelper {

  final val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

  def getSlaTimeTextFor(dateTime: LocalDateTime)(implicit messages: Messages): String = {
    val time = timeFormat.withLocale(messages.lang.locale).format(dateTime.toLocalTime())
    val today = ZonedDateTime.now(ZoneId.of("Europe/London")).toLocalDate()
    val suffix =
      messages(if (dateTime.toLocalDate.isAfter(today)) "site.tomorrow" else "site.today")
    s"""<span id="sla-time">$time</span> <span id="sla-suffix">$suffix</span>"""
  }

  def entryDateForDisplay(entryDetails: EntryDetails)(implicit messages: Messages): String =
    formatDateForDisplay(entryDetails.entryDate)

  def fileNamesForDisplay(uploadedFiles: Seq[UploadedFile]): Seq[String] =
    uploadedFiles.map(_.fileName)

  def isCancellation(questionsAnswers: QuestionsAnswers): Boolean =
    questionsAnswers match {
      case q: ExportQuestions => q.requestType.contains(ExportRequestType.Cancellation)
      case q: ImportQuestions => q.requestType.contains(ImportRequestType.Cancellation)
    }

  def isWithdrawal(questionsAnswers: QuestionsAnswers): Boolean =
    questionsAnswers match {
      case q: ExportQuestions => q.requestType.contains(ExportRequestType.WithdrawalOrReturn)
      case q: ImportQuestions => false
    }

}
