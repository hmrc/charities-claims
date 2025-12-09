import sbt.Keys.libraryDependencies
import sbt._
import play.core.PlayVersion

object AppDependencies {

  private val bootstrapVersion = "10.4.0"
  private val hmrcMongoVersion = "2.11.0"

  val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-play-30"       % hmrcMongoVersion
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-30"  % bootstrapVersion    % Test,
    "org.playframework"      %% "play-test"               % PlayVersion.current % Test,
    "org.scalatest"          %% "scalatest"               % "3.2.19"            % Test,
    "org.scalamock"          %% "scalamock"               % "7.5.2"             % Test,
    "org.scalatestplus"      %% "scalacheck-1-18"         % "3.2.19.0"          % Test,
    "org.scalatestplus.play" %% "scalatestplus-play"      % "7.0.2"             % Test,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-30" % hmrcMongoVersion    % Test,
    "com.vladsch.flexmark"    % "flexmark-all"            % "0.64.8"            % Test
  )
}
