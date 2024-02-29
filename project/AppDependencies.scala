import sbt._

object AppDependencies {
  val bootstrapVersion = "8.4.0"
  private val playVersion      = "30"
  private val mongoVersion     = "1.7.0"

  lazy val compileDeps = Seq(
    "uk.gov.hmrc"                   %% s"bootstrap-frontend-play-$playVersion"    % bootstrapVersion,
    "uk.gov.hmrc"                   %% s"play-frontend-hmrc-play-$playVersion"    % "8.5.0",
    "uk.gov.hmrc.mongo"             %% s"hmrc-mongo-play-$playVersion"            % mongoVersion,
    "uk.gov.hmrc"                   %% "json-encryption"                          % "5.1.0-play-28",
    "com.googlecode.libphonenumber" % "libphonenumber"                            % "8.13.26",
    "com.fasterxml.jackson.module"  %% "jackson-module-scala"                     % "2.14.2",
    "com.sun.mail"                  % "javax.mail"                                % "1.6.2",
    "commons-codec"                 % "commons-codec"                             % "1.10"
  )

  val testDeps: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% s"bootstrap-test-play-$playVersion"  % bootstrapVersion,
    "org.mockito"            %% "mockito-scala-scalatest"            % "1.17.29",
    "org.scalatestplus"      %% "scalacheck-1-17"                    % "3.2.17.0",
    "com.vladsch.flexmark"   %  "flexmark-all"                       % "0.64.8",
    "org.scalameta"          %% "munit"                              % "0.7.29",
    "org.scalacheck"         %% "scalacheck"                         % "1.17.0",
  "com.github.tomakehurst"   % "wiremock-jre8"                       % "2.35.0"
  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compileDeps ++ testDeps
}
