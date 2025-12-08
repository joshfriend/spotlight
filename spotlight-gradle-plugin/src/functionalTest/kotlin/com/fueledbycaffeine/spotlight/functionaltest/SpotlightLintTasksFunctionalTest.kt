package com.fueledbycaffeine.spotlight.functionaltest

import com.autonomousapps.kit.truth.TestKitTruth.Companion.assertThat
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.SpiritboxProject
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.build
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.buildAndFail
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.setGradleProperties
import com.fueledbycaffeine.spotlight.tasks.CheckSpotlightProjectListTask
import com.fueledbycaffeine.spotlight.tasks.SortSpotlightProjectsListTask
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SpotlightLintTasksFunctionalTest {
  @Test
  fun `check all-projects list fails when not sorted`() {
    // Given
    val project = SpiritboxProject().build()
    project.setGradleProperties("org.gradle.unsafe.isolated-projects" to "true")
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    allProjects.writeText(allProjects.readLines().sorted().reversed().joinToString("\n"))

    // When
    val result = project.buildAndFail(":${CheckSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${CheckSpotlightProjectListTask.NAME}").failed()
  }

  @Test
  fun `check all-projects list succeeds when sorted`() {
    // Given
    val project = SpiritboxProject().build()
    project.setGradleProperties("org.gradle.unsafe.isolated-projects" to "true")
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    allProjects.writeText(allProjects.readLines().sorted().joinToString("\n"))

    // When
    val result = project.build(":${CheckSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${CheckSpotlightProjectListTask.NAME}").succeeded()
  }

  @Test
  fun `sort all-projects list`() {
    // Given
    val project = SpiritboxProject().build()
    project.setGradleProperties("org.gradle.unsafe.isolated-projects" to "true")
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    val allProjectsList = allProjects.readLines().sorted().reversed()
    allProjects.writeText(allProjectsList.joinToString("\n"))

    // When
    val result = project.build(":${SortSpotlightProjectsListTask.NAME}")

    // Then
    assertThat(result).task(":${SortSpotlightProjectsListTask.NAME}").succeeded()
    val projectList = allProjects.readLines()
    assertThat(projectList).isEqualTo(allProjectsList.reversed())
  }

  @Test
  fun `check runs check all-projects task`() {
    // Given
    val project = SpiritboxProject().build()
    project.setGradleProperties("org.gradle.unsafe.isolated-projects" to "true")
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    allProjects.writeText(allProjects.readLines().sorted().joinToString("\n"))

    // When
    val result = project.build(":check")

    // Then
    assertThat(result).task(":${CheckSpotlightProjectListTask.NAME}").succeeded()
  }
}
