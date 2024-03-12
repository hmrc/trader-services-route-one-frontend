/*
 * Copyright 2024 HM Revenue & Customs
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

import uk.gov.hmrc.traderservices.journeys.State
import uk.gov.hmrc.traderservices.services.SessionStateService

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future}

/** Basic in-memory implementation of the journey service, facilitates integration testing without MongoDB.
  */
trait InMemoryJourneyService[RequestContext] extends SessionStateService {

  private val state = new AtomicReference[Option[StateAndBreadcrumbs]](None)

  protected def fetch(implicit
    requestContext: RequestContext,
    ec: ExecutionContext
  ): Future[Option[(State, List[State])]] =
    Future.successful(
      state.get
    )

  protected def save(
    s: (State, List[State])
  )(implicit requestContext: RequestContext, ec: ExecutionContext): Future[(State, List[State])] =
    Future {
      state.set(Some(s))
      s
    }

  def clear(implicit requestContext: RequestContext, ec: ExecutionContext): Future[Unit] =
    Future {
      state.set(None)
    }
}
