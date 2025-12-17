package com.fueledbycaffeine.spotlight.functionaltest

import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.truth.TestKitTruth.Companion.assertThat
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.SpiritboxProject
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.build
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.buildAndFail
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.setGradleProperties
import com.fueledbycaffeine.spotlight.tasks.CheckSpotlightProjectListTask
import com.fueledbycaffeine.spotlight.tasks.FixSpotlightProjectListTask
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
  fun `fix all-projects list sorts it`() {
    // Given
    val project = SpiritboxProject().build()
    project.setGradleProperties("org.gradle.unsafe.isolated-projects" to "true")
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    val allProjectsList = allProjects.readLines().sorted().reversed()
    allProjects.writeText(allProjectsList.joinToString("\n"))

    // When
    val result = project.build(":${FixSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${FixSpotlightProjectListTask.NAME}").succeeded()
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

  @Test
  fun `check all-projects list fails when project has no build file`() {
    // Given
    val project = SpiritboxProject().build()
    project.setGradleProperties("org.gradle.unsafe.isolated-projects" to "true")
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)

    // Add a project path that doesn't have a build file
    val projectsList = allProjects.readLines().toMutableList()
    projectsList.add(":missing-build-file")
    allProjects.writeText(projectsList.sorted().joinToString("\n"))

    // Create the directory but no build file
    project.rootDir.resolve("missing-build-file").mkdirs()

    // When
    val result = project.buildAndFail(":${CheckSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${CheckSpotlightProjectListTask.NAME}").failed()
    assertThat(result.output).contains("Found invalid projects in ${SpotlightProjectList.ALL_PROJECTS_LOCATION}")
    assertThat(result.output).contains(":missing-build-file")
    assertThat(result.output).contains("do not have a build.gradle(.kts) file")
  }

  @Test
  fun `check all-projects list fails when missing discovered projects`() {
    // Given
    val project = SpiritboxProject().build()
    project.setGradleProperties("org.gradle.unsafe.isolated-projects" to "true")
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)

    // Remove all child projects of :rotoscope but keep :rotoscope itself
    // Since :rotoscope depends on its children, BFS should discover them as missing
    val projectsList = allProjects.readLines()
      .filterNot { it.startsWith(":rotoscope:") }
      .sorted() // Keep it sorted so checkSorted() passes
    allProjects.writeText(projectsList.joinToString("\n"))

    // When
    val result = project.buildAndFail(":${CheckSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${CheckSpotlightProjectListTask.NAME}").failed()
    assertThat(result.output).contains("Found projects missing from ${SpotlightProjectList.ALL_PROJECTS_LOCATION}")
    assertThat(result.output).contains("discovered via dependency graph but are not listed")
  }

  @Test
  fun `fix removes invalid projects`() {
    // Given
    val project = SpiritboxProject().build()
    project.setGradleProperties("org.gradle.unsafe.isolated-projects" to "true")
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)

    // Add a project that doesn't have a build file
    val originalProjects = allProjects.readLines().toMutableList()
    originalProjects.add(":invalid-project")
    allProjects.writeText(originalProjects.sorted().joinToString("\n"))

    // Create the directory but no build file
    project.rootDir.resolve("invalid-project").mkdirs()

    // When
    val result = project.build(":${FixSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${FixSpotlightProjectListTask.NAME}").succeeded()
    val updatedProjects = allProjects.readLines()
    assertThat(updatedProjects).doesNotContain(":invalid-project")
    assertThat(result.output).contains("removed 1 invalid project(s)")
  }

  @Test
  fun `fix adds missing discovered projects`() {
    // Given
    val project = SpiritboxProject().build()
    project.setGradleProperties("org.gradle.unsafe.isolated-projects" to "true")
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)

    // Remove child projects that would be discovered via BFS from :rotoscope
    val originalProjectsList = allProjects.readLines()
    val projectsList = originalProjectsList
      .filterNot { it.startsWith(":rotoscope:") }
    allProjects.writeText(projectsList.joinToString("\n"))
    
    val expectedMissingCount = originalProjectsList.count { it.startsWith(":rotoscope:") }

    // When
    val result = project.build(":${FixSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${FixSpotlightProjectListTask.NAME}").succeeded()
    val updatedProjects = allProjects.readLines()
    assertThat(updatedProjects).contains(":rotoscope:hysteria")
    assertThat(result.output).contains("added $expectedMissingCount missing project(s)")
  }

  @Test
  fun `fix removes invalid and adds missing projects in one pass`() {
    // Given
    val project = SpiritboxProject().build()
    project.setGradleProperties("org.gradle.unsafe.isolated-projects" to "true")
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)

    // Add an invalid project and remove a valid one that will be discovered via BFS
    val originalProjects = allProjects.readLines()
    val projectsList = originalProjects
      .filterNot { it == ":rotoscope:hysteria" }
      .toMutableList()
    projectsList.add(":invalid-project")
    allProjects.writeText(projectsList.sorted().joinToString("\n"))

    // Create the invalid directory but no build file
    project.rootDir.resolve("invalid-project").mkdirs()

    // When
    val result = project.build(":${FixSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${FixSpotlightProjectListTask.NAME}").succeeded()
    val updatedProjects = allProjects.readLines()
    assertThat(updatedProjects).doesNotContain(":invalid-project")
    assertThat(updatedProjects).contains(":rotoscope:hysteria")
    assertThat(result.output).contains("removed 1 invalid project(s)")
    assertThat(result.output).contains("added 1 missing project(s)")
  }

  @Test
  fun `fix keeps file sorted`() {
    // Given
    val project = SpiritboxProject().build()
    project.setGradleProperties("org.gradle.unsafe.isolated-projects" to "true")
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)

    // Unsort the list
    val projectsList = allProjects.readLines().sorted().reversed()
    allProjects.writeText(projectsList.joinToString("\n"))

    // When
    val result = project.build(":${FixSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${FixSpotlightProjectListTask.NAME}").succeeded()
    val updatedProjects = allProjects.readLines()
    assertThat(updatedProjects).isEqualTo(updatedProjects.sorted())
  }

  @Test
  fun `check succeeds after fix resolves issues`() {
    // Given
    val project = SpiritboxProject().build()
    project.setGradleProperties("org.gradle.unsafe.isolated-projects" to "true")
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)

    // Create issues: remove valid projects and add invalid one
    val projectsList = allProjects.readLines()
      .filterNot { it.startsWith(":rotoscope:") }
      .toMutableList()
    projectsList.add(":invalid-project")
    allProjects.writeText(projectsList.joinToString("\n"))
    project.rootDir.resolve("invalid-project").mkdirs()

    // When: First fix to resolve issues
    val fixResult = project.build(":${FixSpotlightProjectListTask.NAME}")
    assertThat(fixResult).task(":${FixSpotlightProjectListTask.NAME}").succeeded()

    // Then: Check should now succeed
    val checkResult = project.build(":${CheckSpotlightProjectListTask.NAME}")
    assertThat(checkResult).task(":${CheckSpotlightProjectListTask.NAME}").succeeded()
  }
}
