package com.github.daniel.shuy.liquibase.slick.codegen

import slick.driver.H2Driver

object SbtLiquibaseSlickCodegen extends AbstractSbtLiquibaseSlickCodegenPlugin {
  override val slickDriver = H2Driver

  override val autoImport: AbstractSbtLiquibaseSlickCodegenPlugin.autoImport =
    new AbstractSbtLiquibaseSlickCodegenPlugin.autoImport {}
}
