# Changelog

### 1.5.6
* IDE plugin reads list of projects from spotlight gradle plugin model builder after sync completes for more accurate project count and indexing settings.

### 1.5.5
* Add `Settings.applySpotlightConfiguration()` extension to force configuration if Spotlight is applied within another `settingsEvaluated {}` callback

### 1.5.4
* Added new `buildscript-capture-rule` rule type that can use regex substitutions to dynamically include projects referenced in a buildscript via non-standard methods.

### 1.5.3
* Removed `SpotlightBuildService`
* IDE plugin add/remove actions now work in the "Project Files" view
* `:fixAllProjectsFiles` task now moves `include`s from `settings.gradle(.kts)` to `all-projects.txt`

### 1.5.2
* Add custom fields to Develocity build scans ("Spotlight Enabled" and "Spotlight Project Count")

### 1.5.1
* Fixed the IDE plugin editor notification not showing on files from unloaded projects

### 1.5.0
* Type-safe accessor inference is always enabled with what was formerly `FULL` mode. There is no longer any practical penalty for using it this way so options to configure it have been eliminated.
* `:checkAllProjectsList` now validates that `settings.gradle(.kts)` does not contain any `include` statements
* `:checkAllProjectsList` now ensures all projects discovered via dependency graph are listed
* `:checkAllProjectsList` now validates that all listed projects have build files
* Renamed task `:sortAllProjectsList` to `:fixAllProjectsList`
* Auto-fix more issues with `:fixAllProjectsList` (removes invalid projects, adds missing projects, and sorts alphabetically)
* IDE plugin add/remove project actions have better path handling and validation

### 1.4.1
* Fix incorrect line separator used in `:sortAllProjectsList`

### 1.4.0
* Encapsulate all buildscript parsing I/O in a `ValueSource` and remove configuration cache hidden workarounds
* Publish plugin artifact with `org.gradle.plugin.api-version` attribute, current minimum Gradle version is 8.8
* Add `:checkAllProjectsList` task to validate `all-projects.txt` (sorting)
* Add `:sortAllProjectsList` task to automatically sort `all-projects.txt`

### 1.3.4
* Update Okio dependency to 3.16.2

### 1.3.3
* Revert support for any `*.gradle(.kts)` when looking for buildscript in a project

### 1.3.2
* Fall back to any `*.gradle(.kts)` when looking for buildscript in a project

### 1.3.1
* Fix a bug where running something like `./gradlew clean :foo:bar` would not include all projects as required by global task request `clean`
* Support glob patterns in `ide-projects.txt`

### 1.3.0
* Exclude unused projects from IDE indexing
* IDE plugin now understands `spotlight-rules.json` and computes transitively included projects.
* `spotlight-rules.json` format change. Specify rules under the `implicitRules` object key instead of using a toplevel array. The old format is still read as a fallback for now.

### 1.2.3
* Add unsynced project notification banner to IDE plugin
* Add statusbar widget that links to `ide-projects.txt` to IDE plugin

### 1.2.2
* `targetsOverride` can handle an empty value
* Tweaks to `BuildGraph` API

### 1.2.1
* Removed slow sync warning

### 1.2.0
* Fix mistaking gradle APIs like `subprojects` for type-safe project accessors
* Fix `DependencyHandler` wrappers like `platform` and `testFixtures` breaking parsing
* Add an extra decimal

### 1.1
* Fix `STRICT` mode type-safe accessor mode not matching any projects

### 1.0
* Move implicit rules config to `gradle/spotlight-rules.json`
* Improve compatibility with composite builds
* Ensure configuration cache can be invalidated for `-p` invocations when projects are added or removed

### 0.9
* Add `SpotlightBuildService` to provide info about included projects to other build logic
* Fix strings in buildscripts containing "projects." were being parsed as type-safe project accessors
* Fix root directory being captured in configuration cache

### 0.8
* Add a "strict" inference mode for type-safe project accessors
* Minimize the list of Configuration Cache inputs

### 0.7
* Remove DSL configuration for all-projects/ide-projects lists. They will just live inside `gradle/`.
* Add DSL option to indicate if you use type-safe project accessors.
* Avoid capturing all-projects.txt in configuration cache when possible. When type-safe accessors are enabled, all-projects.txt is always captured.

### 0.6
* Implicitly include parent projects of the targets being included to match Gradle's behavior
* BFS algorithm implementation optimizations

### 0.5
* Fixed parsing of type-safe project accessors

### 0.4
* Fixes for type-safe project accessors
* Adds a flag to disable the plugin (`spotlight.enabled` set to `false` in either gradle or system properties)
* Adds a very basic class that will compute the graph edges of your buildscripts project dependencies

### 0.3
* Fixes for type-safe project accessors

### 0.2
* Fixes for type-safe project accessors

### 0.1
* Prototype release
