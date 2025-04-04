/*
 * Copyright 2025 HM Revenue & Customs
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

import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

/** Basic in-memory store used to test journeys.
  */
trait InMemoryStore[A] {

  private val state: AtomicReference[Option[A]] = new AtomicReference(None)

  def fetch: Option[A] =
    state.get()

  def save(newState: A): A =
    state
      .updateAndGet(new UnaryOperator[Option[A]] {
        override def apply(t: Option[A]): Option[A] = Some(newState)
      })
      .get

  def clear(): Unit =
    state.set(None)

}
