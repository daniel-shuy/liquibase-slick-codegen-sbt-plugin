package com.github.daniel.shuy.liquibase.slick.codegen

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import java.security.SecureRandom

import com.permutive.sbtliquibase.Import._
import com.permutive.sbtliquibase.SbtLiquibase
import org.h2.tools.DeleteDbFiles
import sbt.Keys._
import sbt._
import slick.driver.JdbcProfile
import slick.model.Model

import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.util.{Failure, Random, Success}

object AbstractSbtLiquibaseSlickCodegenPlugin {
  trait AutoImport {
    lazy val LiquibaseSlickCodegen: Configuration = config(
      "liquibase-slick-codegen"
    )

    lazy val liquibaseSlickCodegen: TaskKey[File] = TaskKey(
      "liquibase-slick-codegen",
      "Generate Slick database schema code from Liquibase changelog file"
    )

    lazy val liquibaseSlickCodegenOutputPackage: SettingKey[String] =
      SettingKey(
        "liquibase-slick-codegen-output-package",
        "Package the generated Slick database schema code should be placed in"
      )
    lazy val liquibaseSlickCodegenOutputClass: SettingKey[String] = SettingKey(
      "liquibase-slick-codegen-output-class",
      "The class name for the generated Slick database schema code"
    )

    lazy val liquibaseSlickCodegenProfile: SettingKey[JdbcProfile] = SettingKey(
      "liquibase-slick-codegen-profile",
      "The Slick database profile for the generated Slick database schema code"
    )

    lazy val liquibaseSlickCodegenSourceCodeGeneratorFactory
        : SettingKey[Model => SourceCodeGenerator] = SettingKey(
      "liquibase-slick-codegen-source-code-generator-factory",
      "The factory for the SourceCodeGenerator to use to generate Slick database schema code"
    )
  }
}

abstract class AbstractSbtLiquibaseSlickCodegenPlugin extends AutoPlugin {
  import AbstractSbtLiquibaseSlickCodegenPlugin._

  private[this] val random = new Random(new SecureRandom())

  val slickDriver: JdbcProfile

  val jdbcDriver: String = classOf[org.h2.Driver].getName
  val dbName: String = "sbt_liquibase_slick_codegen"
  val username: String = ""
  val password: String = random.nextString(random.nextInt(25)) // generate a random password
  val cacheFileName: String = "sbt_liquibase-slick_codegen_cache"

  override def requires: Plugins = SbtLiquibase

  val autoImport: AutoImport
  import autoImport._

  private[this] lazy val logger = Def.task[Logger] {
    streams.value.log
  }

  private[this] lazy val slickCodegenDir = Def.setting[File] {
    (scalaSource in Compile).value
  }

  private[this] lazy val slickCodegenFileName = Def.setting[String] {
    s"${liquibaseSlickCodegenOutputClass.value}.scala"
  }

  private[this] lazy val slickCodegenFile = Def.setting[File] {
    slickCodegenDir.value / liquibaseSlickCodegenOutputPackage.value / slickCodegenFileName.value
  }

  private[this] lazy val cacheDir = Def.setting[File] {
    // create cache in subfolders corresponding to configured package and class so that cache is invalidated if either is changed
    target.value / cacheFileName / liquibaseSlickCodegenOutputPackage.value / liquibaseSlickCodegenOutputClass.value
  }

  /**
    * Compares the specified cache with the specified input file and output file.
    * Updates the cache if the specified cache doesn't exist, the input file is modified,
    * or the output file doesn't exist.
    *
    * @param cacheFile
    *                  The cache file to check.
    * @param inputFile
    *                  The input file to compare against.
    * @param outputFile
    *                   The output file to compare against.
    * @return `true` if the specified cache exists, the input file is unmodified, and the output file exists,
    *         `false` otherwise.
    */
  private[this] def checkAndUpdateCache(
      cacheFile: File,
      inputFile: File,
      outputFile: File
  ): Boolean = {
    var success = true

    FileFunction.cached(cacheFile, FilesInfo.lastModified, FilesInfo.exists)(
      _ => {
        success = false
        Set(outputFile)
      }
    )(Set(inputFile))

    success
  }

  private[this] lazy val requireLiquibaseSlickCodegen = Def.taskDyn[Boolean] {
    val required = !checkAndUpdateCache(
      cacheDir.value,
      liquibaseChangelog.value,
      slickCodegenFile.value
    )

    if (required) {
      // because it is impossible to check the cache without updating it, the cache may end up in a corrupted state if
      // the cache update is not performed (eg. interrupted due to RuntimeException).
      // Workaround: delete the cache after comparison, if liquibase-slick-codegen is required.
      Def.task {
        deleteCache.value
        required
      }
    } else {
      Def.task {
        required
      }
    }
  }

  private[this] lazy val updateCache = Def.task[Unit] {
    // delete the cache to force an update every time
    deleteCache.value

    checkAndUpdateCache(
      cacheDir.value,
      liquibaseChangelog.value,
      slickCodegenFile.value
    )
  }

