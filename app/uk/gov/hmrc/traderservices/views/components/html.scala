/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.traderservices.views.components

import javax.inject.{Inject, Singleton}

@Singleton
class html @Inject() (
  val h1: uk.gov.hmrc.traderservices.views.html.components.h1,
  val h2: uk.gov.hmrc.traderservices.views.html.components.h2,
  val h3: uk.gov.hmrc.traderservices.views.html.components.h3,
  val p: uk.gov.hmrc.traderservices.views.html.components.p,
  val strong: uk.gov.hmrc.traderservices.views.html.components.strong,
  val a: uk.gov.hmrc.traderservices.views.html.components.link,
  val ul: uk.gov.hmrc.traderservices.views.html.components.bullets,
  val ol: uk.gov.hmrc.traderservices.views.html.components.orderedList,
  val button: uk.gov.hmrc.traderservices.views.html.components.button,
  val summaryList: uk.gov.hmrc.traderservices.views.html.components.summaryList
)
