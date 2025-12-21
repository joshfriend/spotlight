package com.fueledbycaffeine.spotlight.buildscript.parser.groovy

import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT
import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT_KOTLIN
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildscriptParserProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.ServiceLoader
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile

/**
 * Tests that GroovyAstParserProvider is correctly loaded via SPI.
 */
class GroovyAstParserProviderSpiTest {
  
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `ServiceLoader discovers GroovyAstParserProvider`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    
    assertTrue(providers.isNotEmpty(), "Expected at least one parser provider to be loaded via SPI")
    
    val groovyProvider = providers.find { it is GroovyAstParserProvider }
    assertNotNull(groovyProvider, "GroovyAstParserProvider should be loaded via SPI")
  }
  
  @Test
  fun `GroovyAstParserProvider has high priority`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val groovyProvider = providers.first { it is GroovyAstParserProvider }
    
    assertEquals(100, groovyProvider.priority, "GroovyAstParserProvider should have priority 100")
  }
  
  @Test
  fun `GroovyAstParserProvider returns GroovyAstParser for Groovy files`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val groovyProvider = providers.first { it is GroovyAstParserProvider }
    
    // Create a temp project with a Groovy build file
    val projectDir = tempDir.resolve("test-project").createDirectories()
    projectDir.resolve(GRADLE_SCRIPT).createFile()
    val project = GradlePath(tempDir, ":test-project")

    val parser = groovyProvider.getParser(project)
    assertEquals(GroovyAstParser, parser, "Should return GroovyAstParser for .gradle files")
  }
  
  @Test
  fun `GroovyAstParserProvider returns null for Kotlin files`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val groovyProvider = providers.first { it is GroovyAstParserProvider }
    
    // Create a temp project with a Kotlin build file
    val projectDir = tempDir.resolve("test-project").createDirectories()
    projectDir.resolve(GRADLE_SCRIPT_KOTLIN).createFile()
    val project = GradlePath(tempDir, ":test-project")

    // Provider now always returns a parser, but the parser will return empty set for Kotlin files
    val parser = groovyProvider.getParser(project)
    assertNotNull(parser, "Provider should return parser")

    // Parser should return empty set for Kotlin files
    val result = parser!!.parse(project, emptySet())
    assertEquals(emptySet<GradlePath>(), result, "Should return empty set for .gradle.kts files")
  }
  
  @Test
  fun `GroovyAstParserProvider has higher priority than RegexBuildscriptParserProvider`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val groovyProvider = providers.first { it.javaClass.simpleName == "GroovyAstParserProvider" }
    val regexProvider = providers.first { it.javaClass.simpleName == "RegexBuildscriptParserProvider" }

    assertTrue(
      groovyProvider.priority > regexProvider.priority,
      "GroovyAstParserProvider (${groovyProvider.priority}) should have higher priority than RegexBuildscriptParserProvider (${regexProvider.priority})"
    )
  }
}