  private[this] lazy val deleteCache = Def.task[Unit] {
    val cacheDirValue = cacheDir.value
    if (cacheDirValue.exists()) {
      Files.walkFileTree(
        cacheDirValue.toPath,
        new SimpleFileVisitor[Path] {
          override def visitFile(
              file: Path,
              attrs: BasicFileAttributes
          ): FileVisitResult = {
            val result = super.visitFile(file, attrs)
            Files.delete(file)
            result
          }

          override def postVisitDirectory(
              dir: Path,
              exc: IOException
          ): FileVisitResult = {
            val result = super.postVisitDirectory(dir, exc)
            Files.delete(dir)
            result
          }
        }
      )
    }
  }

  private[this] implicit val ec: ExecutionContextExecutor =
    ExecutionContext.global

  private[this] lazy val slickCodegen = Def.task[Future[Unit]] {
    // prevent Slick Codegen from creating database if it doesn't exist
    val url = s"${(liquibaseUrl in LiquibaseSlickCodegen).value};IFEXISTS=TRUE"

    val dbFactory = slickDriver.api.Database
    val db = dbFactory.forURL(
      url,
      username,
      password,
      driver = jdbcDriver,
      keepAliveConnection = true
    )

    val modelAction = slickDriver.createModel().withPinnedSession
    val modelFuture = db.run(modelAction)

    val slickCodegenDirValue = slickCodegenDir.value
    val slickCodegenFileNameValue = slickCodegenFileName.value

    val codegenFuture = modelFuture.map(
      model =>
        liquibaseSlickCodegenSourceCodeGeneratorFactory.value.apply(model)
    )
    codegenFuture.onComplete {
      case Success(_) =>
        logger.value.info(
          s"Slick Codegen: Successfully generated database schema code at ${slickCodegenFile.value.getPath}"
        )
      case Failure(t) => logger.value.error(t.getStackTraceString)
    }
    codegenFuture.onComplete(_ => db.close)
    codegenFuture.map(
      codegen =>
        codegen.writeToFile(
          liquibaseSlickCodegenProfile.value.getClass.singletonUnderlyingClassName,
          slickCodegenDirValue.getPath,
          liquibaseSlickCodegenOutputPackage.value,
          liquibaseSlickCodegenOutputClass.value,
          slickCodegenFileNameValue
        )
    )
  }

  private[this] implicit class ClassUtils(clazz: Class[_]) {
    import ClassUtils._

    // removes the trailing dollar sign from a Singleton Object's class name to obtain the actual underlying Class name
    def singletonUnderlyingClassName: String =
      TrailingDollar.matcher(clazz.getName).replaceFirst("")
  }

  object ClassUtils {
    private val TrailingDollar = "\\$$".r.pattern
  }

  override lazy val projectSettings: Seq[Setting[_]] =
    inConfig(LiquibaseSlickCodegen)(
      SbtLiquibase.liquibaseBaseSettings(LiquibaseSlickCodegen) ++
        Seq(
          liquibaseDriver := jdbcDriver,
          // create an embedded database with Auto Mixed Mode
          // (Server is required for Liquibase and Slick Codegen to share a database, as both do not share the same Class Loader)
          // persist database to a file (Auto Mixed Mode cannot be used with in-memory databases)
          liquibaseUrl := s"jdbc:h2:file:${target.value.getPath}/$dbName;AUTO_SERVER=TRUE;DATABASE_TO_UPPER=FALSE",
          liquibaseUsername := username,
          liquibasePassword := password,
          liquibaseChangelog := (liquibaseChangelog in Compile).value,
          dependencyClasspath := (dependencyClasspath in Compile).value,
          // Delete pre-existing embedded database before performing update
          liquibaseUpdate := liquibaseUpdate
            .dependsOn(Def.task[Unit] {
              logger.value.info("Performing cleanup:")
              DeleteDbFiles.execute(target.value.getPath, dbName, false)
            })
            .value
        )
    ) ++
      Seq(
        libraryDependencies ++= Seq(
          // read version of Slick library from Slick Codegen's Manifest (Slick's Manifest does not have Implementation-Version)
          "com.typesafe.slick" %% "slick" % classOf[
            slick.codegen.SourceCodeGenerator
          ].getPackage.getImplementationVersion,
          // read version of H2 library from Manifest
          "com.h2database" % "h2" % classOf[org.h2.Driver].getPackage.getImplementationVersion
        ),
        // stub out required Liquibase settings to make them optional
        liquibaseDriver := "",
        liquibaseUrl := "",
        liquibaseUsername := "",
        liquibasePassword := "",
        liquibaseSlickCodegenOutputClass := "Tables",
        liquibaseSlickCodegenProfile := slickDriver,
        // default to bundled SourceCodeGenerator
        liquibaseSlickCodegenSourceCodeGeneratorFactory := SourceCodeGenerator.apply,
        liquibaseSlickCodegen := Def.taskDyn {
          (liquibaseUpdate in LiquibaseSlickCodegen).value

          Def.task {
            Def.taskDyn {
              Await.result(
                Await.ready(slickCodegen.value, Duration.Inf).map {
                  // update cache with newly generated database schema code file
                  _ =>
                    updateCache
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
          } else {
            compile in Compile
          }
        }.value
      )
}
