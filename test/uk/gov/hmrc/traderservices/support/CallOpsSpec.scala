///*
// * Copyright 2023 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.traderservices.support
//
//import java.io.File
//
//import com.typesafe.config._
//import play.api.{Configuration, Environment, Mode}
//import uk.gov.hmrc.traderservices.support.CallOps.localFriendlyUrl
//
//class CallOpsSpec extends UnitSpec {
//
//  val testEnv = Environment(new File(""), classOf[CallOpsSpec].getClassLoader, Mode.Test)
//  val prodEnv = Environment(new File(""), classOf[CallOpsSpec].getClassLoader, Mode.Prod)
//  val devEnv = Environment(new File(""), classOf[CallOpsSpec].getClassLoader, Mode.Dev)
//  val devConf = Configuration(ConfigFactory.parseString(""" run.mode = "Dev" """))
//  val prodConf = Configuration(ConfigFactory.parseString(""" run.mode = "Prod" """))
//
//  "CallOps" should {
//
//    "return the original url if it is in the test environment" in {
//
//      localFriendlyUrl(testEnv, devConf)("A", "B") shouldBe "A"
//    }
//
//    "return url string with localhost and port if is in development environment" in {
//
//      localFriendlyUrl(devEnv, devConf)("A", "B") shouldBe "http://BA"
//    }
//
//    "return the original url if it is in the production environment" in {
//
//      localFriendlyUrl(prodEnv, prodConf)("A", "B") shouldBe "A"
//    }
//
//    "if url is not absolute then return the url regardless of environment" in {
//
//      localFriendlyUrl(devEnv, devConf)("http://A", "B") shouldBe "http://A"
//
//    }
//
//  }
//
//}
