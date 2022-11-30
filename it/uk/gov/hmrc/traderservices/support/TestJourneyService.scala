package uk.gov.hmrc.traderservices.support

import uk.gov.hmrc.play.fsm.PersistentJourneyService

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

/** Intended to be mixed-in when binding a real service for integration testing, exposes protected service's methods to
  * the test.
  */
trait TestJourneyService[RequestContext] extends PersistentJourneyService[RequestContext] {

  def set(state: model.State, breadcrumbs: List[model.State])(implicit
    requestContext: RequestContext,
    timeout: Duration,
    ec: ExecutionContext
  ): Unit = Await.result(save((state, breadcrumbs)), timeout)

  def setState(
    state: model.State
  )(implicit requestContext: RequestContext, timeout: Duration, ec: ExecutionContext): Unit =
    Await.result(save((state, Nil)), timeout)

  def get(implicit
    requestContext: RequestContext,
    timeout: Duration,
    ec: ExecutionContext
  ): Option[StateAndBreadcrumbs] = Await.result(fetch, timeout)

  def getState(implicit requestContext: RequestContext, timeout: Duration, ec: ExecutionContext): model.State =
    get.get._1

  def getBreadcrumbs(implicit
    requestContext: RequestContext,
    timeout: Duration,
    ec: ExecutionContext
  ): List[model.State] = get.get._2

  protected def fetch(implicit
    requestContext: RequestContext,
    ec: ExecutionContext
  ): Future[Option[(model.State, List[model.State])]]

  protected def save(
    s: (model.State, List[model.State])
  )(implicit requestContext: RequestContext, ec: ExecutionContext): Future[(model.State, List[model.State])]
}
