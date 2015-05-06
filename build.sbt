organization := "com.typesafe"
name := "jse"

scalaVersion := "2.10.4"
crossScalaVersions := Seq(scalaVersion.value, "2.11.5")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.9",
  "com.typesafe.akka" %% "akka-contrib" % "2.3.9",
  "io.apigee.trireme" % "trireme-core" % "0.8.5",
  "io.apigee.trireme" % "trireme-node10src" % "0.8.5",
  "io.spray" %% "spray-json" % "1.3.2",
  "org.slf4j" % "slf4j-simple" % "1.7.12",
  "org.specs2" %% "specs2-core" % "3.4" % "test",
  "junit" % "junit" % "4.11" % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.9" % "test"
)
// Required by specs2 to get scalaz-stream
resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

lazy val root = project in file(".")

lazy val `js-engine-tester` = project.dependsOn(root)

// Somehow required to get a js engine in tests (https://github.com/sbt/sbt/issues/1214)
fork in Test := true

// Publish settings
publishTo := {
  if (isSnapshot.value) Some(Opts.resolver.sonatypeSnapshots)
  else Some(Opts.resolver.sonatypeStaging)
}
homepage := Some(url("https://github.com/typesafehub/js-engine"))
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.html"))
pomExtra := {
  <scm>
    <url>git@github.com:typesafehub/js-engine.git</url>
    <connection>scm:git:git@github.com:typesafehub/js-engine.git</connection>
  </scm>
  <developers>
    <developer>
      <id>playframework</id>
      <name>Play Framework Team</name>
      <url>https://github.com/playframework</url>
    </developer>
  </developers>
}
pomIncludeRepository := { _ => false }

// Release settings
releaseSettings
ReleaseKeys.crossBuild := true
ReleaseKeys.publishArtifactsAction := PgpKeys.publishSigned.value
ReleaseKeys.tagName := (version in ThisBuild).value

