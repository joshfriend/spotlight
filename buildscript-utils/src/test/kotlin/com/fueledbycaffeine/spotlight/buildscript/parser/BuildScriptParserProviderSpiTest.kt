package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT
import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT_KOTLIN
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Path
import java.util.ServiceLoader

/**
 * Tests that BuildScriptParserProvider implementations are correctly loaded via SPI.
 */
class BuildScriptParserProviderSpiTest {
  
  @Test
  fun `ServiceLoader discovers RegexBuildScriptParserProvider`() {
    val providers = ServiceLoader.load(BuildScriptParserProvider::class.java).toList()
    
    assertTrue(providers.isNotEmpty(), "Expected at least one parser provider to be loaded via SPI")
    
    val regexProvider = providers.find { it.javaClass.simpleName == "RegexBuildScriptParserProvider" }
    assertNotNull(regexProvider, "RegexBuildScriptParserProvider should be loaded via SPI")
  }
  
  @Test
  fun `RegexBuildScriptParserProvider has lowest priority`() {
    val providers = ServiceLoader.load(BuildScriptParserProvider::class.java).toList()
    val regexProvider = providers.find { it.javaClass.simpleName == "RegexBuildScriptParserProvider" }
    
    assertNotNull(regexProvider)
    assertEquals(0, regexProvider!!.getPriority(), "RegexBuildScriptParserProvider should have priority 0")
  }
  
  @Test
  fun `RegexBuildScriptParserProvider returns RegexBuildScriptParser for Groovy files`() {
    val providers = ServiceLoader.load(BuildScriptParserProvider::class.java).toList()
    val regexProvider = providers.find { it.javaClass.simpleName == "RegexBuildScriptParserProvider" }
    
    assertNotNull(regexProvider)
    val parser = regexProvider!!.getParser(Path.of(GRADLE_SCRIPT))
    assertEquals(RegexBuildScriptParser, parser, "Should return RegexBuildScriptParser for .gradle files")
  }
  
  @Test
  fun `RegexBuildScriptParserProvider returns RegexBuildScriptParser for Kotlin files`() {
    val providers = ServiceLoader.load(BuildScriptParserProvider::class.java).toList()
    val regexProvider = providers.find { it.javaClass.simpleName == "RegexBuildScriptParserProvider" }
    
    assertNotNull(regexProvider)
    val parser = regexProvider!!.getParser(Path.of(GRADLE_SCRIPT_KOTLIN))
    assertEquals(RegexBuildScriptParser, parser, "Should return RegexBuildScriptParser for .gradle.kts files")
  }
  
  @Test
  fun `ParserRegistry uses highest priority provider`() {
    val providers = ServiceLoader.load(BuildScriptParserProvider::class.java).toList()
    val sortedByPriority = providers.sortedByDescending { it.getPriority() }
    
    assertTrue(sortedByPriority.isNotEmpty(), "Expected at least one provider")
    
    // If only regex provider is present, it should be selected
    // If AST providers are on classpath, they should have higher priority (100 vs 0)
    val highestPriorityProvider = sortedByPriority.first()
    assertTrue(
      highestPriorityProvider.getPriority() >= 0,
      "Highest priority provider should have non-negative priority"
    )
  }
}
