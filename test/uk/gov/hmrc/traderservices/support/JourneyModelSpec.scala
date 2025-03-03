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

import org.scalactic.source
import org.scalatest.matchers.should.Matchers
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.{BeforeAndAfterAll, Informing}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.http.{HeaderCarrier, RequestId}
import uk.gov.hmrc.traderservices.journeys.CreateCaseJourneyModel.Transitions
import uk.gov.hmrc.traderservices.journeys.{JourneyModel, State, Transition}

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, Future}
import scala.io.AnsiColor
import scala.language.{implicitConversions, postfixOps}
import scala.reflect.ClassTag

/** Abstract base of FSM journey specifications.
  *
  * @example
  *
  * given(State_A) .when(transition) .thenGoes(State_B)
  *
  * given(State_A) .when(transition) .thenMatches { case State_B(...) => }
  *
  * given(State_A) .when(transition) .thenNoChange
  *
  * given(State_A) .when(transition) .thenFailsWith[SomeExceptionType]
  */
trait JourneyModelSpec extends TestJourneyService {
  self: Matchers with BeforeAndAfterAll with Informing =>

  val model: JourneyModel

  val maxFileUploadsNumber = 10

  def retreatFromFileUpload: Transition[State] = Transitions.backFromFileUpload

  implicit val defaultTimeout: FiniteDuration = 5 seconds
  import scala.concurrent.ExecutionContext.Implicits.global

  lazy implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("", "")

  implicit val headerCarrier: HeaderCarrier =
    HeaderCarrier().copy(requestId = Some(RequestId("bar")), otherHeaders = Seq("X-Client-Id" -> "foo"))

  def await[A](future: Future[A])(implicit timeout: Duration): A =
    Await.result(future, timeout)

  /** Assumption about the initial state of journey. */
  case class given[S <: State: ClassTag](initialState: S, breadcrumbs: List[State] = Nil) {

    final def withBreadcrumbs(breadcrumbs: State*): given[S] =
      given(initialState, breadcrumbs.toList)

    final def when(transition: Transition[State]): When = {
      Option(initialState) match {
        case Some(state) => set(state, breadcrumbs)
        case None        => clear
      }
      val resultOrException: Either[Throwable, (State, List[State])] = await(
        apply(transition)
          .map(Right.apply)
          .recover { case exception =>
            Left(exception)
          }
      )
      When(initialState, resultOrException)
    }

  }

  /** State transition result. */
  case class When(
    initialState: State,
    result: Either[Throwable, (State, List[State])]
  ) {

    /** Asserts that the resulting state of the transition is equal to some expected state. */
    final def thenGoes(state: State)(implicit pos: source.Position): Unit =
      this should JourneyModelSpec.this.thenGo(state)

    /** Asserts that the resulting state of the transition matches some case. */
    final def thenMatches(statePF: PartialFunction[State, Unit])(implicit pos: source.Position): Unit =
      this should JourneyModelSpec.this.thenMatch(statePF)

    /** Asserts that the transition hasn't change the state. */
    final def thenNoChange(implicit pos: source.Position): Unit =
      this should JourneyModelSpec.this.changeNothing

    /** Asserts that the transition threw some expected exception of type E. */
    final def thenFailsWith[E <: Throwable](implicit ct: ClassTag[E], pos: source.Position): Unit =
      this should JourneyModelSpec.this.failWith[E]

  }

  /** Asserts that the resulting state of the transition is equal to some expected state. */
  final def thenGo(state: State): Matcher[When] =
    new Matcher[When] {
      override def apply(result: When): MatchResult =
        result match {
          case When(_, Left(exception)) =>
            MatchResult(false, s"Transition has been expected but got an exception $exception", s"")

          case When(_, Right((thisState, _))) if state != thisState =>
            if (state != result.initialState && thisState == result.initialState)
              MatchResult(
                false,
                s"New state ${AnsiColor.CYAN}${nameOf(state)}${AnsiColor.RESET} has been expected but the transition didn't happen.",
                s""
              )
            else if (state.getClass() == thisState.getClass()) {
              val diff = Diff(thisState, state)
              MatchResult(
                false,
                s"Obtained state ${AnsiColor.CYAN}${nameOf(state)}${AnsiColor.RESET} content differs from the expected:\n$diff}",
                s""
              )
            } else
              MatchResult(
                false,
                s"State ${AnsiColor.CYAN}${nameOf(state)}${AnsiColor.RESET} has been expected but got state ${AnsiColor.CYAN}${nameOf(thisState)}${AnsiColor.RESET}",
                s""
              )

          case _ =>
            MatchResult(true, "", s"")
        }
    }

  /** Asserts that the resulting state of the transition matches some case. */
  final def thenMatch(
    statePF: PartialFunction[State, Unit]
  ): Matcher[When] =
    new Matcher[When] {
      override def apply(result: When): MatchResult =
        result match {
          case When(_, Left(exception)) =>
            MatchResult(
              false,
              s"Transition has been expected but got an exception: ${AnsiColor.RED}$exception${AnsiColor.RESET}",
              s""
            )

          case When(_, Right((thisState, _))) if !statePF.isDefinedAt(thisState) =>
            MatchResult(false, s"Matching state has been expected but got state $thisState", s"")

          case _ => MatchResult(true, "", s"")
        }
    }

  /** Asserts that the transition hasn't change the state. */
  final val changeNothing: Matcher[When] =
    new Matcher[When] {
      override def apply(result: When): MatchResult =
        result match {
          case When(_, Left(exception)) =>
            MatchResult(
              false,
              s"Transition has been expected but got an exception: ${AnsiColor.RED}$exception${AnsiColor.RESET}",
              s""
            )

          case When(initialState, Right((thisState, _))) if thisState != initialState =>
            MatchResult(false, s"No state change has been expected but got state $thisState", s"")

          case _ =>
            MatchResult(true, "", s"")
        }
    }

  /** Asserts that the transition threw some expected exception of type E. */
  final def failWith[E <: Throwable](implicit ct: ClassTag[E]): Matcher[When] =
    new Matcher[When] {
      private val expectedClass = ct.runtimeClass
      override def apply(result: When): MatchResult =
        result match {
          case When(_, Left(exception)) if !expectedClass.isAssignableFrom(exception.getClass) =>
            MatchResult(
              false,
              s"Exception of type ${AnsiColor.RED}${expectedClass
                  .getName()}${AnsiColor.RESET} has been expected but got exception of type ${AnsiColor.RED}${exception
                  .getClass()
                  .getName()}${AnsiColor.RESET}",
              s""
            )

          case When(initialState, Right((thisState, _))) =>
            MatchResult(
              false,
              s"Exception of type ${AnsiColor.RED}${expectedClass.getName()}${AnsiColor.RESET} has been expected but got state $thisState",
              s""
            )

          case _ =>
            MatchResult(true, "", s"")
        }
    }

  // Delete the temp file
  override def afterAll() {
    info(s"Test suite executed ${getCounter()} state transitions in total.")
  }

  private def nameOf(state: State): String = {
    val className = state.getClass.getName
    val lastDot = className.lastIndexOf('.')
    val typeName = {
      val s = if (lastDot < 0) className else className.substring(lastDot + 1)
      if (s.last == '$') s.init else s
    }
    val lastDollar = typeName.lastIndexOf('$')
    if (lastDollar < 0) typeName else typeName.substring(lastDollar + 1)
  }

}
