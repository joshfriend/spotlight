package com.fueledbycaffeine.spotlight.buildscript.parser.kotlin

import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT
import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT_KOTLIN
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildscriptParserProvider
import com.fueledbycaffeine.spotlight.buildscript.parser.RegexBuildscriptParserProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.ServiceLoader
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile

/**
 * Tests that KotlinPsiParserProvider is correctly loaded via SPI.
 */
class KotlinPsiParserProviderSpiTest {
  
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `ServiceLoader discovers KotlinPsiParserProvider`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    
    assertTrue(providers.isNotEmpty(), "Expected at least one parser provider to be loaded via SPI")
    
    val kotlinProvider = providers.find { it is KotlinPsiParserProvider }
    assertNotNull(kotlinProvider, "KotlinPsiParserProvider should be loaded via SPI")
  }
  
  @Test
  fun `KotlinPsiParserProvider has high priority`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val kotlinProvider = providers.first { it is KotlinPsiParserProvider }
    
    assertEquals(100, kotlinProvider.priority, "KotlinPsiParserProvider should have priority 100")
  }
  
  @Test
  fun `KotlinPsiParserProvider returns KotlinPsiParser for Kotlin files`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val kotlinProvider = providers.first { it is KotlinPsiParserProvider }
    
    // Create a temp project with a Kotlin build file
    val projectDir = tempDir.resolve("test-project").createDirectories()
    projectDir.resolve(GRADLE_SCRIPT_KOTLIN).createFile()
    val project = GradlePath(tempDir, ":test-project")

    val parser = kotlinProvider.getParser(project)
    assertEquals(KotlinPsiParser, parser, "Should return KotlinPsiParser for .gradle.kts files")
  }
  
  @Test
  fun `KotlinPsiParserProvider returns null for Groovy files`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val kotlinProvider = providers.first { it is KotlinPsiParserProvider }
    
    // Create a temp project with a Groovy build file
    val projectDir = tempDir.resolve("test-project").createDirectories()
    projectDir.resolve(GRADLE_SCRIPT).createFile()
    val project = GradlePath(tempDir, ":test-project")

    // Provider now always returns a parser, but the parser will return empty set for Groovy files
    val parser = kotlinProvider.getParser(project)
    assertNotNull(parser, "Provider should return parser")

    // Parser should return empty set for Groovy files
    val result = parser!!.parse(project, emptySet())
    assertEquals(emptySet<GradlePath>(), result, "Should return empty set for .gradle files")
  }
  
  @Test
  fun `KotlinPsiParserProvider has higher priority than RegexBuildscriptParserProvider`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val kotlinProvider = providers.filterIsInstance<KotlinPsiParserProvider>().first()
    val regexProvider = providers.filterIsInstance<RegexBuildscriptParserProvider>().first()

    assertTrue(
      kotlinProvider.priority > regexProvider.priority,
      "KotlinPsiParserProvider (${kotlinProvider.priority}) should have higher priority than RegexBuildscriptParserProvider (${regexProvider.priority})"
    )
  }
}
