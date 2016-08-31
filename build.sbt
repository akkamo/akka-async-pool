import com.typesafe.sbt.pgp.PgpKeys._
import sbt.Keys._

lazy val cAkkaVersion = "2.4.9"
lazy val cScalaVersion = "2.11.8"

organization in Global := "eu.akkamo"

description := "Akkamo modules in Akka. Runtime assembly of several modules running on top of Akka."

crossScalaVersions:= Seq("2.11.8", cScalaVersion)

scalaVersion:= cScalaVersion

version := "1.0.0-SNAPSHOT"

name := "akkamo-async-pool"

publishMavenStyle:= true

resolvers ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"))

publishTo in Global := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

// enable automatic linking to the external Scaladoc of managed dependencies
autoAPIMappings := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

scalacOptions ++= Seq(
  "-encoding", "utf-8",
  "-unchecked",
  "-deprecation",
  "-feature",
  "-language:postfixOps",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-Xfatal-warnings",
  "-Xlint",
  "-Xfuture",
  "-Yrangepos",
  "-Yrangepos",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Ywarn-unused-import",
  "-Ywarn-unused",
  "-Xlint:missing-interpolator"
)

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % cAkkaVersion % "provided" withSources,
    "com.typesafe.akka" %% "akka-testkit" % cAkkaVersion % "test" withSources,
    "org.scalatest" %% "scalatest" % "3.0.0" % "test" withSources
)

pomExtra :=
  <url>http://www.akkamo.eu</url>
    <licenses>
      <license>
        <name>unlicense</name>
        <url>http://unlicense.org/</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>https://github.com/akkamo/akkamo.git</url>
    </scm>
    <developers>
      <developer>
        <id>JurajBurian</id>
        <name>Juraj Burian</name>
        <url>https://github.com/JurajBurian</url>
      </developer>
      <developer>
        <id>VaclavSvejcar</id>
        <name>Vaclav Svejcar</name>
        <url>https://github.com/vaclavsvejcar</url>
      </developer>
    </developers>