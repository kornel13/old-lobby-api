import play.core.PlayVersion.akkaVersion

val playJsonDerivedCodecs = "org.julienrf" %% "play-json-derived-codecs" % "6.0.0"
val chimney = "io.scalaland" %% "chimney" % "0.3.2"
val hasher = "com.outr" %% "hasher" % "1.2.2"

val testKit = Seq(
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test,
  "org.awaitility" % "awaitility" % "4.0.1" % Test,
)

val slick = Seq(
  "com.typesafe.play" %% "play-slick" % "5.0.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "5.0.0"
)

val postgresDriver = "org.postgresql" % "postgresql" % "42.2.5"

resolvers in ThisBuild += Resolver.bintrayRepo("lunaryorn", "maven")
resolvers in ThisBuild += Resolver.jcenterRepo

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(
    name := "LobbyAPI",
    version := "0.1",
    scalaVersion := "2.13.1",
    maintainer := "Kornel Mrozek",
    libraryDependencies ++= Seq(
      guice,
      ws,
      playJsonDerivedCodecs,
      postgresDriver,
      chimney,
      hasher
    ) ++ testKit
      ++ slick,
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-Xfatal-warnings",
      "-language:postfixOps"
    ),
    javaOptions in Universal ++= Seq(
      "-Dpidfile.path=/dev/null"
    )
  )
