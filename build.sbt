lazy val appName = "api-platform-update-usage-plan-lambda"

lazy val appDependencies: Seq[ModuleID] = compileDependencies ++ testDependencies

lazy val compileDependencies = Seq(
  "uk.gov.hmrc" %% "api-platform-manage-api" % "0.49.0"
)

lazy val mockitoVersion = "5.18.0"

lazy val testDependencies = Seq(
  "org.scalatest"     %% "scalatest"    % "3.2.19",
  "org.mockito"        % "mockito-core" % mockitoVersion,
  "org.scalatestplus" %% "mockito-5-18" % "3.2.19.0"
).map(_ % Test)

lazy val lambda = (project in file("."))
  .settings(
    name := appName,
    scalaVersion := "2.13.16",
    libraryDependencies ++= appDependencies,
    Test / parallelExecution := false,
    Test / fork := true,
    Test / javaOptions += s"-javaagent:${csrCacheDirectory.value.getAbsolutePath}/https/repo1.maven.org/maven2/org/mockito/mockito-core/$mockitoVersion/mockito-core-$mockitoVersion.jar"
  )
  .settings(
    resolvers += "hmrc-releases" at "https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases/"
  )
  .settings(
    assembly / assemblyOutputPath := file(s"./$appName.zip"),
    assembly / assemblyMergeStrategy := {
      case PathList("module-info.class") => MergeStrategy.first
      case PathList("META-INF", "versions", "9", "module-info.class") => MergeStrategy.last
      case PathList("META-INF", xs @ _*) => MergeStrategy.first
      case path if path.endsWith("io.netty.versions.properties") => MergeStrategy.discard
      case path if path.endsWith("BuildInfo$.class") => MergeStrategy.discard
      case path =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(path)
    }
  )
  .settings(Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.AllLibraryJars)

// Coverage configuration
coverageMinimumStmtTotal := 85
coverageMinimumBranchTotal := 85
coverageFailOnMinimum := true
coverageExcludedPackages := "<empty>"
