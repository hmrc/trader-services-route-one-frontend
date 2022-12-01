package uk.gov.hmrc.traderservices.support

import java.util.concurrent.atomic.AtomicReference

import uk.gov.hmrc.play.fsm.PersistentJourneyService

import scala.concurrent.{ExecutionContext, Future}

/** Basic in-memory implementation of the journey service, facilitates integration testing without MongoDB.
  */
trait InMemoryJourneyService[RequestContext] extends PersistentJourneyService[RequestContext] {

  private val state = new AtomicReference[Option[StateAndBreadcrumbs]](None)

  override protected def fetch(implicit
    requestContext: RequestContext,
    ec: ExecutionContext
  ): Future[Option[(model.State, List[model.State])]] =
    Future.successful(
      state.get
    )

  override protected def save(
    s: (model.State, List[model.State])
  )(implicit requestContext: RequestContext, ec: ExecutionContext): Future[(model.State, List[model.State])] =
    Future {
      state.set(Some(s))
      s
    }

  override def clear(implicit requestContext: RequestContext, ec: ExecutionContext): Future[Unit] =
    Future {
      state.set(None)
    }
}
