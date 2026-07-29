package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.parser.impl.CompositeTaskRequestParser
import com.fueledbycaffeine.spotlight.buildscript.parser.impl.TaskRequestParserProvider
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Tests for CompositeTaskRequestParser which merges results from multiple parsers.
 */
class CompositeTaskRequestParserTest {

  @Test
  fun `CompositeParser merges results from multiple parsers`() {
    val taskNames = setOf("doesNotMatter")

    val parser1 = TestParser(setOf(GradlePath(Path.of("."), ":lib1")))
    val parser2 = TestParser(setOf(GradlePath(Path.of("."), ":lib2")))
    val parser3 = TestParser(setOf(GradlePath(Path.of("."), ":lib3")))

    val composite = CompositeTaskRequestParser(listOf(parser1, parser2, parser3))
    val result = composite.parse(taskNames)

    assertEquals(3, result.size)
    assertTrue(result.contains(GradlePath(Path.of("."), ":lib1")))
    assertTrue(result.contains(GradlePath(Path.of("."), ":lib2")))
    assertTrue(result.contains(GradlePath(Path.of("."), ":lib3")))
  }

  @Test
  fun `CompositeParser deduplicates results`() {
    val taskNames = setOf("doesNotMatter")

    val sharedDep = GradlePath(Path.of("."), ":common")
    val parser1 = TestParser(setOf(sharedDep, GradlePath(Path.of("."), ":lib1")))
    val parser2 = TestParser(setOf(sharedDep, GradlePath(Path.of("."), ":lib2")))
    val composite = CompositeTaskRequestParser(listOf(parser1, parser2))
    val result = composite.parse(taskNames)

    assertEquals(3, result.size) // :common should appear only once
    assertTrue(result.contains(sharedDep))
    assertTrue(result.contains(GradlePath(Path.of("."), ":lib1")))
    assertTrue(result.contains(GradlePath(Path.of("."), ":lib2")))
  }

  @Test
  fun `ParserProvider returns parser`() {
    val provider = TestProvider()

    val parser = provider.getParser()
    assertNotNull(parser)
  }

  // Test helper classes
  private class TestParser(private val dependencies: Set<GradlePath>) : TaskRequestParser {
    override fun parse(taskNames: Set<String>): Set<GradlePath> {
      return dependencies
    }
  }

  private class TestProvider : TaskRequestParserProvider {
    override fun getParser(): TaskRequestParser {
      return TestParser(emptySet())
    }
  }
}