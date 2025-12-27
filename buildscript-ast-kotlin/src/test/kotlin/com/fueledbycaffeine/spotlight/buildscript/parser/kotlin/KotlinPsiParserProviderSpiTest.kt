package com.fueledbycaffeine.spotlight.buildscript.parser.kotlin
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT
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
    assertThat(kotlinProvider).isNotNull()
  }

  @Test
  fun `KotlinPsiParserProvider has high priority`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val kotlinProvider = providers.first { it is KotlinPsiParserProvider }
    assertThat(kotlinProvider.priority).equals(100)
  }

  @Test
  fun `KotlinPsiParserProvider returns KotlinPsiParser`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val kotlinProvider = providers.first { it is KotlinPsiParserProvider }
    val parser = kotlinProvider.getParser()
    assertThat(parser).equals(KotlinPsiParser)
  }

  @Test
  fun `KotlinPsiParser returns empty set for Groovy files`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val kotlinProvider = providers.first { it is KotlinPsiParserProvider }
    // Create a temp project with a Groovy build file
    val projectDir = tempDir.resolve("test-project").createDirectories()
    projectDir.resolve(GRADLE_SCRIPT).createFile()
    val project = GradlePath(tempDir, ":test-project")
    val parser = kotlinProvider.getParser()
    // Parser should return empty set for Groovy files (parser decides applicability)
    val result = parser.parse(project, emptySet())
    assertThat(result).isEmpty()
  }

  @Test
  fun `KotlinPsiParserProvider has higher priority than RegexBuildscriptParserProvider`() {
    val providers = ServiceLoader.load(BuildscriptParserProvider::class.java).toList()
    val kotlinProvider = providers.filterIsInstance<KotlinPsiParserProvider>().first()
    val regexProvider = providers.filterIsInstance<RegexBuildscriptParserProvider>().first()
    assertThat(kotlinProvider.priority).isGreaterThan(regexProvider.priority)
  }
}
