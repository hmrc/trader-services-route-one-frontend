package uk.gov.hmrc.traderservices.controllers

import uk.gov.hmrc.traderservices.journeys.AmendCaseJourneyModel.root
import uk.gov.hmrc.traderservices.support.ServerISpec

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global

class LanguageSwitchControllerISpec extends ServerISpec {

  val dateTime = LocalDateTime.now()

  "LanguageSwitchController" when {

    "GET /language/cy" should {
      "show change language to cymraeg" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = root
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val result = await(request("/language/cymraeg").get())
        result.status shouldBe 200
        journey.getState shouldBe state
        result.body should include("Change the language to English")
      }
    }

    "GET /language/engligh" should {
      "show change language to english" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = root
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val result = await(request("/language/englisg").get())
        result.status shouldBe 200
        journey.getState shouldBe state
        result.body should include("Newid yr iaith ir Gymraeg")
      }
    }

    "GET /language/xxx" should {
      "show change language to default English if unknown" in {
        implicit val journeyId: JourneyId = JourneyId()
        val state = root
        journey.setState(state)
        givenAuthorisedForEnrolment(Enrolment("HMRC-XYZ", "EORINumber", "foo"))
        val result = await(request("/language/xxx").get())
        result.status shouldBe 200
        journey.getState shouldBe state
        result.body should include("Newid yr iaith ir Gymraeg")
      }
    }
  }
}
