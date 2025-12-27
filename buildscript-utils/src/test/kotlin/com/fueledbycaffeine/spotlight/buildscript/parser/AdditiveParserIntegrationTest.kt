package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Integration test demonstrating how a consumer can add custom parsing
 * on top of existing parsers using ADDITIVE mode.
 */
class AdditiveParserIntegrationTest {
  @Test
  fun `Custom ADDITIVE parser supplements AST parser`() {
    // Simulate a consumer's custom parser that adds special handling for certain build files
    val customProvider = CustomAdditiveParserProvider()
    
    // Get parser from provider
    val parser = customProvider.getParser()
    assertNotNull(parser, "Should return a parser")
    assertTrue(parser is CustomParser, "Should be custom parser")

    // Verify it's in ADDITIVE mode
    assertEquals(ParserMode.ADDITIVE, customProvider.mode)
    
    // Verify priority allows it to run before default parsers
    assertTrue(customProvider.priority > 100, "Custom parser should have higher priority to run first")
  }
  
  @Test
  fun `ADDITIVE parser adds dependencies without replacing AST parsing`() {
    val project = GradlePath(Path.of("."), ":special-module")
    val rules = emptySet<DependencyRule>()
    
    // Simulate base parser finding standard dependencies
    val baseParser = TestParser(setOf(
      GradlePath(Path.of("."), ":lib1"),
      GradlePath(Path.of("."), ":lib2")
    ))
    
    // Custom additive parser adds special dependencies
    val additiveParser = TestParser(setOf(
      GradlePath(Path.of("."), ":special-dep")
    ))
    
    // CompositeParser combines them
    val composite = CompositeParser(listOf(additiveParser, baseParser))
    val result = composite.parse(project, rules)
    
    // Verify all dependencies are found
    assertEquals(3, result.size)
    assertTrue(result.contains(GradlePath(Path.of("."), ":lib1")))
    assertTrue(result.contains(GradlePath(Path.of("."), ":lib2")))
    assertTrue(result.contains(GradlePath(Path.of("."), ":special-dep")))
  }
  
  /**
   * Example custom provider that adds special parsing for specific build files.
   * This demonstrates how a consumer would implement their own provider.
   */
  private class CustomAdditiveParserProvider : BuildscriptParserProvider {
    override fun getParser(): BuildscriptParser {
      return CustomParser()
    }
    
    override val priority: Int = 200 // Higher than AST parsers (100)
    
    override val mode: ParserMode = ParserMode.ADDITIVE // Don't replace, supplement
  }
  
  private class CustomParser : BuildscriptParser {
    override fun parse(project: GradlePath, rules: Set<DependencyRule>): Set<GradlePath> {
      // Custom logic to find additional dependencies
      return setOf(GradlePath(Path.of("."), ":special-dep"))
    }
  }
  
  private class TestParser(private val dependencies: Set<GradlePath>) : BuildscriptParser {
    override fun parse(project: GradlePath, rules: Set<DependencyRule>): Set<GradlePath> {
      return dependencies
    }
  }
}
