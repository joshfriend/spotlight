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
    assertThat(result).output().contains("Spotlight included 13 projects")
    val includedProjects = result.includedProjects()
    val allProjects = project.allProjects.readLines()
    allProjects.forEach { path ->
      assertThat(includedProjects).contains(path)
    }
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
    assertThat(result).output().contains("Spotlight included 13 projects")
    val includedProjects = result.includedProjects()
    val allProjects = project.allProjects.readLines()
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
    assertThat(result).output().contains("Spotlight included 4 projects")
    val includedProjects = result.includedProjects()
    assertThat(includedProjects).contains(":rotoscope")
    assertThat(includedProjects).contains(":rotoscope:rotoscope")
    assertThat(includedProjects).contains(":rotoscope:hysteria")
    assertThat(includedProjects).contains(":rotoscope:sew-me-up")
  }
}