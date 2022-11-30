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

package uk.gov.hmrc.traderservices.connectors

import akka.actor.ActorSystem
import akka.pattern.after
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.play.http.logging.Mdc
import play.api.Logger
import scala.util.Try
import scala.util.Success
import scala.util.Failure

trait Retries {

  protected def actorSystem: ActorSystem

  def retry[A](intervals: FiniteDuration*)(shouldRetry: Try[A] => Boolean, retryReason: A => String)(
    block: => Future[A]
  )(implicit ec: ExecutionContext): Future[A] = {
    def loop(remainingIntervals: Seq[FiniteDuration])(mdcData: Map[String, String])(block: => Future[A]): Future[A] =
      // scheduling will loose MDC data. Here we explicitly ensure it is available on block.
      Mdc
        .withMdc(block, mdcData)
        .flatMap(result =>
          if (remainingIntervals.nonEmpty && shouldRetry(Success(result))) {
            val delay = remainingIntervals.head
            Logger(getClass).warn(s"Retrying in $delay due to ${retryReason(result)}")
            after(delay, actorSystem.scheduler)(loop(remainingIntervals.tail)(mdcData)(block))
          } else
            Future.successful(result)
        )
        .recoverWith { case e: Throwable =>
          if (remainingIntervals.nonEmpty && shouldRetry(Failure(e))) {
            val delay = remainingIntervals.head
            Logger(getClass).warn(s"Retrying in $delay due to ${e.getClass.getName()}: ${e.getMessage()}")
            after(delay, actorSystem.scheduler)(loop(remainingIntervals.tail)(mdcData)(block))
          } else
            Future.failed(e)
        }
    loop(intervals)(Mdc.mdcData)(block)
  }

}
