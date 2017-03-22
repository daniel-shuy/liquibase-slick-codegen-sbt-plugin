sbtPlugin := true

organization := "com.github.daniel-shuy"

name := "sbt-liquibase-slick-codegen"

version := "0.1.0-SNAPSHOT"

licenses := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

homepage := Some(url("https://github.com/daniel-shuy/liquibase-slick-codegen-sbt-plugin"))

addSbtPlugin("com.github.sbtliquibase" % "sbt-liquibase" % "0.2.0")

libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick-codegen" % "3.1.1",

  "com.h2database" % "h2" % "1.4.193"
)
