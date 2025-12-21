# Spotlight Buildscript AST Parser - Kotlin

Optional Kotlin PSI parser for Spotlight buildscript utilities.

## Overview

This module provides a PSI-based parser for Kotlin build scripts (`build.gradle.kts`). It uses the Kotlin compiler's PSI (Program Structure Interface) to provide more accurate parsing than regex-based approaches.

## Usage

To enable Kotlin PSI parsing, add this dependency to your project:

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.fueledbycaffeine.spotlight:buildscript-ast-kotlin:VERSION")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'com.fueledbycaffeine.spotlight:buildscript-ast-kotlin:VERSION'
}
```

## Service Provider Interface

This module implements the `BuildScriptParserProvider` service provider interface, allowing the parser to be automatically discovered via Java's `ServiceLoader` mechanism. When this module is on the classpath, the Kotlin PSI parser will automatically be used for `.gradle.kts` files when AST parsing is configured (via `ParsingConfiguration.AST`).

The Kotlin parser registers with priority 100, making it preferred over the regex parser (priority 0) when available.

## Dependencies

This module depends on:
- `buildscript-utils` - Core parsing interfaces and utilities
- `kotlin-compiler-embeddable` - Kotlin compiler for PSI parsing

## License

MIT License - See LICENSE file in the root of the repository
