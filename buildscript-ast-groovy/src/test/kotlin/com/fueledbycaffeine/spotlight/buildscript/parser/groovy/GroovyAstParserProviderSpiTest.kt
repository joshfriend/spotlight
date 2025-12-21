package com.fueledbycaffeine.spotlight.buildscript.parser.groovy

import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildScriptParserProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Path
import java.util.ServiceLoader

/**
 * Tests that GroovyAstParserProvider is correctly loaded via SPI.
 */
class GroovyAstParserProviderSpiTest {
  
  @Test
  fun `ServiceLoader discovers GroovyAstParserProvider`() {
    val providers = ServiceLoader.load(BuildScriptParserProvider::class.java).toList()
    
    assertTrue(providers.isNotEmpty(), "Expected at least one parser provider to be loaded via SPI")
    
    val groovyProvider = providers.find { it is GroovyAstParserProvider }
    assertNotNull(groovyProvider, "GroovyAstParserProvider should be loaded via SPI")
  }
  
  @Test
  fun `GroovyAstParserProvider has high priority`() {
    val providers = ServiceLoader.load(BuildScriptParserProvider::class.java).toList()
    val groovyProvider = providers.first { it is GroovyAstParserProvider }
    
    assertEquals(100, groovyProvider.getPriority(), "GroovyAstParserProvider should have priority 100")
  }
  
  @Test
  fun `GroovyAstParserProvider returns GroovyAstParser for Groovy files`() {
    val providers = ServiceLoader.load(BuildScriptParserProvider::class.java).toList()
    val groovyProvider = providers.first { it is GroovyAstParserProvider }
    
    val parser = groovyProvider.getParser(Path.of(GRADLE_SCRIPT))
    assertEquals(GroovyAstParser, parser, "Should return GroovyAstParser for .gradle files")
  }
  
  @Test
  fun `GroovyAstParserProvider returns null for Kotlin files`() {
    val providers = ServiceLoader.load(BuildScriptParserProvider::class.java).toList()
    val groovyProvider = providers.first { it is GroovyAstParserProvider }
    
    val parser = groovyProvider.getParser(Path.of("build.gradle.kts"))
    assertEquals(null, parser, "Should return null for .gradle.kts files")
  }
  
  @Test
  fun `GroovyAstParserProvider has higher priority than RegexBuildScriptParserProvider`() {
    val providers = ServiceLoader.load(BuildScriptParserProvider::class.java).toList()
    val groovyProvider = providers.first { it.javaClass.simpleName == "GroovyAstParserProvider" }
    val regexProvider = providers.first { it.javaClass.simpleName == "RegexBuildScriptParserProvider" }
    
    assertTrue(
      groovyProvider.getPriority() > regexProvider.getPriority(),
      "GroovyAstParserProvider (${groovyProvider.getPriority()}) should have higher priority than RegexBuildScriptParserProvider (${regexProvider.getPriority()})"
    )
  }
}
