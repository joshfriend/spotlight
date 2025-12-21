package com.fueledbycaffeine.spotlight.buildscript.parser.kotlin

import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT_KOTLIN
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildScriptParserProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Path
import java.util.ServiceLoader

/**
 * Tests that KotlinPsiParserProvider is correctly loaded via SPI.
 */
class KotlinPsiParserProviderSpiTest {
  
  @Test
  fun `ServiceLoader discovers KotlinPsiParserProvider`() {
    val providers = ServiceLoader.load(BuildScriptParserProvider::class.java).toList()
    
    assertTrue(providers.isNotEmpty(), "Expected at least one parser provider to be loaded via SPI")
    
    val kotlinProvider = providers.find { it is KotlinPsiParserProvider }
    assertNotNull(kotlinProvider, "KotlinPsiParserProvider should be loaded via SPI")
  }
  
  @Test
  fun `KotlinPsiParserProvider has high priority`() {
    val providers = ServiceLoader.load(BuildScriptParserProvider::class.java).toList()
    val kotlinProvider = providers.first { it is KotlinPsiParserProvider }
    
    assertEquals(100, kotlinProvider.getPriority(), "KotlinPsiParserProvider should have priority 100")
  }
  
  @Test
  fun `KotlinPsiParserProvider returns KotlinPsiParser for Kotlin files`() {
    val providers = ServiceLoader.load(BuildScriptParserProvider::class.java).toList()
    val kotlinProvider = providers.first { it is KotlinPsiParserProvider }
    
    val parser = kotlinProvider.getParser(Path.of(GRADLE_SCRIPT_KOTLIN))
    assertEquals(KotlinPsiParser, parser, "Should return KotlinPsiParser for .gradle.kts files")
  }
  
  @Test
  fun `KotlinPsiParserProvider returns null for Groovy files`() {
    val providers = ServiceLoader.load(BuildScriptParserProvider::class.java).toList()
    val kotlinProvider = providers.first { it is KotlinPsiParserProvider }
    
    val parser = kotlinProvider.getParser(Path.of("build.gradle"))
    assertEquals(null, parser, "Should return null for .gradle files")
  }
  
  @Test
  fun `KotlinPsiParserProvider has higher priority than RegexBuildScriptParserProvider`() {
    val providers = ServiceLoader.load(BuildScriptParserProvider::class.java).toList()
    val kotlinProvider = providers.first { it.javaClass.simpleName == "KotlinPsiParserProvider" }
    val regexProvider = providers.first { it.javaClass.simpleName == "RegexBuildScriptParserProvider" }
    
    assertTrue(
      kotlinProvider.getPriority() > regexProvider.getPriority(),
      "KotlinPsiParserProvider (${kotlinProvider.getPriority()}) should have higher priority than RegexBuildScriptParserProvider (${regexProvider.getPriority()})"
    )
  }
}
