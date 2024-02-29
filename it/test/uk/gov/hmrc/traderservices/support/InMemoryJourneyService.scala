package test.uk.gov.hmrc.traderservices.support

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
