package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.BuildFile
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Test
import java.nio.file.Path

class PropertyExpandingBuildscriptParserTest {

  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `expands providers gradleProperty variable and parses project dependency`() {
    // Arrange a tiny fake project with a build.gradle
    val root = tempDir
    val projectDir = root.resolve("app").also { it.toFile().mkdirs() }
    val buildFile = projectDir.resolve("build.gradle")

    buildFile.toFile().writeText(
      """
      def twig = providers.gradleProperty(\"twig\").get()

      dependencies {
          implementation(project(\":${'$'}twig\"))
      }
      """.trimIndent()
    )

    val project = GradlePath(root = root, path = ":app")

    val config = ParserConfiguration.fromMap(mapOf("twig" to "leaf"))

    // Act
    val deps: Set<GradlePath> = ParserContext.parserContext(registry = null, configuration = config) {
      BuildFile(project).parseDependencies()
    }

    // Assert
    assertTrue(deps.contains(GradlePath(root, ":leaf")))
  }
}
