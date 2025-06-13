# Changelog

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