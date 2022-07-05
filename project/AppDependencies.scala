import sbt._

object AppDependencies {
  lazy val compileDeps = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-frontend-play-28" % "6.2.0",
    "uk.gov.hmrc"                  %% "auth-client"                % "5.8.0-play-28",
    "uk.gov.hmrc"                  %% "play-fsm"                   % "0.89.0-play-28",
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-28"         % "0.66.0",
    "uk.gov.hmrc"                  %% "json-encryption"            % "4.11.0-play-28",
    "uk.gov.hmrc"                  %% "play-frontend-hmrc"         % "3.21.0-play-28",
    "com.googlecode.libphonenumber" % "libphonenumber"             % "8.12.31",
    "com.fasterxml.jackson.module" %% "jackson-module-scala"       % "2.12.5",
    "com.sun.mail"                  % "javax.mail"                 % "1.6.2"
  )

  def testDeps(scope: String) =
    Seq(
      "org.scalatest"       %% "scalatest"       % "3.2.8"   % scope,
      "com.vladsch.flexmark" % "flexmark-all"    % "0.36.8"  % scope,
      "org.scalameta"       %% "munit"           % "0.7.29"  % scope,
      "org.scalacheck"      %% "scalacheck"      % "1.15.4"  % scope,
      "org.scalatestplus"   %% "scalacheck-1-15" % "3.2.8.0" % scope
    )

  lazy val itDeps = Seq(
    "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0"  % "it",
    "com.github.tomakehurst"  % "wiremock-jre8"      % "2.27.2" % "it"
  )

  def apply(): Seq[ModuleID] =
    compileDeps ++ testDeps("test") ++ testDeps("it") ++ itDeps
}
