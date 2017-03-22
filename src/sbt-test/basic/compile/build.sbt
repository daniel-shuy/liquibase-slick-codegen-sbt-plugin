import scala.util.Random

// scala identifiers must begin with an alphabet
def randomIdentifierName = Random.alphanumeric.dropWhile(_.isDigit).take(Random.nextInt(10) + 1).mkString

lazy val checkSlickDatabaseSchemaCodeExists: TaskKey[Unit] = TaskKey("check-slick-database-schema-code-exists")
lazy val checkCompiledSlickDatabaseSchemaCodeExists: TaskKey[Unit] = TaskKey("check-compiled-slick-database-schema-code-exists")

lazy val testBasicCompile = project
  .in(file("."))
  .settings(
    name := "test/basic/compile",

    liquibaseSlickCodegenOutputPackage := randomIdentifierName,
    liquibaseSlickCodegenOutputClass := randomIdentifierName,

    liquibaseChangelog := file("changelog.xml"),

    checkSlickDatabaseSchemaCodeExists := {
      val slickCodegenFile = (scalaSource in Compile).value / liquibaseSlickCodegenOutputPackage.value / s"${liquibaseSlickCodegenOutputClass.value}.scala"

      assert(slickCodegenFile.exists, s"Slick database schema code file not found at ${slickCodegenFile.getPath}")
    },

    checkCompiledSlickDatabaseSchemaCodeExists := {
      val compiledSlickCodegenFile = (classDirectory in Compile).value / liquibaseSlickCodegenOutputPackage.value / s"${liquibaseSlickCodegenOutputClass.value}.class"

      assert(compiledSlickCodegenFile.exists, s"Compiled Slick database schema code file not found at ${compiledSlickCodegenFile.getPath}")
    }
  )
  .enablePlugins(SbtLiquibaseSlickCodegen)
