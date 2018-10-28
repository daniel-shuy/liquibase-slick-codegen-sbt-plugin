# liquibase-slick-codegen-sbt-plugin

[ ![Download](https://api.bintray.com/packages/daniel-shuy/sbt-plugins/sbt-liquibase-slick-codegen/images/download.svg) ](https://bintray.com/daniel-shuy/sbt-plugins/sbt-liquibase-slick-codegen/_latestVersion)
[![Build Status](https://travis-ci.org/daniel-shuy/liquibase-slick-codegen-sbt-plugin.svg?branch=master)](https://travis-ci.org/daniel-shuy/liquibase-slick-codegen-sbt-plugin)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/840edcbf1cd3464ea1d4597362ad7588)](https://www.codacy.com/app/daniel-shuy/liquibase-slick-codegen-sbt-plugin?utm_source=github.com&utm_medium=referral&utm_content=daniel-shuy/liquibase-slick-codegen-sbt-plugin&utm_campaign=badger)

| Plugin Version | SBT Version | Slick Version |
| -------------- | ----------- | ------------- |
| 0.1.x          | 0.13.x      | 3.x.x         |

A SBT plugin that uses [sbt-liquibase](https://github.com/sbtliquibase/sbt-liquibase-plugin) and [Slick Codegen](http://slick.lightbend.com/doc/3.1.1/code-generation.html) to generate Slick database schema code from a Liquibase changelog file.

The database schema code is generated in the Source folder (because the generated Slick database schema code should be under version control), in the configured package, with the configured class name.

The plugin attaches itself to the `compile` phase, and will run before the compilation takes place (the generated Slick database schema code will be compiled as well).
It is skipped on subsequent runs if the Liquibase changelog file hasn't been modified, and the Slick database schema code file hasn't been deleted.

__Warning:__ This plugin currently depends on an experimental feature in Slick Codegen. This plugin uses the [H2 Database](http://www.h2database.com/html/main.html) to generate the Slick database schema code. Currently, using a different database profile from the one used to generate the database schema code is considered experimental and is not officially supported by Slick (see http://slick.lightbend.com/doc/3.1.1/code-generation.html#generated-code for more information).

## Limitations
Because the plugin uses the Liquibase changelog file to create tables in a temporary H2 database, there are some limitations when configuring the Liquibase changelog file. If possible, avoid using them. If not, refer below for the workarounds. Make sure to test the generated Slick database schema code thoroughly.

### Database-specific Preconditions
If a [\<dbms> precondition](http://www.liquibase.org/documentation/preconditions.html#ltdbmsgt) with a database other than H2 is used, add `<dbms type="h2" />` as well.

### Unsupported Changes
If a [change](http://www.liquibase.org/documentation/changes/index.html) that isn't supported by H2 (eg. [\<renameView>](http://www.liquibase.org/documentation/changes/rename_view.html)) is used, add corresponding [SQL](http://www.liquibase.org/documentation/changes/sql.html) or a combination of changes to achieve the same result, with the `<dbms type="h2" />` precondition.

Eg. for `<renameView>`, use [\<dropView>](http://www.liquibase.org/documentation/changes/drop_view.html) and [\<createView>](http://www.liquibase.org/documentation/changes/create_view.html) to achieve the same result.

### Database-specific SQL
If database-specific SQL is used with [\<sql>](http://www.liquibase.org/documentation/changes/sql.html), add a [\<dbms> precondition](http://www.liquibase.org/documentation/preconditions.html#ltdbmsgt) to limit it to the database type that supports it, then add corresponding SQL for H2 that achieves the same result in a `<sql>` change, with the `<dbms type="h2" />` precondition.

## Usage

### Step 1: Include the plugin in your build

Add the following to your `project/plugins.sbt`:
```scala
addSbtPlugin("com.github.daniel-shuy" % "sbt-liquibase-slick-codegen" % "0.1.1")
```

### Step 2: Enable the plugin for your project

Add the following to your `build.sbt`:
```scala
enablePlugins(SbtLiquibaseSlickCodegen)
```

### Step 3: Create the Liquibase changelog file

See http://www.liquibase.org/documentation/databasechangelog.html

### Step 4: Configure project settings for the plugin

See [Settings](#settings).

Minimal example:
```scala
enablePlugins(SbtLiquibaseSlickCodegen)

liquibaseSlickCodegenOutputPackage := "com.foo.bar"
liquibaseSlickCodegenOutputClass := "Tables"
```
This will create the Slick database schema code as `com.foo.bar.Tables.scala`

### Step 5: Execute the plugin

Run `sbt compile` or `sbt liquibaseSlickCodegen` to generate the Slick database schema code.

## Settings

| Setting                                     | Type                         | Description                                                                                                                                                                                                                         |
| ------------------------------------------- | ---------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| liquibaseChangelog                          | File                         | __Optional__. Your Liquibase changelog file. Defaults to `src/main/migrations/changelog.xml`.                                                                                                                          |
| liquibaseSlickCodegenOutputPackage          | String                       | __Required__. Package the generated Slick database schema code should be created in.                                                                                                                                                |
| liquibaseSlickCodegenOutputClass            | String                       | __Optional__. The class name for the generated Slick database schema code, without the `.scala` extension. Defaults to `Tables`.                                                                                                    |
| liquibaseSlickCodegenProfile                | JdbcProfile                  | __Optional__. The Slick database profile for the generated Slick database schema code. __This should be substituted with the Slick driver implementation for the database you intend to use.__ Defaults to `slick.driver.H2Driver`. |
| liquibaseSlickCodegenSourceGeneratorFactory | Model => SourceCodeGenerator | __Optional__. The factory for the SourceCodeGenerator implementation. See [Slick Codegen customization](#slick-codegen-customization). Defaults to the bundled SourceCodeGenerator.                                                 |

## Tasks

| Task                  | Description                                                                                                                                    |
| --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| liquibaseSlickCodegen | Forces the plugin to run, regardless of whether the Liquibase changelog file has been modified, or the Slick database schema code file exists. |

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
You can still perform [Slick Codegen customizations](http://slick.lightbend.com/doc/3.1.1/code-generation.html#customization) with this plugin.

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

### SBT 1.x
This project depends on [sbt-liquibase](https://github.com/sbtliquibase/sbt-liquibase-plugin), which doesn't support SBT 1.x as of yet.

If you would like this project to support SBT 1.x, please help to upvote this Issue (https://github.com/sbtliquibase/sbt-liquibase-plugin/issues/17), or better yet, help open a Pull Request to update it to support SBT 1.x.

## Licence

Copyright 2017 Daniel Shuy

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
