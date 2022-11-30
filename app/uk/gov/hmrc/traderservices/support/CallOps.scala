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

package uk.gov.hmrc.traderservices.support

import java.net.URI

import play.api.{Configuration, Environment, Mode}

object CallOps {

  /** Creates a URL string with localhost and port if running locally, for relative URLs Absolute URLs are unaffected
    * Just passes through the URL as normal if running in a non-local environment
    */
  def localFriendlyUrl(env: Environment, config: Configuration)(url: String, hostAndPort: String) = {
    val isLocalEnv =
      if (env.mode.equals(Mode.Test)) false
      else config.getOptional[String]("run.mode").contains(Mode.Dev.toString)

    val uri = new URI(url)

    if (!uri.isAbsolute && isLocalEnv) s"http://$hostAndPort$url"
    else url
  }
}
