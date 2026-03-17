import sbt._

object AppDependencies {
  val bootstrapVersion = "10.7.0"
  private val playVersion      = "30"

  val mailDependencies: Seq[ModuleID] = Seq(
    "jakarta.mail"      % "jakarta.mail-api" % "2.1.5",
    "org.eclipse.angus" % "angus-mail"       % "2.0.5")

  lazy val compileDeps = Seq(
    "uk.gov.hmrc"                   %% s"bootstrap-frontend-play-$playVersion"    % bootstrapVersion,
    "uk.gov.hmrc"                   %% s"play-frontend-hmrc-play-$playVersion"    % "12.32.0",
    "uk.gov.hmrc.mongo"             %% s"hmrc-mongo-play-$playVersion"            % "2.12.0",
    "uk.gov.hmrc"                   %% s"crypto-json-play-$playVersion"           % "8.4.0",
    "com.googlecode.libphonenumber" % "libphonenumber"                            % "8.13.47",
    "com.fasterxml.jackson.module"  %% "jackson-module-scala"                     % "2.19.0",
    "commons-codec"                 % "commons-codec"                             % "1.10"
  ) ++ mailDependencies

  val testDeps: Seq[ModuleID] = (Seq(
    "uk.gov.hmrc"            %% s"bootstrap-test-play-$playVersion"        % bootstrapVersion,
    "org.mockito"            %% "mockito-scala-scalatest"                  % "1.17.37",
    "org.scalatestplus"      %% "scalacheck-1-17"                          % "3.2.18.0",
    "com.vladsch.flexmark"   %  "flexmark-all"                             % "0.64.8",
    "org.scalameta"          %% "munit"                                    % "0.7.29",
    "org.scalacheck"         %% "scalacheck"                               % "1.18.1"
  ) ++ mailDependencies).map(_ % Test)

  def apply(): Seq[ModuleID] = compileDeps ++ testDeps
}
