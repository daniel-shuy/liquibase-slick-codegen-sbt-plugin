# liquibase-slick-codegen-sbt-plugin
A SBT plugin that uses [sbt-liquibase](https://github.com/sbtliquibase/sbt-liquibase-plugin) and [Slick Codegen](http://slick.lightbend.com/doc/3.1.1/code-generation.html) to generate Slick database schema code from a Liquibase changelog file.

The database schema code is generated in the Source folder, in the configured package, with the configured class name.

The plugin attaches itself to the `compile` phase, and will run before the compilation takes place (the generated Slick database schema code will be compiled as well).
It is skipped on subsequent runs if the Liquibase changelog file hasn't been modified, and the Slick database schema code file hasn't been deleted.

__Warning:__ This plugin currently depends on an experimental feature in Slick Codegen. This plugin uses the [H2 Database](http://www.h2database.com/html/main.html) to generate the Slick database schema code. Currently, using a different database profile from the one used to generate the database schema code is considered experimental and is not officially supported by Slick.

## Installation

Since the plugin hasn't been published, you will have to checkout and build the project yourself:
1. Clone/Checkout Repository: `git clone https://github.com/daniel-shuy/liquibase-slick-codegen-sbt-plugin.git`
2. Build and publish the JAR file to your local Ivy cache: `sbt publishLocal`

## Usage

### Step 1: Include the plugin in your build

Add the following to your `project/plugins.sbt`:
```scala
addSbtPlugin("com.github.daniel-shuy" % "sbt-liquibase-slick-codegen" % "0.1.0-SNAPSHOT")
```

### Step 2: Enable the plugin for your project

Add the following to your `build.sbt`:
```scala
enablePlugins(SbtLiquibaseSlickCodegen)
```

### Step 3: Configure project settings for the plugin

Minimal example:
```scala
enablePlugins(SbtLiquibaseSlickCodegen)

liquibaseSlickCodegenOutputPackage := "com.foo.bar"
liquibaseSlickCodegenOutputClass := "Tables"
```
This will create the Slick database schema code as `com.foo.bar.Tables.scala`

## Settings

| Setting                                     | Description                                                                                                                                                                                                                         |
| ------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| liquibaseChangelog                          | __Optional__. Full path to your Liquibase changelog file. Defaults to `src/main/migrations/changelog.xml`.                                                                                                                          |
| liquibaseSlickCodegenOutputPackage          | __Required__. Package the generated Slick database schema code should be created in.                                                                                                                                                |
| liquibaseSlickCodegenOutputClass            | __Optional__. The class name for the generated Slick database schema code, without the `.scala` extension. Defaults to `Tables`.                                                                                                    |
| liquibaseSlickCodegenProfile                | __Optional__. The Slick database profile for the generated Slick database schema code. __This should be substituted with the Slick driver implementation for the database you intend to use.__ Defaults to `slick.driver.H2Driver`. |
| liquibaseSlickCodegenSourceGeneratorFactory | __Optional__. The factory for the SourceCodeGenerator implementation. See Slick Codegen customization. Defaults to the bundled SourceCodeGenerator.                                                                            |

## Tasks

| Task                    | Description                                                                                                                                    |
| ----------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| liquibase-slick-codegen | Forces the plugin to run, regardless of whether the Liquibase changelog file has been modified, or the Slick database schema code file exists. |

## Notes

### sbt-liquibase
A project that enables the `SbtLiquibaseSlickCodegen` plugin automatically enables the `SbtLiquibase` plugin as well.

The `sbt-liquibase` plugin can still be used as normal alongside `sbt-liquibase-slick-codegen`.

Note that the `liquibaseChangelog` setting is shared among both plugins.


### Play Framework
When using this plugin with [Play Framework](https://www.playframework.com/), remember to configure `liquibaseChangelog` to point to the correct path, eg.
```scala
liquibaseChangelog := file("conf/migrations/changelog.xml")
```

Since we are using [Liquibase](http://www.liquibase.org/) instead of [Play Slick Evolutions](https://www.playframework.com/documentation/latest/Evolutions) for Database Migration, it is recommended to use [Slick](http://slick.lightbend.com/) standalone, instead of the [play-slick](https://www.playframework.com/documentation/latest/PlaySlick) module, so that you are not bound to the version of Slick supported by play-slick.


### Multiple Liquibase changelog files
You can have multiple Liquibase changelog files, then [include](http://www.liquibase.org/documentation/include.html) all of them in a "master" changelog file, then configure `liquibaseChangelog` to point to it.


### Slick Codegen customization
You can still perform [Slick Codegen customizations](http://slick.lightbend.com/doc/3.2.0/code-generation.html#customization) with this plugin.

Create a class in `project` that extends `com.github.daniel.shuy.liquibase.slick.codegen.SourceCodeGenerator`, then override methods to customize Slick Codegen's behavior, eg.

`project/CustomSourceCodeGenerator.scala`:
```scala
import com.github.daniel.shuy.liquibase.slick.codegen.SourceCodeGenerator
import slick.model.Model

class CustomSourceCodeGenerator(model: Model) extends SourceCodeGenerator(model) {
    // override def ...
```

then in `build.sbt`, assign the constructor as the `liquibaseSlickCodegenSourceCodeGeneratorFactory`, eg.
```scala
liquibaseSlickCodegenSourceCodeGeneratorFactory := (model => new CustomSourceCodeGenerator(model))
```
