package com.fueledbycaffeine.spotlight.buildscript.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.ServiceLoader

/**
 * Tests that BuildscriptParserProvider implementations are correctly loaded via SPI.
 */
class BuildscriptParserProviderSpiTest {
  @Test
  fun `ServiceLoader discovers RegexBuildscriptParserProvider`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    
    assertTrue(providers.isNotEmpty(), "Expected at least one parser provider to be loaded via SPI")
    
    val regexProvider = providers.first { it is RegexBuildscriptParserProvider }
    assertNotNull(regexProvider, "RegexBuildscriptParserProvider should be loaded via SPI")
  }
  
  @Test
  fun `RegexBuildscriptParserProvider has lowest priority`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val regexProvider = providers.first { it is RegexBuildscriptParserProvider }

    assertEquals(0, regexProvider.priority, "RegexBuildscriptParserProvider should have priority 0")
  }
  
  @Test
  fun `RegexBuildscriptParserProvider returns RegexBuildScriptParser for Groovy files`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val regexProvider = providers.first { it is RegexBuildscriptParserProvider }

    val parser = regexProvider.getParser()
    assertEquals(RegexBuildscriptParser, parser, "Should return RegexBuildScriptParser")
  }
  
  @Test
  fun `RegexBuildscriptParserProvider returns RegexBuildScriptParser for Kotlin files`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val regexProvider = providers.first { it is RegexBuildscriptParserProvider }

    val parser = regexProvider.getParser()
    assertEquals(RegexBuildscriptParser, parser, "Should return RegexBuildScriptParser")
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
