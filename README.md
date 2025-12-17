# Spotlight

[![Maven Central Version](https://img.shields.io/maven-central/v/com.fueledbycaffeine.spotlight/spotlight-gradle-plugin)](https://central.sonatype.com/artifact/com.fueledbycaffeine.spotlight/spotlight-gradle-plugin)
[![Gradle Plugin Portal Version](https://img.shields.io/gradle-plugin-portal/v/com.fueledbycaffeine.spotlight)][plugin-portal-page]
[![IDE Plugin Version](https://img.shields.io/jetbrains/plugin/v/27451)][jb-marketplace-page]
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

A [Gradle plugin][plugin-portal-page] and [IntelliJ plugin][jb-marketplace-page] that make managing `settings.gradle(.kts)` for large projects easier:

* Moves your `include` directives out of `settings.gradle(.kts)` into a flat text file
* Inspired by tools like [Focus][focus] and [dependency-explorer][dependency-explorer], this plugin lets you easily load subsets of your project into the IDE
* Only loads the minimum required list of projects required to run your requested build tasks

> [!TIP]
> Using Spotlight can dramatically decrease your IDE sync times, check out [this blog post][shrinking-elephants] for more info.

## How to use it
Apply the plugin in `settings.gradle(.kts)`:
```groovy
plugins {
  id 'com.fueledbycaffeine.spotlight'
}
```

Remove any `include` statements:

```groovy
// get rid of these!
include ':app'
include ':feature'
```

Move the included project declarations to `gradle/all-projects.txt`

```
:app
:feature-a
:feature-b
# items may be commented out like this
```

### IDE Sync

To load a subset of your project in the IDE, list the projects you want to work on in `gradle/ide-projects.txt`:

```
:feature-a
...
```

On next sync, only `:feature-a` and other projects you choose will be be loaded. The transitive dependencies of these target projects are also loaded (because sync will fail otherwise), but you do not need to identify them yourself. The plugin statically parses your `build.gradle(.kts)` files at the start of the build to determine the dependency graph. 

### Task Execution
Spotlight will infer the projects necessary to include in the build from [the task request list][taskRequests] and [the project directory][projectDir] (specified with the `-p`/`--project-dir` [flags][project-dir-flag]).

For example, running `./gradlew :help` will only add the root project. Running something like `./gradlew :example:assemble` will only add the projects required by the dependency graph of `:example`.

When the `-p`/`--project-dir` flag is used, Spotlight will expand the list of child projects at the specified directory and add the projects required by their dependency graphs to the build. This enables you to run something like `./gradlew -p example check` in a subdirectory.

### Implicit rules
It is not uncommon for a conventions plugin setup to add a default set of utilities/testing project dependencies to each project in a build. By default, Spotlight is not able to detect these implicit dependencies added to your projects by other build logic or plugins because those do not appear in your buildscripts.

A config file option is provided to configure some pattern matching rules based on project paths or buildscript contents to implicitly add other projects:

```json5
// gradle/spotlight-rules.json
{
  "implicitRules": [
    // Add :tsunami-sea as a dependency to all projects with a path matching ":rotoscope:.*"
    // The pattern strings are regexes
    {
      "type": "project-path-match-rule",
      "pattern": ":rotoscope:.*",
      "includedProjects": [
        ":tsunami-sea"
      ]
    },
    // Add :eternal-blue to any project applying the `com.example.android` convention plugin
    {
      "type": "buildscript-match-rule",
      "pattern": "id 'com.example.android'",
      "includedProjects": [
        ":eternal-blue",
        ":singles-collection"
        // multiple includes can be given for a pattern
      ]
    }
  ]
}
```

Implicit rules apply to all Gradle invocations (sync and task execution).

If you are using the `buildscript-utils` package by itself, you can read this rules list using the `SpotlightRulesList` class.

### Type-safe Project Accessors
By default, Spotlight attempts to do a basic "strict" mapping of any [type-safe project accessors][typesafe-project-accessors] used to project path. This assumes that your project paths are all lowercased and use kebab-case for naming convention.

If your project does not follow this convention and can't be updated to follow it, you can enable full mapping of type-safe accessors:

```groovy
// settings.gradle(.kts)
import com.fueledbycaffeine.spotlight.dsl.TypeSafeAccessorInference

spotlight {
  typeSafeAccessorInference TypeSafeAccessorInference.FULL
}
```

> [!IMPORTANT]
> Don't use type-safe project accessors with Kotlin buildscripts. Doing so [causes more configuration cache misses][kts-accessors-bad] when Spotlight is being used.

> [!TIP]
> If your project does not use type-safe project accessors at all, you can disable this inference entirely with `DISABLED` mode.

### Useful Tasks
Spotlight provides several tasks for managing its config files:
* `./gradlew :checkAllProjectsList` - Check that the `all-projects.txt` file is correct
  * Verifies alphabetic sorting
  * Verifies that `settings.gradle(.kts)` does not have any `include`s
* `./gradlew :sortAllProjectsList` - Sort the `all-projects.txt` file

## Differences from Focus
Unlike [Focus][focus], which configures your gradle project to select which projects get synced using the `:createFocusSettings` task provided by the plugin, Spotlight relies on parsing of your buildscripts with regexes to compute the dependency graph, which is much faster.

Spotlight does not include any Gradle tasks to manage your `all-projects.txt` or `ide-projects.txt` lists, and instead relies on external tooling ([IDE plugin][jb-marketplace-page], shell command) to avoid invoking Gradle.

## Differences from dependency-explorer
dependency-explorer runs completely outside Gradle, and users must rerun the build graph query whenever their dependency graph changes to avoid errors during IDE sync. Both `dependency-explorer` and Spotlight use similar approaches for parsing the build graph, but Spotlight just does it automatically inside a settings plugin.

## Limitations
This plugin assumes you have a "nice" Gradle build that doesn't do [cross-project configuration][cross-project-configuration]. Please read [_"Herding Elephants"_][herding-elephants] and [_"Stampeding Elephants"_][stampeding-elephants] for more thoughts on this topic.

Dependency declarations in buildscripts must be formatted to each be on a single line. The following example would not be parsed by the regexes used:

```groovy
dependencies {
  implementation(
    project(
      ':feature-a'
    )
  )
}
```

The `projectDir` for projects listed in `all-projects.txt` cannot be relocated because the list of all your projects is now a flat text file and not a dynamic script.

You can still add `include`s to `settings.gradle(.kts)` in your build outside of this plugin.

[plugin-portal-page]: https://plugins.gradle.org/plugin/com.fueledbycaffeine.spotlight
[jb-marketplace-page]: https://plugins.jetbrains.com/plugin/27451-spotlight
[focus]: https://github.com/dropbox/focus
[dependency-explorer]: https://github.com/square/dependency-explorer
[taskRequests]: https://docs.gradle.org/current/javadoc/org/gradle/StartParameter.html#getTaskRequests()
[projectDir]: https://docs.gradle.org/current/javadoc/org/gradle/StartParameter.html#getProjectDir()
[project-dir-flag]: https://docs.gradle.org/current/userguide/command_line_interface.html#sec:environment_options
[cross-project-configuration]: https://github.com/joshfriend/gradle-best-practices-plugin?tab=readme-ov-file#instances-of-cross-project-configuration
[herding-elephants]: https://developer.squareup.com/blog/herding-elephants/
[stampeding-elephants]: https://developer.squareup.com/blog/stampeding-elephants/
[shrinking-elephants]: https://engineering.block.xyz/blog/shrinking-elephants
[typesafe-project-accessors]: https://docs.gradle.org/current/userguide/declaring_dependencies_basics.html#sec:type-safe-project-accessors
[kts-accessors-bad]: https://www.zacsweers.dev/dont-use-type-safe-project-accessors-with-kotlin-gradle-dsl/
