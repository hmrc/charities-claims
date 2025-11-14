import uk.gov.hmrc.DefaultBuildSettings
import scoverage.ScoverageKeys

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "3.3.6"
ThisBuild / scalacOptions += "-Wconf:msg=Flag.*repeatedly:s"

lazy val microservice = Project("charities-claims", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) // Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    name := "charities-claims",
    organization := "uk.gov.hmrc",
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
    // suppress warnings in generated routes files
    scalacOptions += "-Wconf:src=routes/.*:s"
  )
  .settings(PlayKeys.playDefaultPort := 8031)
  .settings(scalafmtOnCompile := true)
  .settings(CodeCoverageSettings.settings: _*)
  .settings(ScoverageKeys.coverageFailOnMinimum := false)
  .settings(ScoverageKeys.coverageMinimumStmtTotal := 90)
