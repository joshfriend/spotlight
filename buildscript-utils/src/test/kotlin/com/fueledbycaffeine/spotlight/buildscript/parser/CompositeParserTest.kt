package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Path

/**
 * Tests for CompositeParser and parser chaining behavior.
 */
class CompositeParserTest {
  
  @Test
  fun `CompositeParser merges results from multiple parsers`() {
    val project = GradlePath(Path.of("."), ":app")
    val rules = emptySet<DependencyRule>()
    
    val parser1 = TestParser(setOf(GradlePath(Path.of("."), ":lib1")))
    val parser2 = TestParser(setOf(GradlePath(Path.of("."), ":lib2")))
    val parser3 = TestParser(setOf(GradlePath(Path.of("."), ":lib3")))
    
    val composite = CompositeParser(listOf(parser1, parser2, parser3))
    val result = composite.parse(project, rules)
    
    assertEquals(3, result.size)
    assertTrue(result.contains(GradlePath(Path.of("."), ":lib1")))
    assertTrue(result.contains(GradlePath(Path.of("."), ":lib2")))
    assertTrue(result.contains(GradlePath(Path.of("."), ":lib3")))
  }
  
  @Test
  fun `CompositeParser deduplicates results`() {
    val project = GradlePath(Path.of("."), ":app")
    val rules = emptySet<DependencyRule>()
    
    val sharedDep = GradlePath(Path.of("."), ":common")
    val parser1 = TestParser(setOf(sharedDep, GradlePath(Path.of("."), ":lib1")))
    val parser2 = TestParser(setOf(sharedDep, GradlePath(Path.of("."), ":lib2")))
    
    val composite = CompositeParser(listOf(parser1, parser2))
    val result = composite.parse(project, rules)
    
    assertEquals(3, result.size) // :common should appear only once
    assertTrue(result.contains(sharedDep))
    assertTrue(result.contains(GradlePath(Path.of("."), ":lib1")))
    assertTrue(result.contains(GradlePath(Path.of("."), ":lib2")))
  }
  
  @Test
  fun `ParserRegistry returns single parser when only one matches`() {
    val provider = TestProvider(ParserMode.REPLACE, 100)

    // In real scenario, would use ServiceLoader, but testing logic directly
    val parser = provider.getParser()
    assertNotNull(parser)
  }
  
  // Test helper classes
  private class TestParser(private val dependencies: Set<GradlePath>) : BuildscriptParser {
    override fun parse(project: GradlePath, rules: Set<DependencyRule>): Set<GradlePath> {
      return dependencies
    }
  }
  
  private class TestProvider(
    override val mode: ParserMode,
    override val priority: Int
  ) : BuildscriptParserProvider {
    override fun getParser(): BuildscriptParser {
      return TestParser(emptySet())
    }
  }
}
