# Spotlight

A Gradle plugin that and makes managing `settings.gradle(.kts)` for large projects easier:

* Moves your `include` directives out of `settings.gradle(.kts)` into a flat text file
* Inspired by tools like [Focus][focus] and [dependency-explorer][dependency-explorer], this plugin lets you easily load subsets of your project into the IDE

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

To load a subset of your project in the IDE, list the projects you want to work on in `gradle/target-projects.txt`:

```
:feature-a
...
```

On next sync, only `:feature-a` and other projects you choose will be be loaded. The transitive dependencies of these target projects are also loaded (because sync will fail otherwise), but you do not need to identify them yourself. The plugin statically parses your `build.gradle(.kts)` files at the start of the build to determine the dependency graph. 

## Differences from Focus
Unlike [Focus][focus], which configures your gradle project to select which projects get synced using the `:createFocusSettings` task provided by the plugin, Spotlight relies on parsing of your buildscripts with regexes to compute the dependency graph, which is much faster.

Spotlight does not included any Gradle tasks to manage your `all-projects.txt` or `target-projects.txt` lists, and instead relies on external tooling (IDE plugin, shell command) to avoid invoking Gradle.

## Differences from dependency-explorer
dependency-explorer runs completely outside of Gradle, and users must rerun the build graph query whenever their dependency graph changes to avoid errors during IDE sync. Both `dependency-explorer` and Spotlight use similar approaches for parsing the build graph, but Spotlight just does it automatically inside a settings plugin.

## Limitations
Dependency declarations must be formatted to each be on a single line. The following example would not be parsed by the regexes used:

```groovy
dependencies {
  implementation(
    project(
      ':feature-a'
    )
  )
}
```

The `projectDir` for projects listed in `all-projects.txt` cannot be relocated.

It is not uncommon for a conventions plugin setup to add a default set of utilities/testing project dependencies to each project in a build. Spotlight is not able to detect these implicit dependencies added to your projects by other build logic or plugins because those do not appear in your buildscripts. As a workaround for this, those implicit project dependencies may be listed in `gradle/implicit-projects.txt` and will always be included in the build.

Adding implicit dependencies based on module structure logic is not supported.

[focus]: https://github.com/dropbox/focus
[dependency-explorer]: https://github.com/square/dependency-explorer