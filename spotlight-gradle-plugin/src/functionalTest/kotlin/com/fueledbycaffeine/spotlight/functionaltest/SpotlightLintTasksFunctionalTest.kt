package com.fueledbycaffeine.spotlight.functionaltest

import com.autonomousapps.kit.GradleProject
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

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

  @ParameterizedTest
  @EnumSource(GradleProject.DslKind::class)
  fun `check all-projects list fails when settings has include statements`(dslKind: GradleProject.DslKind) {
    // Given
    val project = SpiritboxProject().build(dslKind = dslKind)
    project.setGradleProperties("org.gradle.unsafe.isolated-projects" to "true")

    // Ensure all-projects list is sorted first
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    allProjects.writeText(allProjects.readLines().sorted().joinToString("\n"))

    // Create a build file for a project that would be included
    val someProject = project.rootDir.resolve("some-project")
    someProject.mkdirs()
    someProject.resolve(dslKind.buildFile).writeText("// empty build file")

    // Add include statement to settings
    val settingsFile = project.rootDir.resolve(dslKind.settingsFile)
    val currentContent = settingsFile.readText()
    val includeStatement = when (dslKind) {
      GradleProject.DslKind.GROOVY -> "\ninclude ':some-project'\n"
      GradleProject.DslKind.KOTLIN -> "\ninclude(\":some-project\")\n"
    }
    settingsFile.writeText(currentContent + includeStatement)

    // When
    val result = project.buildAndFail(":${CheckSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${CheckSpotlightProjectListTask.NAME}").failed()
    assertThat(result.output).contains("Found 'include' statements in ${dslKind.settingsFile}:")
  }

  @ParameterizedTest
  @EnumSource(GradleProject.DslKind::class)
  fun `check all-projects list succeeds when no include statements present`(dslKind: GradleProject.DslKind) {
    // Given
    val project = SpiritboxProject().build(dslKind = dslKind)
    project.setGradleProperties("org.gradle.unsafe.isolated-projects" to "true")
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    allProjects.writeText(allProjects.readLines().sorted().joinToString("\n"))

    // When
    val result = project.build(":${CheckSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${CheckSpotlightProjectListTask.NAME}").succeeded()
  }
}
