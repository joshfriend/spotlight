package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.parser.impl.BuildscriptParserProvider
import com.fueledbycaffeine.spotlight.buildscript.parser.impl.RegexBuildscriptParser
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
  fun `ServiceLoader discovers RegexBuildscriptParser Provider`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    
    assertTrue(providers.isNotEmpty(), "Expected at least one parser provider to be loaded via SPI")
    
    val regexProvider = providers.firstOrNull { it is RegexBuildscriptParser.Provider }
    assertNotNull(regexProvider, "RegexBuildscriptParser.Provider should be loaded via SPI")
  }
  
  @Test
  fun `RegexBuildscriptParser Provider returns RegexBuildscriptParser`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val regexProvider = providers.firstOrNull { it is RegexBuildscriptParser.Provider }
    assertNotNull(regexProvider)

    val parser = regexProvider!!.getParser()
    assertEquals(RegexBuildscriptParser, parser, "Should return RegexBuildscriptParser")
  }
}
