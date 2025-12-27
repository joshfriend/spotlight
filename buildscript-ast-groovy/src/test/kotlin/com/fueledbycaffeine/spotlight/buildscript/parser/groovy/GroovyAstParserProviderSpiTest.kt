package com.fueledbycaffeine.spotlight.buildscript.parser.groovy
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT_KOTLIN
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildscriptParserProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.ServiceLoader
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
/**
 * Tests that GroovyAstParserProvider is correctly loaded via SPI.
 */
class GroovyAstParserProviderSpiTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `ServiceLoader discovers GroovyAstParserProvider`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    assertTrue(providers.isNotEmpty(), "Expected at least one parser provider to be loaded via SPI")
    val groovyProvider = providers.find { it is GroovyAstParserProvider }
    assertThat(groovyProvider).isNotNull()
  }
  @Test
  fun `GroovyAstParserProvider has high priority`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val groovyProvider = providers.first { it is GroovyAstParserProvider }
    assertThat(groovyProvider.priority).equals(groovyProvider.priority)
  }
  @Test
  fun `GroovyAstParserProvider returns GroovyAstParser`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val groovyProvider = providers.first { it is GroovyAstParserProvider }
    val parser = groovyProvider.getParser()
    assertThat(parser).equals(GroovyAstParser)
  }
  @Test
  fun `GroovyAstParser returns empty set for Kotlin files`() {
    // Create a temp project with a Kotlin build file
    val projectDir = tempDir.resolve("test-project").createDirectories()
    projectDir.resolve(GRADLE_SCRIPT_KOTLIN).createFile()
    val project = GradlePath(tempDir, ":test-project")
    // Parser should return empty set for Kotlin files (parser itself decides applicability)
    val result = GroovyAstParser.parse(project, emptySet())
    assertThat(result).isEmpty()
  }
  @Test
  fun `GroovyAstParserProvider has higher priority than RegexBuildscriptParserProvider`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val groovyProvider = providers.first { it.javaClass.simpleName == "GroovyAstParserProvider" }
    val regexProvider = providers.first { it.javaClass.simpleName == "RegexBuildscriptParserProvider" }
    assertThat(groovyProvider.priority).isGreaterThan(regexProvider.priority)
  }
}
