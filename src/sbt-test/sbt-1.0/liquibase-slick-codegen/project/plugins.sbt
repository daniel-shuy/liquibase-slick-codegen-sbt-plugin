sys.props.get("plugin.version") match {
  case Some(pluginVersion) => addSbtPlugin("com.github.daniel-shuy" % "sbt-liquibase-slick-codegen" % pluginVersion)
  case _ => sys.error("""|The system property 'plugin.version' is not defined.
                         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
}

useCoursier := false

addSbtPlugin("com.github.daniel-shuy" % "sbt-scripted-scalatest" % "2.0.0")
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.12"
