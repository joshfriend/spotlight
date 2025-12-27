# IDE Plugin Tooling API Integration

This document explains how the Spotlight IDE plugin receives BuildScript parser instances via the Gradle Tooling API during IDE sync.

## Overview

The Spotlight IDE plugin receives actual parser implementation instances during Gradle sync. These parsers are discovered via SPI in the Gradle build and transferred to the IDE, allowing the IDE to parse build scripts directly using the same parser implementations (including AST parsers).

## Key Design: Serializable Parsers

Since the IDE and Gradle build run in separate processes, parser instances must cross a process boundary. This is achieved by making `BuildscriptParser` extend `Serializable`. When a parser instance is transferred via the Tooling API, Java serialization handles the transfer.

```kotlin
public interface BuildscriptParser : Serializable {
  public fun parse(project: GradlePath, rules: Set<DependencyRule>): Set<GradlePath>
}
```

## Architecture

### 1. Tooling API Model (`buildscript-utils`)

**File: `BuildscriptParsersModel.kt`**

Defines the model that transfers parser instances from Gradle to the IDE:

```kotlin
interface BuildscriptParsersModel : Serializable {
  val parsers: Map<String, BuildscriptParser>  // Parser instances keyed by provider class
  val parserInfo: List<ParserInfo>             // Metadata about the parsers
}
```

### 2. Model Builder (Gradle Plugin)

**File: `BuildscriptParsersModelBuilder.kt`**

Discovers parsers via `ServiceLoader` and creates instances to transfer:

```kotlin
class BuildscriptParsersModelBuilder : ToolingModelBuilder {
  override fun buildAll(modelName: String, project: Project): Any {
    // Discover parser providers
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java)
    
    val parsersMap = mutableMapOf<String, BuildscriptParser>()
    for (provider in providers) {
      val parser = provider.getParser(dummyProject)
      if (parser != null) {
        parsersMap[provider::class.java.name] = parser
      }
    }
    
    return BuildscriptParsersModelImpl(parsersMap, parserInfoList)
  }
}
```

### 3. Project Resolver Extension (IDE Plugin)

**File: `SpotlightProjectResolverExtension.kt`**

Requests the model during IDE sync and stores parser instances:

```kotlin
class SpotlightProjectResolverExtension : AbstractProjectResolverExtension() {
  override fun populateProjectExtraModels(
    gradleProject: IdeaProject,
    ideProject: DataNode<ProjectData>
  ) {
    val parsersModel = resolverCtx.getExtraProject(
      null,
      BuildscriptParsersModel::class.java
    )
    
    if (parsersModel != null) {
      ideProject.createChild(
        SpotlightParsersData.KEY,
        SpotlightParsersData(
          parsers = parsersModel.parsers,  // Actual parser instances!
          parserInfo = parsersModel.parserInfo
        )
      )
    }
  }
}
```

### 4. Parsers Service (IDE Plugin)

**File: `SpotlightParsersService.kt`**

Stores parser instances and provides methods to use them:

```kotlin
@Service(Service.Level.PROJECT)
class SpotlightParsersService {
  private val _parsers = MutableStateFlow<Map<String, BuildscriptParser>>(emptyMap())
  
  fun updateParsers(parsers: Map<String, BuildscriptParser>, parserInfo: List<ParserInfo>) {
    _parsers.value = parsers
  }
  
  fun parseProject(project: Project, projectPath: String): Set<String>? {
    val gradlePath = GradlePath(rootDir, projectPath)
    val parser = getParser(gradlePath) ?: return null
    
    return parser.parse(gradlePath, emptySet())  // Direct invocation!
      .map { it.path }
      .toSet()
  }
}
```

## How It Works

1. **During IDE Sync:**
   - The IDE's `SpotlightProjectResolverExtension` requests `BuildscriptParsersModel`
   - Gradle's `BuildscriptParsersModelBuilder` is invoked
   - It uses `ServiceLoader` to discover all `BuildscriptParserProvider` implementations
   - For each provider, it calls `getParser()` to get a parser instance
   - These parser instances (which are Serializable) are packaged into the model
   - Java serialization transfers the parser instances to the IDE process

