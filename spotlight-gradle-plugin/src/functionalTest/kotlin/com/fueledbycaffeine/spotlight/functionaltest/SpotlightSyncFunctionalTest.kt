package com.fueledbycaffeine.spotlight.functionaltest

import com.fueledbycaffeine.spotlight.functionaltest.fixtures.*
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.appendText

class SpotlightSyncFunctionalTest {
  @Test
  fun `missing ide-projects includes all projects`() {
    // Given
    val project = SpiritboxProject().build()
    project.ideProjects.delete()

    // When
    val syncResult = project.sync()

    // Then
    val allProjects = project.allProjects.readLines() + ":"
    val projectsSynced = syncResult.projects.map { it.path }
    assertThat(projectsSynced).containsExactlyElementsIn(allProjects)
    assertThat(syncResult.stdout).contains("gradle/ide-projects.txt was missing or empty, including all projects")
  }

  @Test
  fun `empty ide-projects includes all projects`() {
    // Given
    val project = SpiritboxProject().build()
    project.ideProjects.writeText("")

    // When
    val syncResult = project.sync()

    // Then
    val allProjects = project.allProjects.readLines() + ":"
    val projectsSynced = syncResult.projects.map { it.path }
    assertThat(projectsSynced).containsExactlyElementsIn(allProjects)
    assertThat(syncResult.stdout).contains("gradle/ide-projects.txt was missing or empty, including all projects")
  }

  @Test
  fun `ide-projects limits loaded projects`() {
    // Given
    val project = SpiritboxProject().build()
    project.ideProjects.writeText(":rotoscope")

    // When
    val syncResult = project.sync()

    // Then
    val expectedProjects = listOf(
      ":",
      ":rotoscope",
      ":rotoscope:rotoscope",
      ":rotoscope:hysteria",
      ":rotoscope:sew-me-up",
    )
    val projectsSynced = syncResult.projects.map { it.path }
    assertThat(projectsSynced).containsExactlyElementsIn(expectedProjects)
    assertThat(syncResult.stdout).contains("gradle/ide-projects.txt contains 1 targets")
  }

  @Test
  fun `supports isolated projects when syncing all projects`() {
    // Given
    val project = SpiritboxProject().build()
    project.setGradleProperties("org.gradle.unsafe.isolated-projects" to "true")

    // When
    val syncResult = project.sync()

    // Then
    val allProjects = project.allProjects.readLines() + ":"
    val projectsSynced = syncResult.projects.map { it.path }
    assertThat(projectsSynced).containsExactlyElementsIn(allProjects)
    assertThat(syncResult.configurationCacheStored).isTrue()
    val ccReport = syncResult.ccReport()
    assertThat(ccReport.inputs).containsExactlyElementsIn(listOf(
      CCDiagnostic.Input(type = "file system entry", name = "gradle/all-projects.txt"),
      CCDiagnostic.Input(type = "file", name = "gradle/all-projects.txt"),
      CCDiagnostic.Input(type = "file system entry", name = "gradle/ide-projects.txt"),
      CCDiagnostic.Input(type = "file", name = "gradle/ide-projects.txt"),
      CCDiagnostic.Input(type = "system property", name = "idea.sync.active"),
      CCDiagnostic.Input(type = "system property", name = "spotlight.enabled"),
    ))
  }

  @Test
  fun `supports isolated projects when syncing specific projects`() {
    // Given
    val project = SpiritboxProject().build()
    project.ideProjects.writeText(":rotoscope")
    project.setGradleProperties("org.gradle.unsafe.isolated-projects" to "true")

    // When
    val syncResult = project.sync()

    // Then
    val expectedProjects = listOf(
      ":",
      ":rotoscope",
      ":rotoscope:rotoscope",
      ":rotoscope:hysteria",
      ":rotoscope:sew-me-up",
    )
    val projectsSynced = syncResult.projects.map { it.path }
    assertThat(projectsSynced).containsExactlyElementsIn(expectedProjects)
    assertThat(syncResult.stdout).contains("gradle/ide-projects.txt contains 1 targets")
    assertThat(syncResult.configurationCacheStored).isTrue()
    val ccReport = syncResult.ccReport()
    assertThat(ccReport.inputs).containsExactlyElementsIn(listOf(
      CCDiagnostic.Input(type = "file system entry", name = "gradle/ide-projects.txt"),
      CCDiagnostic.Input(type = "file", name = "gradle/ide-projects.txt"),
      CCDiagnostic.Input(type = "file system entry", name = "gradle/spotlight-rules.json"),
      CCDiagnostic.Input(type = "system property", name = "idea.sync.active"),
      CCDiagnostic.Input(type = "system property", name = "spotlight.enabled"),
    ))
  }

  @Test
  fun `sync can reuse configuration cache when isolated projects is enabled`() {
    // Given
    val project = SpiritboxProject().build()
    project.ideProjects.writeText(":rotoscope")
    project.setGradleProperties("org.gradle.unsafe.isolated-projects" to "true")
    1
    // When
    val syncResult1 = project.sync()
    val syncResult2 = project.sync()

    // Then
    assertThat(syncResult1.configurationCacheStored).isTrue()
    assertThat(syncResult2.configurationCacheReused).isTrue()
  }

  @Test
  fun `sync invalidate full configuration cache when isolated projects is enabled`() {
    // Given
    val project = SpiritboxProject().build()
    project.ideProjects.writeText(":rotoscope")
    project.setGradleProperties("org.gradle.unsafe.isolated-projects" to "true")
    1
    // When
    val syncResult1 = project.sync()
    project.rootDir.resolve("settings.gradle")
      .appendText("\n// some settings change")
    val syncResult2 = project.sync()

    // Then
    assertThat(syncResult1.configurationCacheStored).isTrue()
    assertThat(syncResult2.configurationCacheInvalidationReason)
      .isEqualTo("file 'settings.gradle' has changed.")
    assertThat(syncResult2.configurationCacheStored).isTrue()
  }

  @Test
  fun `sync invalidate partial configuration cache when isolated projects is enabled`() {
    // Given
    val project = SpiritboxProject().build()
    project.ideProjects.writeText(":rotoscope")
    project.setGradleProperties("org.gradle.unsafe.isolated-projects" to "true")

    // When
    val syncResult1 = project.sync()
    project.projectDir(":rotoscope:rotoscope").resolve("build.gradle")
      .appendText("\n// some buildscript change")

    val syncResult2 = project.sync()

    // Then
    assertThat(syncResult1.configurationCacheStored).isTrue()
    assertThat(syncResult2.configurationCacheInvalidationReason)
      .isEqualTo("file 'rotoscope/rotoscope/build.gradle' has changed.")
    assertThat(syncResult2.configurationCacheReused).isFalse()
    assertThat(syncResult2.configurationCacheUpdated).isTrue()
  }

  @Test
  fun `sync invalidates configuration cache adding project to spotlight when isolated projects is enabled`() {
    // Given
    val project = SpiritboxProject().build()
    project.ideProjects.writeText(":rotoscope")
    project.setGradleProperties("org.gradle.unsafe.isolated-projects" to "true")

    // When
    val syncResult1 = project.sync()
    project.ideProjects.appendText("\n:tsunami-sea")
    val syncResult2 = project.sync()

    // Then
    assertThat(syncResult1.configurationCacheStored).isTrue()
    assertThat(syncResult2.configurationCacheInvalidationReason)
      .isEqualTo("file 'gradle/ide-projects.txt' has changed.")
    assertThat(syncResult2.configurationCacheReused).isFalse()
    assertThat(syncResult2.configurationCacheStored).isTrue()
  }
}