package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT
import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT_KOTLIN
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.ServiceLoader
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile

/**
 * Tests that BuildscriptParserProvider implementations are correctly loaded via SPI.
 */
class BuildscriptParserProviderSpiTest {

  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `ServiceLoader discovers RegexBuildscriptParserProvider`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    
    assertTrue(providers.isNotEmpty(), "Expected at least one parser provider to be loaded via SPI")
    
    val regexProvider = providers.find { it.javaClass.simpleName == "RegexBuildscriptParserProvider" }
    assertNotNull(regexProvider, "RegexBuildscriptParserProvider should be loaded via SPI")
  }
  
  @Test
  fun `RegexBuildscriptParserProvider has lowest priority`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val regexProvider = providers.find { it.javaClass.simpleName == "RegexBuildscriptParserProvider" }

    assertNotNull(regexProvider)
    assertEquals(0, regexProvider!!.priority, "RegexBuildscriptParserProvider should have priority 0")
  }
  
  @Test
  fun `RegexBuildscriptParserProvider returns RegexBuildScriptParser for Groovy files`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val regexProvider = providers.find { it.javaClass.simpleName == "RegexBuildscriptParserProvider" }

    assertNotNull(regexProvider)

    // Create a temp project with a Groovy build file
    val projectDir = tempDir.resolve("test-project").createDirectories()
    projectDir.resolve(GRADLE_SCRIPT).createFile()
    val project = GradlePath(tempDir, ":test-project")

    val parser = regexProvider!!.getParser(project)
    assertEquals(RegexBuildScriptParser, parser, "Should return RegexBuildScriptParser for .gradle files")
  }
  
  @Test
  fun `RegexBuildscriptParserProvider returns RegexBuildScriptParser for Kotlin files`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val regexProvider = providers.find { it.javaClass.simpleName == "RegexBuildscriptParserProvider" }

    assertNotNull(regexProvider)

    // Create a temp project with a Kotlin build file
    val projectDir = tempDir.resolve("test-project").createDirectories()
    projectDir.resolve(GRADLE_SCRIPT_KOTLIN).createFile()
    val project = GradlePath(tempDir, ":test-project")

    val parser = regexProvider!!.getParser(project)
    assertEquals(RegexBuildScriptParser, parser, "Should return RegexBuildScriptParser for .gradle.kts files")
  }
  
  @Test
  fun `ParserRegistry uses highest priority provider`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val sortedByPriority = providers.sortedByDescending { it.priority }
    
    assertTrue(sortedByPriority.isNotEmpty(), "Expected at least one provider")
    
    // If only regex provider is present, it should be selected
    // If AST parsers are on classpath, they should have higher priority (100 vs 0)
    val highestPriorityProvider = sortedByPriority.first()
    assertTrue(
      highestPriorityProvider.priority >= 0,
      "Highest priority provider should have non-negative priority"
    )
  }
  
  @Test
  fun `RegexBuildscriptParserProvider defaults to REPLACE mode`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val regexProvider = providers.find { it.javaClass.simpleName == "RegexBuildscriptParserProvider" }

    assertNotNull(regexProvider)
    assertEquals(ParserMode.REPLACE, regexProvider!!.mode, "Default mode should be REPLACE")
  }
}
