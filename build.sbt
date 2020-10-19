import sbt.Tests.{Group, SubProcess}
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexes matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := """uk\.gov\.hmrc\.BuildInfo;.*\.Routes;.*\.RoutesPrefix;.*Filters?;MicroserviceAuditConnector;Module;GraphiteStartUp;.*\.Reverse[^.]*""",
    ScoverageKeys.coverageMinimum := 80.00,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true,
    parallelExecution in Test := false
  )
}

lazy val compileDeps = Seq(
  ws,
  "uk.gov.hmrc"                  %% "bootstrap-frontend-play-26" % "2.25.0",
  "uk.gov.hmrc"                  %% "auth-client"                % "3.1.0-play-26",
  "uk.gov.hmrc"                  %% "play-partials"              % "6.11.0-play-26",
  "uk.gov.hmrc"                  %% "agent-kenshoo-monitoring"   % "4.4.0",
  "uk.gov.hmrc"                  %% "play-fsm"                   % "0.56.0-play-26",
  "uk.gov.hmrc"                  %% "domain"                     % "5.10.0-play-26",
  "uk.gov.hmrc"                  %% "mongo-caching"              % "6.15.0-play-26",
  "uk.gov.hmrc"                  %% "json-encryption"            % "4.8.0-play-26",
  "uk.gov.hmrc"                  %% "play-frontend-govuk"        % "0.51.0-play-26",
  "uk.gov.hmrc"                  %% "play-frontend-hmrc"         % "0.20.0-play-26",
  "com.googlecode.libphonenumber" % "libphonenumber"             % "8.12.11"
)

def testDeps(scope: String) =
  Seq(
    "uk.gov.hmrc"            %% "hmrctest"           % "3.9.0-play-26" % scope,
    "org.scalatest"          %% "scalatest"          % "3.0.9"         % scope,
    "org.mockito"             % "mockito-core"       % "3.1.0"         % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.3"         % scope,
    "com.github.tomakehurst"  % "wiremock"           % "2.27.2"        % scope
  )

val jettyVersion = "9.2.24.v20180105"

val jettyOverrides = Seq(
  "org.eclipse.jetty"           % "jetty-server"       % jettyVersion % IntegrationTest,
  "org.eclipse.jetty"           % "jetty-servlet"      % jettyVersion % IntegrationTest,
  "org.eclipse.jetty"           % "jetty-security"     % jettyVersion % IntegrationTest,
  "org.eclipse.jetty"           % "jetty-servlets"     % jettyVersion % IntegrationTest,
  "org.eclipse.jetty"           % "jetty-continuation" % jettyVersion % IntegrationTest,
  "org.eclipse.jetty"           % "jetty-webapp"       % jettyVersion % IntegrationTest,
  "org.eclipse.jetty"           % "jetty-xml"          % jettyVersion % IntegrationTest,
  "org.eclipse.jetty"           % "jetty-client"       % jettyVersion % IntegrationTest,
  "org.eclipse.jetty"           % "jetty-http"         % jettyVersion % IntegrationTest,
  "org.eclipse.jetty"           % "jetty-io"           % jettyVersion % IntegrationTest,
  "org.eclipse.jetty"           % "jetty-util"         % jettyVersion % IntegrationTest,
  "org.eclipse.jetty.websocket" % "websocket-api"      % jettyVersion % IntegrationTest,
  "org.eclipse.jetty.websocket" % "websocket-common"   % jettyVersion % IntegrationTest,
  "org.eclipse.jetty.websocket" % "websocket-client"   % jettyVersion % IntegrationTest
)

lazy val root = (project in file("."))
  .settings(
    name := "trader-services-route-one-frontend",
    organization := "uk.gov.hmrc",
    scalaVersion := "2.12.12",
    PlayKeys.playDefaultPort := 9379,
    TwirlKeys.templateImports ++= Seq(
      "play.twirl.api.HtmlFormat",
      "uk.gov.hmrc.govukfrontend.views.html.components._",
      "uk.gov.hmrc.hmrcfrontend.views.html.{components => hmrcComponents}",
      "uk.gov.hmrc.govukfrontend.views.html.helpers._",
      "uk.gov.hmrc.traderservices.views.html.components"
    ),
    resolvers := Seq(
      Resolver.bintrayRepo("hmrc", "releases"),
      Resolver.bintrayRepo("hmrc", "release-candidates"),
      Resolver.typesafeRepo("releases"),
      Resolver.jcenterRepo
    ),
    libraryDependencies ++= compileDeps ++ testDeps("test") ++ testDeps("it"),
    dependencyOverrides ++= jettyOverrides,
    publishingSettings,
    scoverageSettings,
    unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
    scalafmtOnCompile in Compile := true,
    scalafmtOnCompile in Test := true,
    majorVersion := 0,
    // concatenate js
    Concat.groups := Seq(
      "javascripts/application.js" ->
        group(
          Seq(
            "lib/govuk-frontend/govuk/all.js",
            "javascripts/jquery.min.js",
            "javascripts/app.js",
            "javascripts/timeout/timeoutDialog.js",
            "javascripts/autocomplete.js"
          )
        )
    ),
    // prevent removal of unused code which generates warning errors due to use of third-party libs
    uglifyCompressOptions := Seq("unused=false", "dead_code=false"),
    // below line required to force asset pipeline to operate in dev rather than only prod
    pipelineStages in Assets := Seq(concat, uglify),
    // only compress files generated by concat
    includeFilter in uglify := GlobFilter("application.js")
  )
  .configs(IntegrationTest)
  .settings(
    Keys.fork in IntegrationTest := false,
    Defaults.itSettings,
    unmanagedSourceDirectories in IntegrationTest += baseDirectory(_ / "it").value,
    parallelExecution in IntegrationTest := false,
    testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    scalafmtOnCompile in IntegrationTest := true
  )
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .enablePlugins(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)

inConfig(IntegrationTest)(org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings)

def oneForkedJvmPerTest(tests: Seq[TestDefinition]) =
  tests.map { test =>
    new Group(test.name, Seq(test), SubProcess(ForkOptions().withRunJVMOptions(Vector(s"-Dtest.name=${test.name}"))))
  }
