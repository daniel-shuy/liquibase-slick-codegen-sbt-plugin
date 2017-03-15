# liquibase-slick-codegen-sbt-plugin
A SBT plugin that uses [sbt-liquibase](https://github.com/sbtliquibase/sbt-liquibase-plugin) and [Slick Codegen](http://slick.lightbend.com/doc/3.1.1/code-generation.html) to generate Slick database schema code from a Liquibase changelog file.

The database schema code is generated in the Source folder, in the configured package, as `Tables.scala` (unfortunately, the file name is not configurable as it is hardcoded in Slick Codegen).

The plugin attaches itself to the `compile` phase, and will run before the compilation takes place (the generated `Tables.scala` will be compiled as well).
It is skipped on subsequent runs if the Liquibase changelog file hasn't been modified, and the Slick database schema code file hasn't been deleted.

## Installation

Since the plugin hasn't been published, you will have to checkout and build the project yourself:
1. Clone/Checkout Repository: `git clone https://github.com/daniel-shuy/liquibase-slick-codegen-sbt-plugin.git`
2. Build the project: `sbt compile`
3. Publish the JAR file to your local Ivy cache: `sbt publishLocal`

## Usage

### Step 1: Include the plugin in your build

Add the following to your `project/plugins.sbt`:

    addSbtPlugin("com.github.daniel-shuy" % "sbt-liquibase-slick-codegen" % "0.1.0-SNAPSHOT")

### Step 2: Enable the plugin for your project

Add the following to your `build.sbt`:

    enablePlugins(SbtLiquibaseSlickCodegen)

### Step 3: Configure project settings for the plugin

Minimal example:

    enablePlugins(SbtLiquibaseSlickCodegen)
    
    liquibaseSlickCodegenOutputPackage := "com.foo.bar"

This will create the Slick database schema code as `com.foo.bar.Tables.scala`

## Settings

| Setting                            | Description                                                                                                     |
| ---------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| liquibaseChangelog                 | __Optional__. Full path to your Liquibase changelog file. This defaults to 'src/main/migrations/changelog.xml'. |
| liquibaseSlickCodegenOutputPackage | __Required__. Package the generated Slick database schema code should be created in.                            |

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
When using this plugin with Play Framework, remember to configure `liquibaseChangelog` to point to the correct path, eg.

    liquibaseChangelog := file("conf/migrations/changelog.xml")

Since we are using `Liquibase` instead of `Play Slick Evolutions`, it is recommended to use `Slick` standalone, instead of the `Play-Slick` module.

### Multiple Liquibase changelog files
You can have multiple Liquibase changelog files, then [include](http://www.liquibase.org/documentation/include.html) all of them in a "master" changelog file, then configure `liquibaseChangelog` to point to it.
