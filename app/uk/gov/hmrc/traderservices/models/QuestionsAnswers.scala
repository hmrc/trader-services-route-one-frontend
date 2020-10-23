/*
 * Copyright 2020 HM Revenue & Customs
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

import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.libs.json.Writes
import play.api.libs.json.JsValue
import play.api.libs.json.JsObject
import play.api.libs.json.JsError

trait QuestionsAnswers

object QuestionsAnswers {

  implicit def reads: Reads[QuestionsAnswers] =
    Reads {
      case o: JsObject if (o \ ExportQuestions.tag).isDefined =>
        ExportQuestions.formats.reads((o \ ExportQuestions.tag).get)
      case o: JsObject if (o \ ImportQuestions.tag).isDefined =>
        ImportQuestions.formats.reads((o \ ImportQuestions.tag).get)
      case _ => JsError("Invalid format of QuestionsAnswers")
    }

  implicit def writes: Writes[QuestionsAnswers] =
    new Writes[QuestionsAnswers] {
      override def writes(o: QuestionsAnswers): JsValue =
        o match {
          case e: ExportQuestions =>
            ExportQuestions.formats.transform(v => Json.obj(ExportQuestions.tag -> v)).writes(e)
          case i: ImportQuestions =>
            ImportQuestions.formats.transform(v => Json.obj(ImportQuestions.tag -> v)).writes(i)
          case _ => throw new IllegalArgumentException("Unknown QuestionsAnswers type")
        }
    }

}
