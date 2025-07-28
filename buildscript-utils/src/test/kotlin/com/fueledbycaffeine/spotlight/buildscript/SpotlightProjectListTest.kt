package com.fueledbycaffeine.spotlight.buildscript

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SpotlightProjectListTest {
  @TempDir
  lateinit var buildRoot: Path

  @Test
  fun `can read project list`() {
    val projectListFile = buildRoot.resolve("projects.txt")
    val expectedProjects = buildRoot.createProjectList(
      ":foo",
      ":foo:bar",
      ":foo:bar-baz",
    )
    projectListFile.writeText(expectedProjects.joinToString("\n") { it.path })

    val projects = AllProjects(buildRoot, projectListFile).read()
    assertThat(projects).equals(expectedProjects)
  }

  @Test
  fun `ignores comments`() {
    val projectListFile = buildRoot.resolve("projects.txt")
    val expectedProjects = buildRoot.createProjectList(
      ":foo",
    )
    projectListFile.writeText("""
      # A comment
      :foo
    """.trimIndent())

    val projects = AllProjects(buildRoot, projectListFile).read()
    assertThat(projects).equals(expectedProjects)
  }

  @Test
  fun `ignores empty lines`() {
    val projectListFile = buildRoot.resolve("projects.txt")
    val expectedProjects = buildRoot.createProjectList(
      ":foo",
      ":foo:bar",
    )
    projectListFile.writeText("""
      :foo
      
      :foo:bar
    """.trimIndent())

    val projects = AllProjects(buildRoot, projectListFile).read()
    assertThat(projects).equals(expectedProjects)
  }

  @Test
  fun `ignores duplicates`() {
    val projectListFile = buildRoot.resolve("projects.txt")
    val expectedProjects = buildRoot.createProjectList(
      ":foo",
    )
    projectListFile.writeText("""
      :foo
      :foo
    """.trimIndent())

    val projects = AllProjects(buildRoot, projectListFile).read()
    assertThat(projects).equals(expectedProjects)
  }

  @Test
  fun `contains project is valid`() {
    val projectListFile = buildRoot.resolve("projects.txt")
    buildRoot.createProjectList(
      ":foo",
    )
    projectListFile.writeText("""
      :foo
    """.trimIndent())

    val present = GradlePath(buildRoot, ":foo")
    val missing = GradlePath(buildRoot, ":bar")
    val projects = AllProjects(buildRoot, projectListFile)
    assertThat(projects.contains(missing)).isFalse()
    assertThat(projects.contains(present)).isTrue()
  }

  @Test
  fun `add writes updated list to file`() {
    val projectListFile = buildRoot.resolve("projects.txt")
    buildRoot.createProjectList(
      ":foo",
      ":bar",
    )
    projectListFile.writeText("""
      :foo
    """.trimIndent())

    val missing = GradlePath(buildRoot, ":bar")
    val projects = IdeProjects(buildRoot, projectListFile)
    projects.add(listOf(missing))
    val updatedFileContents = projectListFile.readText()
    assertThat(updatedFileContents).equals(":foo\n:bar\n")
  }

  @Test
  fun `IdeProjects supports glob patterns`() {
    val ideProjectListFile = buildRoot.resolve("ide-projects.txt")
    val allProjectsList = buildRoot.createProjectList(
      ":libraries:core",
      ":libraries:ui",
      ":libraries:data",
      ":apps:main",
      ":apps:sample"
    )
    ideProjectListFile.writeText("""
      :libraries:*
      :apps:main
    """.trimIndent())

    val projects = IdeProjects(buildRoot, ideProjectListFile, allProjectsList::toSet).read()
    val expectedProjects = allProjectsList.filter {
      it.path in setOf(":libraries:core",
        ":libraries:ui",
        ":libraries:data",
        ":apps:main")
    }
    assertThat(projects).equals(expectedProjects.toSet())
  }

  @Test
  fun `IdeProjects filters non-existent projects when allProjects provided`() {
    val ideProjectListFile = buildRoot.resolve("ide-projects.txt")
    val allProjectsList = buildRoot.createProjectList(
      ":foo",
      ":bar"
    )
    ideProjectListFile.writeText("""
      :foo
      :baz
    """.trimIndent())

    val projects = IdeProjects(buildRoot, ideProjectListFile, allProjectsList::toSet).read()
    val expectedProjects = allProjectsList.filter { it.path == ":foo" }
    assertThat(projects).equals(expectedProjects.toSet())
  }

  @Test
  fun `IdeProjects works without allProjects`() {
    val ideProjectListFile = buildRoot.resolve("ide-projects.txt")
    val projects = buildRoot.createProjectList(
      ":foo",
      ":bar"
    )
    ideProjectListFile.writeText("""
      :foo
      :bar
    """.trimIndent())

    val ideProjects = IdeProjects(buildRoot, ideProjectListFile, null).read()
    assertThat(ideProjects).equals(projects.toSet())
  }

  private fun Path.createProjectList(vararg projects: String): List<GradlePath> {
    return projects.map { project ->
      resolve(project).apply { createDirectories() }
      GradlePath(this, project).apply {
        projectDir.createDirectories()
        projectDir.resolve("build.gradle").createFile()
      }
    }
  }
}