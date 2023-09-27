import sbt._

object AppDependencies {
  val playVersion = "7.22.0"

  lazy val compileDeps = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-frontend-play-28" % playVersion,
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-28"         % "0.68.0",
    "uk.gov.hmrc"                  %% "json-encryption"            % "5.1.0-play-28",
    "uk.gov.hmrc"                  %% "play-frontend-hmrc"         % "7.0.0-play-28",
    "com.googlecode.libphonenumber" % "libphonenumber"             % "8.12.31",
    "com.fasterxml.jackson.module" %% "jackson-module-scala"       % "2.14.2",
    "com.sun.mail"                  % "javax.mail"                 % "1.6.2",
    "commons-codec"                 % "commons-codec"              % "1.10"
  )

  def testDeps(scope: String) =
    Seq(
      "org.scalatest"       %% "scalatest"              % "3.2.15"     % scope,
      "com.vladsch.flexmark" % "flexmark-all"           % "0.64.6"    % scope,
      "org.scalameta"       %% "munit"                  % "0.7.29"    % scope,
      "org.scalacheck"      %% "scalacheck"             % "1.17.0"    % scope,
      "org.scalatestplus"   %% "scalacheck-1-15"        % "3.2.11.0"   % scope,
      "uk.gov.hmrc"         %% "bootstrap-test-play-28" % playVersion % scope
    )

  lazy val itDeps = Seq(
    "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0"  % "it",
    "com.github.tomakehurst"  % "wiremock-jre8"      % "2.35.0" % "it"
  )

  def apply(): Seq[ModuleID] =
    compileDeps ++ testDeps("test") ++ testDeps("it") ++ itDeps
}
