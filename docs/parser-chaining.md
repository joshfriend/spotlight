# Parser Chaining with ADDITIVE Mode

## Overview

Spotlight's parser architecture supports configurable parser chaining, allowing consumers to add custom parsing logic on top of existing parsers without replacing them entirely.

## Parser Modes

### REPLACE Mode (Default)
- **Behavior**: This parser replaces lower-priority parsers. When found, the search stops.
- **Use Case**: Standard parsers (Groovy AST, Kotlin PSI, Regex) that provide complete parsing.
- **Priority**: Higher priority REPLACE parsers are selected over lower priority ones.

### ADDITIVE Mode
- **Behavior**: This parser supplements other parsers. When found, the search continues to find additional parsers.
- **Use Case**: Custom parsers that add special logic on top of existing parsers.
- **Priority**: Higher priority ADDITIVE parsers run first, then lower priority parsers.

## How It Works

1. **Parser Registry** loads all `BuildScriptParserProvider` implementations via Java's ServiceLoader
2. Providers are sorted by **priority** (highest first)
3. For each provider in order:
   - If it returns a parser:
     - Add parser to the chain
     - If mode is **REPLACE**: stop searching
     - If mode is **ADDITIVE**: continue to next provider
4. All collected parsers are executed and their results are **merged** (duplicates removed)

## Creating a Custom ADDITIVE Parser

### Step 1: Implement BuildScriptParserProvider

```kotlin
package com.example.custom

import com.fueledbycaffeine.spotlight.buildscript.parser.BuildScriptParserProvider
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildScriptParser
import com.fueledbycaffeine.spotlight.buildscript.parser.ParserMode
import java.nio.file.Path
import kotlin.io.path.name

class CustomParserProvider : BuildScriptParserProvider {
    override fun getParser(buildFilePath: Path): BuildScriptParser? {
        // Only provide custom parsing for specific files
        return if (buildFilePath.name == "build.gradle" && shouldApplyCustomLogic(buildFilePath)) {
            CustomParser()
        } else {
            null
        }
    }
    
    override fun getPriority(): Int = 200 // Higher than AST parsers (100)
    
    override fun getMode(): ParserMode = ParserMode.ADDITIVE // Don't replace existing parsers
    
    private fun shouldApplyCustomLogic(path: Path): Boolean {
        // Your logic to determine if custom parsing should apply
        return path.toString().contains("special-module")
    }
}
```

### Step 2: Implement Your Custom Parser

```kotlin
package com.example.custom

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildScriptParser
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule

class CustomParser : BuildScriptParser {
    override fun parse(project: GradlePath, rules: Set<DependencyRule>): Set<GradlePath> {
        // Your custom parsing logic here
        // This will be executed IN ADDITION TO the AST parser
        return setOf(
            // Add extra dependencies detected by your custom logic
            GradlePath(project.root, ":custom-dependency")
        )
    }
}
```

### Step 3: Register via ServiceLoader

Create file: `src/main/resources/META-INF/services/com.fueledbycaffeine.spotlight.buildscript.parser.BuildScriptParserProvider`

```
com.example.custom.CustomParserProvider
```

## Example Use Case

**Scenario**: You have special build files in a `feature-modules/` directory that use a custom DSL for declaring dependencies. You want to:
1. Keep the standard Groovy AST parsing for normal `project()` calls
2. Add custom logic to parse your DSL syntax

**Solution**: Create an ADDITIVE parser with priority 200 that:
- Matches files in `feature-modules/` directory
- Parses your custom DSL
- Returns additional dependencies found
- Lets the Groovy AST parser (priority 100) also run and find standard dependencies

The result is a **merged set** of dependencies from both parsers!

## Priority Guidelines

- **Standard parsers**: 0-100
  - Regex fallback: 0
  - AST/PSI parsers: 100
- **Custom parsers**: 100+
  - ADDITIVE supplements: 101-999
  - REPLACE overrides: 1000+

## Benefits

1. **Non-intrusive**: Add custom logic without forking or modifying Spotlight
2. **Composable**: Multiple ADDITIVE parsers can work together
3. **Flexible**: Full access to build file path for conditional logic
4. **Safe**: Existing parsers continue to work as fallback
