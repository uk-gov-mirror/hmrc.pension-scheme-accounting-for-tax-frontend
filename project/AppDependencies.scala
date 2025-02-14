import sbt._

object AppDependencies {
  import play.core.PlayVersion

  val compile = Seq(
    play.sbt.PlayImport.ws,
    "uk.gov.hmrc"                   %%  "play-health"                    % "3.16.0-play-27",
    "uk.gov.hmrc"                   %%  "play-conditional-form-mapping"  % "1.6.0-play-27",
    "uk.gov.hmrc"                   %%  "bootstrap-frontend-play-27"     % "3.4.0",
    "uk.gov.hmrc"                   %%  "play-nunjucks"                  % "0.27.0-play-27",
    "uk.gov.hmrc"                   %%  "play-nunjucks-viewmodel"        % "0.13.0-play-27",
    "org.webjars.npm"               %   "govuk-frontend"                 % "3.5.0",
    "org.webjars.npm"               %   "hmrc-frontend"                  % "1.19.0",
    "com.google.inject.extensions"  %   "guice-multibindings"            % "4.2.2",
    "uk.gov.hmrc"                   %%  "domain"                         % "5.11.0-play-27"
  )

  val test = Seq(
    "org.scalatest"               %% "scalatest"          % "3.0.7",
    "org.scalatestplus.play"      %% "scalatestplus-play" % "4.0.2",
    "org.pegdown"                 %  "pegdown"            % "1.6.0",
    "org.jsoup"                   %  "jsoup"              % "1.10.3",
    "com.typesafe.play"           %% "play-test"          % PlayVersion.current,
    "org.mockito"                 %  "mockito-all"        % "1.10.19",
    "org.scalacheck"              %% "scalacheck"         % "1.14.0",
    "com.github.tomakehurst"      %  "wiremock-jre8"      % "2.21.0"
  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compile ++ test
}
