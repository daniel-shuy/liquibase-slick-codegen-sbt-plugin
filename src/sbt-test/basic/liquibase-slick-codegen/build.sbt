import com.github.daniel.shuy.sbt.scripted.scalatest.SbtScriptedScalaTest.FullStacks
import com.github.daniel.shuy.sbt.scripted.scalatest.ScriptedScalaTestSuiteMixin
import org.scalatest.WordSpec

import scala.util.Random

// scala identifiers must begin with an alphabet
def randomIdentifierName = Random.alphanumeric.dropWhile(_.isDigit).take(Random.nextInt(10) + 1).mkString

lazy val testBasicLiquibaseSlickCodegen = project
  .in(file("."))
  .settings(
    name := "test/basic/liquibase-slick-codegen",

    liquibaseSlickCodegenOutputPackage := randomIdentifierName,
    liquibaseSlickCodegenOutputClass := randomIdentifierName,

    liquibaseChangelog := file("changelog.xml"),

    scriptedScalaTestStacks := FullStacks,
    scriptedScalaTestSpec := Some(new WordSpec with ScriptedScalaTestSuiteMixin {
      override val sbtState: State = state.value

      "liquibase-slick-codegen" should {
        "generate Slick database schema code" in {
          val slickCodegenFile = (scalaSource in Compile).value / liquibaseSlickCodegenOutputPackage.value / s"${liquibaseSlickCodegenOutputClass.value}.scala"

          Project.runTask(compile, state.value)

          assert(slickCodegenFile.exists(), s"$slickCodegenFile not found")
        }
      }
    })
  )
  .enablePlugins(SbtLiquibaseSlickCodegen)
