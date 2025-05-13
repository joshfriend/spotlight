package com.fueledbycaffeine.spotlight

import com.autonomousapps.kit.truth.TestKitTruth.Companion.assertThat
import com.fueledbycaffeine.spotlight.fixtures.SpiritboxProject
import com.fueledbycaffeine.spotlight.fixtures.allProjects
import com.fueledbycaffeine.spotlight.fixtures.ideProjects
import com.fueledbycaffeine.spotlight.fixtures.includedProjects
import com.fueledbycaffeine.spotlight.fixtures.sync
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SpotlightSyncFunctionalTest {
  @Test
  fun `missing ide-projects includes all projects`() {
    // Given
    val project = SpiritboxProject().build()
    project.ideProjects.delete()

    // When
    val result = project.sync("--info")

    // Then
    assertThat(result).output().contains("gradle/ide-projects.txt was missing or empty, including all projects")
    val includedProjects = result.includedProjects()
    val allProjects = project.allProjects.readLines() +
      project.rootProject.settingsScript.rootProjectName
    assertThat(includedProjects).containsExactlyElementsIn(allProjects)
  }

  @Test
  fun `empty ide-projects includes all projects`() {
    // Given
    val project = SpiritboxProject().build()
    project.ideProjects.writeText("")

    // When
    val result = project.sync("--info")

    // Then
    assertThat(result).output().contains("gradle/ide-projects.txt was missing or empty, including all projects")
    val includedProjects = result.includedProjects()
    val allProjects = project.allProjects.readLines() +
      project.rootProject.settingsScript.rootProjectName
    assertThat(includedProjects).containsExactlyElementsIn(allProjects)
  }

  @Test
  fun `ide-projects limits loaded projects`() {
    // Given
    val project = SpiritboxProject().build()
    project.ideProjects.writeText(":rotoscope")

    // When
    val result = project.sync("--info")

    // Then
    assertThat(result).output().contains("gradle/ide-projects.txt contains 1 targets")
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