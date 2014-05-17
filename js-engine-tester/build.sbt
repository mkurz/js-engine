resolvers += Resolver.sonatypeRepo("snapshots")

fork in run := true

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-simple" % "1.7.6"
)

javaOptions ++= Seq("-Dorg.slf4j.simpleLogger.defaultLogLevel=trace")