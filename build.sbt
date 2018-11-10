sbtPlugin := true

organization := "com.github.daniel-shuy"

name := "sbt-liquibase-slick-codegen"

licenses := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

homepage := Some(url("https://github.com/daniel-shuy/liquibase-slick-codegen-sbt-plugin"))

addSbtPlugin("com.permutive" % "sbt-liquibase" % "1.1.0")

crossSbtVersions := Seq(
  "0.13.17",
  "1.2.6"
)

def slickVersion(scalaVersion: String) =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, 10)) | Some((2, 11)) => "3.0.0"
    case Some((2, 12)) => "3.2.0"
    case _ =>
      throw new IllegalArgumentException(s"Unsupported Scala version $scalaVersion")
  }

libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick-codegen" % slickVersion(scalaVersion.value),

  "com.h2database" % "h2" % "1.4.197"
)
