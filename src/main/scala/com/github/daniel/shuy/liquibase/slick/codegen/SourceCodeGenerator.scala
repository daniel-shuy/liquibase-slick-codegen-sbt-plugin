package com.github.daniel.shuy.liquibase.slick.codegen

import slick.model.Model

/**
  * A SourceCodeGenerator implementation that doesn't modify any behavior,
  * except preventing certain core methods from being overridden.
  * <p>
  * Extend this class and override methods to customize the behavior of Slick Codegen.
  * <p>
  * This class also adds the convenience of not having to import slick-codegen.
  *
  * @param model Slick data model for which code should be generated.
  */
class SourceCodeGenerator(model: Model) extends slick.codegen.SourceCodeGenerator(model) {
  // prevent this method from being overridden
  override final def writeStringToFile(content: String, folder: String, pkg: String, fileName: String): Unit = super.writeStringToFile(content, folder, pkg, fileName)

  // prevent this method from being overridden
  override final def writeToFile(profile: String, folder: String, pkg: String, container: String, fileName: String): Unit = super.writeToFile(profile, folder, pkg, container, fileName)
}
