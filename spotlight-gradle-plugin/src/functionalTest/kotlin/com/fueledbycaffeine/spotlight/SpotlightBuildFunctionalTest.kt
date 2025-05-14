package com.fueledbycaffeine.spotlight

import com.autonomousapps.kit.GradleBuilder
import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.truth.TestKitTruth.Companion.assertThat
import com.fueledbycaffeine.spotlight.fixtures.SpiritboxProject
import com.fueledbycaffeine.spotlight.fixtures.allProjects
import com.fueledbycaffeine.spotlight.fixtures.build
import com.fueledbycaffeine.spotlight.fixtures.includedProjects
import com.google.common.truth.Truth.assertThat
import org.gradle.api.tasks.GradleBuild
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
    val rotoscopeBuildscript = project.rootDir.resolve("rotoscope/build.gradle")
    val contents = rotoscopeBuildscript.readText()
    rotoscopeBuildscript.writeText("// some marker\n$contents")
    settings.appendText(
      """
      spotlight {
        whenBuildscriptMatches("some marker") {
          alsoInclude ":eternal-blue"
        }
      }
    """.trimIndent()
    )

    // When
    val result = project.build(":rotoscope:assemble", "--info")

    // Then
    assertThat(result).task(":rotoscope:assemble").succeeded()
    val includedProjects = result.includedProjects()
    val expectedProjects = listOf(
      project.rootProject.settingsScript.rootProjectName,
      ":rotoscope",
      ":rotoscope:rotoscope",
      ":rotoscope:hysteria",
      ":rotoscope:sew-me-up",
      ":eternal-blue",
      ":eternal-blue:circle-with-me",
      ":eternal-blue:constance",
      ":eternal-blue:eternal-blue",
      ":eternal-blue:halcyon",
      ":eternal-blue:holy-roller",
      ":eternal-blue:hurt-you",
      ":eternal-blue:secret-garden",
      ":eternal-blue:silk-in-the-strings",
      ":eternal-blue:sun-killer",
      ":eternal-blue:the-summit",
      ":eternal-blue:we-live-in-a-strange-world",
      ":eternal-blue:yellowjacket",
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

  @Test
  fun `can run a global task`() {
    // Given
    val project = SpiritboxProject().build()

    // When
    val result = project.build("assemble", "--info")

    // Then
    assertThat(result).task(":rotoscope:assemble").succeeded()
    assertThat(result).task(":the-fear-of-fear:assemble").succeeded()
    assertThat(result).task(":eternal-blue:assemble").succeeded()
    val includedProjects = result.includedProjects()
    val allProjects = project.allProjects.readLines() +
      project.rootProject.settingsScript.rootProjectName
    assertThat(includedProjects).containsExactlyElementsIn(allProjects)
  }

  @Test
  fun `can run a task with specific working directory`() {
    // Given
    val project = SpiritboxProject().build()

    // When
    val result = GradleBuilder.build(
      project.rootDir.resolve("rotoscope"),
      "assemble",
      "--info"
    )

    // Then
    assertThat(result).task(":rotoscope:assemble").succeeded()
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
}