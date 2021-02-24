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

package uk.gov.hmrc.traderservices.controllers

import javax.inject.{Inject, Singleton}
import play.api.libs.ws.WSClient
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import play.api.mvc.Call
import play.api.mvc.RequestHeader

@Singleton
class PrintStylesheet @Inject() (wsClient: WSClient) {

  private val location: Call = controllers.routes.Assets
    .versioned("stylesheets/download-receipt.css")

  final def content(implicit request: RequestHeader, ec: ExecutionContext): Future[String] =
    wsClient
      .url(location.absoluteURL)
      .get()
      .map(_.body)

}
