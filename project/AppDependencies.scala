import sbt.*

object AppDependencies {
  val bootstrapVersion = "10.8.0"
  private val playVersion      = "30"

  val mailDependencies: Seq[ModuleID] = Seq(
    "jakarta.mail"      % "jakarta.mail-api" % "2.1.5",
    "org.eclipse.angus" % "angus-mail"       % "2.0.5")

  lazy val compileDeps: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                   %% s"bootstrap-frontend-play-$playVersion"    % bootstrapVersion,
    "uk.gov.hmrc"                   %% s"play-frontend-hmrc-play-$playVersion"    % "13.14.0",
    "uk.gov.hmrc.mongo"             %% s"hmrc-mongo-play-$playVersion"            % "2.14.0",
    "uk.gov.hmrc"                   %% s"crypto-json-play-$playVersion"           % "8.4.0",
    "com.fasterxml.jackson.module"  %% "jackson-module-scala"                     % "2.22.3.1",
    "commons-codec"                 % "commons-codec"                             % "1.22.1"
  ) ++ mailDependencies

  val testDeps: Seq[ModuleID] = (Seq(
    "uk.gov.hmrc"            %% s"bootstrap-test-play-$playVersion"        % bootstrapVersion,
    "org.scalatestplus"      %% "scalacheck-1-17"                          % "3.2.18.0",
    "org.scalameta"          %% "munit"                                    % "0.7.29",
    "org.scalacheck"         %% "scalacheck"                               % "1.20.0"
  ) ++ mailDependencies).map(_ % Test)

  def apply(): Seq[ModuleID] = compileDeps ++ testDeps
}
