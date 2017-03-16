package com.github.daniel.shuy

import java.io.IOException
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import java.nio.file.attribute.BasicFileAttributes

import com.github.sbtliquibase.SbtLiquibase
import sbt._
import slick.codegen.SourceCodeGenerator
import Keys._
import com.github.sbtliquibase.Import._
import org.h2.tools.DeleteDbFiles
import slick.driver.H2Driver
import slick.model.Model

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Random, Success}

object SbtLiquibaseSlickCodegen extends AutoPlugin {
  val SlickDriver = H2Driver
  val JdbcDriver: String = classOf[org.h2.Driver].getName
  val DbName: String = "sbt_liquibase_slick_codegen"
  val Username: String = ""
  val Password: String = Random.nextString(Random.nextInt(25))  // generate a random password
  val SlickCodegenFileName: String = "Tables.scala" // this is hardcoded in Slick Codegen
  val CacheFileName: String = "sbt_liquibase-slick_codegen_cache"

  override def requires: Plugins = SbtLiquibase

  object autoImport {
    lazy val LiquibaseSlickCodegen: Configuration = config("liquibase-slick-codegen")

    lazy val liquibaseSlickCodegen: TaskKey[File] = TaskKey("liquibase-slick-codegen", "Generate Slick database schema code from Liquibase changelog file")

    lazy val liquibaseSlickCodegenOutputPackage: SettingKey[String] = SettingKey("liquibase-slick-codegen-output-package", "Package the generated Slick database schema code should be placed in")

    lazy val liquibaseSlickCodegenSourceCodeGeneratorFactory: SettingKey[Model => SourceCodeGenerator] = SettingKey("liquibase-slick-codegen-source-code-generator-factory", "The factory for the SourceCodeGenerator to use to generate Slick database schema code")
  }
  import autoImport._

  private[this] lazy val logger = Def.task[Logger] {
    streams.value.log
  }

  private[this] lazy val slickCodegenDir = Def.setting[File] {
    (scalaSource in Compile).value
  }

  private[this] lazy val slickCodegenFile = Def.setting[File] {
    slickCodegenDir.value / liquibaseSlickCodegenOutputPackage.value / SlickCodegenFileName
  }

  private[this] lazy val cacheDir = Def.setting[File] {
    // create cache in configured package so that cache is invalidated if package is changed
    target.value / CacheFileName / liquibaseSlickCodegenOutputPackage.value
  }

  /**
    * Compares the specified cache with the specified input file and output file.
    * If cache doesn't exist, the input file is modified, or the output file doesn't exist,
    * performs the specified action and updates the cache.
    *
    * @param cacheFile
    *                  The cache file to check.
    * @param inputFile
    *                  The input file to compare against.
    * @param outputFile
    *                   The output file to compare against.
    * @param action
    *               The action to perform if cache doesn't exist, the input file is modified, or the output file doesn't exist
    */
  private[this] def checkAndUpdateCache(cacheFile: File, inputFile: File, outputFile: File)(action: Set[File] => Unit): Unit =
    FileFunction.cached(cacheFile, FilesInfo.lastModified, FilesInfo.exists)(action.andThen(_ => Set(outputFile)))(Set(inputFile))

  private[this] lazy val requireLiquibaseSlickCodegen = Def.taskDyn[Boolean] {
    var required = false

    checkAndUpdateCache(cacheDir.value, liquibaseChangelog.value, slickCodegenFile.value)(_ => required = true)

    if (required) {
      // because it is impossible to check the cache without updating it, the cache may end up in a corrupted state if
      // it the cache update is not performed (eg. interrupted due to RuntimeException).
      // Workaround: delete the cache after comparison, if liquibase-slick-codegen is required.
      Def.task {
        deleteCache
        required
      }
    }
    else {
      Def.task {
        required
      }
    }
  }

  private[this] lazy val updateCache = Def.task[Unit] {
    // delete the cache to force an update every time
    deleteCache.value

    checkAndUpdateCache(cacheDir.value, liquibaseChangelog.value, slickCodegenFile.value)(_ => ())
  }

