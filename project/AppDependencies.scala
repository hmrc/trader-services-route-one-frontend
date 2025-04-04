import sbt._

object AppDependencies {
  val bootstrapVersion = "8.6.0"
  private val playVersion      = "30"

  lazy val compileDeps = Seq(
    "uk.gov.hmrc"                   %% s"bootstrap-frontend-play-$playVersion"    % bootstrapVersion,
    "uk.gov.hmrc"                   %% s"play-frontend-hmrc-play-$playVersion"    % "8.5.0",
    "uk.gov.hmrc.mongo"             %% s"hmrc-mongo-play-$playVersion"            % "2.6.0",
    "uk.gov.hmrc"                   %% s"crypto-json-play-$playVersion"           % "7.6.0",
    "com.googlecode.libphonenumber" % "libphonenumber"                            % "8.13.47",
    "com.fasterxml.jackson.module"  %% "jackson-module-scala"                     % "2.14.2",
    "com.sun.mail"                  % "javax.mail"                                % "1.6.2",
    "commons-codec"                 % "commons-codec"                             % "1.10"
  )

  val testDeps: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% s"bootstrap-test-play-$playVersion"        % bootstrapVersion,
    "org.mockito"            %% "mockito-scala-scalatest"                  % "1.17.37",
    "org.scalatestplus"      %% "scalacheck-1-17"                          % "3.2.18.0",
    "com.vladsch.flexmark"   %  "flexmark-all"                             % "0.64.8",
    "org.scalameta"          %% "munit"                                    % "0.7.29",
    "org.scalacheck"         %% "scalacheck"                               % "1.18.1"
  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compileDeps ++ testDeps
}
