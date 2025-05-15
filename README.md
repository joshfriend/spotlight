# Spotlight

A Gradle plugin that and makes managing `settings.gradle(.kts)` for large projects easier:

* Moves your `include` directives out of `settings.gradle(.kts)` into a flat text file
* Inspired by tools like [Focus][focus] and [dependency-explorer][dependency-explorer], this plugin lets you easily load subsets of your project into the IDE
* Only loads the minimum required list of projects required to run your requested build tasks

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

A settings DSL is provided to configure some pattern matching rules based on project paths or buildscript contents to implicitly add other projects:

```groovy
// settings.gradle(.kts)
spotlight {
  // Add :tsunami-sea as a dependency to all projects with a path matching ":rotoscope:.*"
  // The pattern strings are regexes
  whenProjectPathMatches(":rotoscope:.*") {
    alsoInclude ":tsunami-sea"
  }
  // Add :eternal-blue to any project applying the `com.example.android` convention plugin
  whenBuildscriptMatches("id 'com.example.android'") {
    alsoInclude ":eternal-blue"
    alsoInclude ":singles-collection" // multiple includes can be given for a pattern
  }
}
```

Implicit rules apply to all Gradle invocations (sync and task execution).

## Differences from Focus
Unlike [Focus][focus], which configures your gradle project to select which projects get synced using the `:createFocusSettings` task provided by the plugin, Spotlight relies on parsing of your buildscripts with regexes to compute the dependency graph, which is much faster.

Spotlight does not included any Gradle tasks to manage your `all-projects.txt` or `ide-projects.txt` lists, and instead relies on external tooling (IDE plugin, shell command) to avoid invoking Gradle.

## Differences from dependency-explorer
dependency-explorer runs completely outside of Gradle, and users must rerun the build graph query whenever their dependency graph changes to avoid errors during IDE sync. Both `dependency-explorer` and Spotlight use similar approaches for parsing the build graph, but Spotlight just does it automatically inside a settings plugin.

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

[focus]: https://github.com/dropbox/focus
[dependency-explorer]: https://github.com/square/dependency-explorer
[taskRequests]: https://docs.gradle.org/current/javadoc/org/gradle/StartParameter.html#getTaskRequests()
[projectDir]: https://docs.gradle.org/current/javadoc/org/gradle/StartParameter.html#getProjectDir()
[project-dir-flag]: https://docs.gradle.org/current/userguide/command_line_interface.html#sec:environment_options
[cross-project-configuration]: https://github.com/joshfriend/gradle-best-practices-plugin?tab=readme-ov-file#instances-of-cross-project-configuration
[herding-elephants]: https://developer.squareup.com/blog/herding-elephants/
[stampeding-elephants]: https://developer.squareup.com/blog/stampeding-elephants/
[typesafe-project-accessors]: https://docs.gradle.org/current/userguide/declaring_dependencies_basics.html#sec:type-safe-project-accessors