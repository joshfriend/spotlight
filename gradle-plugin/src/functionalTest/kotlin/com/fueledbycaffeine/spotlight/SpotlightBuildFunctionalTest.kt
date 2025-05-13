package com.fueledbycaffeine.spotlight

import com.autonomousapps.kit.truth.TestKitTruth.Companion.assertThat
import com.fueledbycaffeine.spotlight.fixtures.SpiritboxProject
import com.fueledbycaffeine.spotlight.fixtures.build
import com.fueledbycaffeine.spotlight.fixtures.includedProjects
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SpotlightBuildFunctionalTest {
  @Test
  fun `computes explicit dependencies correctly`() {
    // Given
    val project = SpiritboxProject().build()

    // When
    val result = project.build(":rotoscope:assemble", "--info")

    // Then
    assertThat(result).task(":rotoscope:assemble").succeeded()
    assertThat(result).task(":rotoscope:rotoscope:compileJava").noSource()
    assertThat(result).task(":rotoscope:hysteria:compileJava").noSource()
    assertThat(result).task(":rotoscope:sew-me-up:compileJava").noSource()
    assertThat(result).output().contains("Requested targets include 3 projects transitively")
    val includedProjects = result.includedProjects()
    val expectedProjects = listOf(
      project.rootProject.settingsScript.rootProjectName,
      ":rotoscope",
      ":rotoscope:rotoscope",
      ":rotoscope:hysteria",
      ":rotoscope:sew-me-up",
    )
    assertThat(includedProjects).containsExactlyElementsIn(expectedProjects)
  }

  @Test
  fun `can include implicit dependencies by project path`() {
    // Given
    val project = SpiritboxProject().build()

    val settings = project.rootDir.resolve("settings.gradle")
    settings.appendText("""
      spotlight {
        whenProjectPathMatches(":rotoscope:.*") {
          alsoInclude ":tsunami-sea"
        }
      }
    """.trimIndent())

    // When
    val result = project.build(":rotoscope:assemble", "--info")

    // Then
    assertThat(result).task(":rotoscope:assemble").succeeded()
    assertThat(result).output().contains("Requested targets include 4 projects transitively")
    val includedProjects = result.includedProjects()
    val expectedProjects = listOf(
      project.rootProject.settingsScript.rootProjectName,
      ":rotoscope",
      ":rotoscope:rotoscope",
      ":rotoscope:hysteria",
      ":rotoscope:sew-me-up",
      ":tsunami-sea"
    )
    assertThat(includedProjects).containsExactlyElementsIn(expectedProjects)
  }

  @Test
  fun `can include implicit dependencies by buildscript contents`() {
    // Given
    val project = SpiritboxProject().build()

    val settings = project.rootDir.resolve("settings.gradle")
    settings.appendText(
      """
      spotlight {
        whenBuildscriptMatches("id 'java-library'") {
          alsoInclude ":eternal-blue"
        }
      }
    """.trimIndent()
    )

    // When
    val result = project.build(":rotoscope:assemble", "--info")

    // Then
    assertThat(result).task(":rotoscope:assemble").succeeded()
    assertThat(result).output().contains("Requested targets include 4 projects transitively")
    val includedProjects = result.includedProjects()
    val expectedProjects = listOf(
      project.rootProject.settingsScript.rootProjectName,
      ":rotoscope",
      ":rotoscope:rotoscope",
      ":rotoscope:hysteria",
      ":rotoscope:sew-me-up",
      ":eternal-blue"
    )
    assertThat(includedProjects).containsExactlyElementsIn(expectedProjects)
  }

  @Test
  fun `can include only the root project`() {
    // Given
    val project = SpiritboxProject().build()

    // When
    val result = project.build(":help", "--info")

    // Then
    assertThat(result).task(":help").succeeded()
    assertThat(result).output().contains("Requested targets include 0 projects transitively")
    val includedProjects = result.includedProjects()
    assertThat(includedProjects).containsExactly(project.rootProject.settingsScript.rootProjectName)
  }
}