# Spotlight Buildscript AST Parser - Groovy

Optional Groovy AST parser for Spotlight buildscript utilities.

## Overview

This module provides an AST-based parser for Groovy build scripts (`build.gradle`). It uses the Groovy compiler's AST capabilities to provide more accurate parsing than regex-based approaches.

## Usage

To enable Groovy AST parsing, add this dependency to your project:

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.fueledbycaffeine.spotlight:buildscript-ast-groovy:VERSION")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'com.fueledbycaffeine.spotlight:buildscript-ast-groovy:VERSION'
}
```

## Service Provider Interface

This module implements the `BuildScriptParserProvider` service provider interface, allowing the parser to be automatically discovered via Java's `ServiceLoader` mechanism. When this module is on the classpath, the Groovy AST parser will automatically be used for `.gradle` files when AST parsing is configured (via `ParsingConfiguration.AST`).

The Groovy parser registers with priority 100, making it preferred over the regex parser (priority 0) when available.

## Dependencies

This module depends on:
- `buildscript-utils` - Core parsing interfaces and utilities
- `groovy` - Groovy compiler for AST parsing

## License

MIT License - See LICENSE file in the root of the repository