2. **In the IDE:**
   - The `SpotlightParsersDataService` receives the parser instances
   - It updates `SpotlightParsersService` with the parser map
   - The IDE now has working parser instances it can invoke directly

3. **Parsing Build Scripts:**
   - IDE code calls `SpotlightParsersService.parseProject(project, ":app")`
   - The service looks up the appropriate parser from the map
   - It invokes `parser.parse(gradlePath, rules)` directly
   - The parser runs in the IDE process and returns dependencies

## Parser Serializability

For a parser to work across the process boundary, it must be serializable:

**✅ Works:** Singleton objects and stateless parsers
```kotlin
object RegexBuildscriptParser : BuildscriptParser {
  override fun parse(project: GradlePath, rules: Set<DependencyRule>): Set<GradlePath> {
    // Uses only parameters, no instance state
  }
}
```

**✅ Works:** Parsers with serializable state
```kotlin
data class ConfigurableParser(
  val patterns: List<Regex>  // Regex is Serializable
) : BuildscriptParser {
  override fun parse(...) { ... }
}
```

**❌ Won't work:** Parsers with non-serializable dependencies
```kotlin
class ASTParser(
  private val compiler: KotlinCompiler  // Not Serializable!
) : BuildscriptParser
```

For AST parsers that need non-serializable resources, they should be designed to create those resources on-demand during `parse()` rather than storing them as fields.

## Benefits

- **Same implementation everywhere:** IDE uses the exact same parser code as the Gradle build
- **AST parsers work:** Even complex parsers can be transferred if designed properly
- **Direct invocation:** No need for expensive Tooling API calls on every parse
- **Type-safe:** Parser instances have their full API available in the IDE
- **Extensible:** New parsers automatically become available to the IDE when added to the classpath

## Example Usage in IDE

```kotlin
val parsersService = SpotlightParsersService.getInstance(project)

// Check if parsers are available
if (parsersService.hasParsers()) {
  // Parse a specific project
  val dependencies = parsersService.parseProject(project, ":feature:auth")
  dependencies?.forEach { depPath ->
    println("Depends on: $depPath")
  }
}

// Access parser metadata
parsersService.parserInfo.collect { info ->
  info.forEach { parser ->
    println("Available: ${parser.parserType} (priority: ${parser.priority})")
  }
}
```

## Related Files

- `buildscript-utils/src/main/kotlin/com/fueledbycaffeine/spotlight/buildscript/parser/BuildscriptParser.kt` - Made Serializable
- `buildscript-utils/src/main/kotlin/com/fueledbycaffeine/spotlight/buildscript/tooling/BuildscriptParsersModel.kt` - Model with parser instances
- `spotlight-gradle-plugin/src/main/kotlin/com/fueledbycaffeine/spotlight/BuildscriptParsersModelBuilder.kt` - Discovers and transfers parsers
- `spotlight-gradle-plugin/src/main/kotlin/com/fueledbycaffeine/spotlight/SpotlightModelBuilderPlugin.kt` - Registers model builder
- `spotlight-gradle-plugin/src/main/kotlin/com/fueledbycaffeine/spotlight/SpotlightSettingsPlugin.kt` - Applies model builder plugin
- `spotlight-idea-plugin/src/main/kotlin/com/fueledbycaffeine/spotlight/idea/gradle/SpotlightProjectResolverExtension.kt` - Receives parsers
- `spotlight-idea-plugin/src/main/kotlin/com/fueledbycaffeine/spotlight/idea/gradle/SpotlightParsersData.kt` - Data node with parsers
- `spotlight-idea-plugin/src/main/kotlin/com/fueledbycaffeine/spotlight/idea/gradle/SpotlightParsersService.kt` - Stores and uses parsers
- `spotlight-idea-plugin/src/main/kotlin/com/fueledbycaffeine/spotlight/idea/gradle/SpotlightParsersDataService.kt` - Imports parser data
- `spotlight-idea-plugin/src/main/resources/META-INF/plugin.xml` - Extension point registrations

