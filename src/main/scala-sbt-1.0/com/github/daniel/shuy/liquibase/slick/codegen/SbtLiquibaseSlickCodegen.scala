package com.github.daniel.shuy.liquibase.slick.codegen

import sbt.SettingKey
import slick.jdbc.{H2Profile, JdbcProfile}

object SbtLiquibaseSlickCodegen extends AbstractSbtLiquibaseSlickCodegenPlugin {
  override val slickDriver = H2Profile

  override val autoImport: AbstractSbtLiquibaseSlickCodegenPlugin.autoImport =
    new AbstractSbtLiquibaseSlickCodegenPlugin.autoImport {
      override lazy val liquibaseSlickCodegenProfile: SettingKey[JdbcProfile] =
        SettingKey(
          "liquibase-slick-codegen-profile",
          "The Slick database profile for the generated Slick database schema code"
        )
    }
}
