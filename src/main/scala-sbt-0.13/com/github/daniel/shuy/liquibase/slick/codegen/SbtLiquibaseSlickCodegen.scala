package com.github.daniel.shuy.liquibase.slick.codegen

import slick.driver.H2Driver

object SbtLiquibaseSlickCodegen extends AbstractSbtLiquibaseSlickCodegenPlugin {
  override val slickDriver = H2Driver

  object AutoImport extends AbstractSbtLiquibaseSlickCodegenPlugin.AutoImport

  override val autoImport: AutoImport.type = AutoImport
}
