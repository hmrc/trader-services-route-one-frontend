import play.sbt.routes.RoutesKeys
import sbt.Def
import sbt.Tests.{Group, SubProcess}
import play.sbt.routes.RoutesKeys
import uk.gov.hmrc.DefaultBuildSettings
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.*

ThisBuild / scalaVersion := "2.13.12"
ThisBuild / majorVersion := 0

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexes matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := """uk\.gov\.hmrc\.BuildInfo;.*\.Routes;.*\.RoutesPrefix;.*Filters?;MicroserviceAuditConnector;Module;GraphiteStartUp;.*\.Reverse[^.]*;uk\.gov\.hmrc\.traderservices\.views\.html\.components\.*""",
    ScoverageKeys.coverageMinimumStmtTotal := 80.00,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true
  )
}

lazy val root = (project in file("."))
  .settings(
    name := "trader-services-route-one-frontend",
    organization := "uk.gov.hmrc",
    PlayKeys.playDefaultPort := 9379,
    TwirlKeys.templateImports ++= Seq(
      "play.twirl.api.HtmlFormat",
      "uk.gov.hmrc.govukfrontend.views.html.components._",
      "uk.gov.hmrc.traderservices.views.html.components",
      "uk.gov.hmrc.traderservices.views.ViewHelpers._"
    ),
    libraryDependencies ++= AppDependencies(),
    scoverageSettings,
    WebpackKeys.webpack / WebpackKeys.outputFileName := "javascripts/application.min.js",
    WebpackKeys.webpack / WebpackKeys.entries := Seq("assets:javascripts/index.ts"),
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
    Compile / scalafmtOnCompile := true,
    Test / javaOptions += "-Djava.locale.providers=CLDR,JRE",
    Test / parallelExecution := false,
    Test / scalafmtOnCompile := true
  )
  .settings(
    RoutesKeys.routesImport += "uk.gov.hmrc.play.bootstrap.binders.RedirectUrl"
  )
  .settings(
    scalacOptions += "-Wconf:cat=unused-imports&src=html/.*:s"
  )
  .disablePlugins(JUnitXmlReportPlugin) // Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .configs(Test)


lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(root % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(DefaultBuildSettings.itSettings(true) ++ Seq(
    Test / javaOptions += "-Djava.locale.providers=CLDR,JRE",
    Test / parallelExecution := false
  ))
  .settings(libraryDependencies ++= AppDependencies.testDeps)
