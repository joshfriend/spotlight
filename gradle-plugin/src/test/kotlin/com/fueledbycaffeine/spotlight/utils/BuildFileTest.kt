package com.fueledbycaffeine.spotlight.utils

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class BuildFileTest {
  @TempDir lateinit var buildRoot: Path

  @Test fun `reads dependencies`() {
    val buildFilePath = buildRoot.resolve("build.gradle")
    val gradlePath = GradlePath(buildRoot, ":")
    buildFilePath.writeText("""
      dependencies {
        implementation  project(":multiple-spaces-double-quotes")
        implementation  project(':multiple-spaces-single-quotes')

        implementation project(":one-space-double-quotes")
        implementation project(':one-space-single-quotes')

        implementation(project(":parentheses-double-quotes"))
        implementation(project(':parentheses-single-quotes'))

        api(project(':other-configuration'))
       
          implementation project(':bad-indentation')

        // implementation(project(':commented'))
      }
    """.trimIndent())

    val buildFile = BuildFile(gradlePath)

    assertThat(buildFile.parseDependencies()).containsExactlyInAnyOrder(
      GradlePath(buildRoot, ":multiple-spaces-double-quotes"),
      GradlePath(buildRoot, ":multiple-spaces-single-quotes"),
      GradlePath(buildRoot, ":one-space-double-quotes"),
      GradlePath(buildRoot, ":one-space-single-quotes"),
      GradlePath(buildRoot, ":parentheses-double-quotes"),
      GradlePath(buildRoot, ":parentheses-single-quotes"),
      GradlePath(buildRoot, ":other-configuration"),
      GradlePath(buildRoot, ":bad-indentation"),
    )
  }

  @Test fun `ignores duplicates`() {
    val buildFilePath = buildRoot.resolve("build.gradle")
    val gradlePath = GradlePath(buildRoot, ":")
    buildFilePath.writeText(
      """
      dependencies {
        debugImplementation project(":foo")
        debugImplementation project(":foo")
      }
    """.trimIndent()
    )

    val buildFile = BuildFile(gradlePath)

    assertThat(buildFile.parseDependencies()).containsExactlyInAnyOrder(
      GradlePath(buildRoot, ":foo")
    )
  }
}