  private[this] lazy val deleteCache = Def.task[Unit] {
    if (cacheDir.value.exists()) {
      Files.walkFileTree(cacheDir.value.toPath, new SimpleFileVisitor[Path] {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          val result = super.visitFile(file, attrs)
          Files.delete(file)
          result
        }

        override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
          val result = super.postVisitDirectory(dir, exc)
          Files.delete(dir)
          result
        }
      })
    }
  }

  private[this] implicit val ec = ExecutionContext.global

  private[this] lazy val slickCodegen = Def.task[Future[Unit]] {
    // prevent Slick Codegen from creating database if it doesn't exist
    val url = s"${(liquibaseUrl in LiquibaseSlickCodegen).value};IFEXISTS=TRUE"

    val dbFactory = SlickDriver.api.Database
    val db = dbFactory.forURL(url, Username, Password, driver = JdbcDriver, keepAliveConnection = true)

    val modelAction = SlickDriver.createModel().withPinnedSession
    val modelFuture = db.run(modelAction)

    val codegenFuture = modelFuture.map(model => liquibaseSlickCodegenSourceCodeGeneratorFactory.value.apply(model))
    codegenFuture.onComplete {
      case Success(_) => logger.value.info(s"Slick Codegen: Successfully generated database schema code at ${slickCodegenFile.value.getPath}")
      case Failure(t) => logger.value.error(t.getStackTraceString)
    }
    codegenFuture.onComplete(_ => db.close)

    // TODO: replace package with a new setting
    codegenFuture.map(codegen => codegen.writeToFile(classOf[H2Driver].getName, slickCodegenDir.value.getPath, liquibaseSlickCodegenOutputPackage.value))
  }

  override lazy val projectSettings: Seq[Setting[_]] =
    inConfig(LiquibaseSlickCodegen)(SbtLiquibase.liquibaseBaseSettings(LiquibaseSlickCodegen) ++
      Seq(
        liquibaseDriver := JdbcDriver,

        // create an embedded database with Auto Mixed Mode
        // (Server is required for Liquibase and Slick Codegen to share a database, as both do not share the same Class Loader)
        // persist database to a file (Auto Mixed Mode cannot be used with in-memory databases)
        liquibaseUrl := s"jdbc:h2:file:${target.value.getPath}/$DbName;AUTO_SERVER=TRUE",

        liquibaseUsername := Username,
        liquibasePassword := Password,

        liquibaseChangelog := (liquibaseChangelog in Compile).value,

        dependencyClasspath := (dependencyClasspath in Compile).value,

        // Delete pre-existing embedded database before performing update
        liquibaseUpdate := liquibaseUpdate.dependsOn(Def.task[Unit] {
          logger.value.info("Performing cleanup:")
          DeleteDbFiles.execute(target.value.getPath, DbName, false)
        }).value
      )
    ) ++
    Seq(
      // read version of H2 library from Manifest
      libraryDependencies += "com.h2database" % "h2" % classOf[org.h2.Driver].getPackage.getImplementationVersion,

      // stub out required Liquibase settings to make them optional
      liquibaseDriver := "",
      liquibaseUrl := "",
      liquibaseUsername := "",
      liquibasePassword := "",

      // default to Slick Codegen's bundled SourceCodeGenerator
      liquibaseSlickCodegenSourceCodeGeneratorFactory := {
        model: Model => new SourceCodeGenerator(model)
      },

      liquibaseSlickCodegen := Def.taskDyn {
        (liquibaseUpdate in LiquibaseSlickCodegen).value

        Def.task {
          Def.taskDyn {
            Await.result(
              Await.ready(slickCodegen.value, Duration.Inf) map {
                // update cache with newly generated database schema code file
                _ => updateCache
              },
              Duration.Inf
            )
          }.value

          slickCodegenFile.value
        }
      }.value,

      compile := Def.taskDyn {
        if (requireLiquibaseSlickCodegen.value) {
          logger.value.info("Executing liquibase-slick-codegen")

          // execute liquibase-slick-codegen before each compile
          Def.taskDyn {
            liquibaseSlickCodegen.value

            compile in Compile
          }
        }
        else {
          compile in Compile
        }
      }.value
    )
}
