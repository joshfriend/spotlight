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
  fun `read returns correct projects`() {
    val projectListFile = buildRoot.resolve("projects.txt")
    buildRoot.createProjectList(
      ":foo",
    )
    projectListFile.writeText("""
      :foo
    """.trimIndent())

    val present = GradlePath(buildRoot, ":foo")
    val missing = GradlePath(buildRoot, ":bar")
    val projects = AllProjects(buildRoot, projectListFile).read()
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
  fun `IdeProjects supports recursive glob patterns`() {
    val ideProjectListFile = buildRoot.resolve("ide-projects.txt")
    val allProjectsList = buildRoot.createProjectList(
      ":libraries:core",
      ":libraries:ui:components:text",
      ":libraries:ui:components:buttons",
      ":libraries:data:api",
      ":apps:main"
    )
    ideProjectListFile.writeText("""
      :libraries:ui:**
      :apps:main
    """.trimIndent())

    val projects = IdeProjects(buildRoot, ideProjectListFile, allProjectsList::toSet).read()
    val expectedProjects = allProjectsList.filter {
      it.path.startsWith(":libraries:ui") || it.path == ":apps:main"
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

  @Test
  fun `remove only removes exact matches`() {
    val ideProjectListFile = buildRoot.resolve("ide-projects.txt")
    val allProjectsList = buildRoot.createProjectList(
      ":advertising:core",
      ":advertising:ui",
      ":other:project"
    )
    ideProjectListFile.writeText("""
      :advertising:**
      :other:project
    """.trimIndent())

    val projects = IdeProjects(buildRoot, ideProjectListFile, allProjectsList::toSet)
    // Remove the exact wildcard pattern
    projects.remove(listOf(GradlePath(buildRoot, ":advertising:**")))

    val updatedFileContents = ideProjectListFile.readText()
    assertThat(updatedFileContents).equals(":other:project\n")
  }

  @Test
  fun `remove does not remove different pattern`() {
    val ideProjectListFile = buildRoot.resolve("ide-projects.txt")
    val allProjectsList = buildRoot.createProjectList(
      ":advertising:core",
      ":advertising:ui",
      ":other:project"
    )
    ideProjectListFile.writeText("""
      :advertising:**
      :other:project
    """.trimIndent())

    val projects = IdeProjects(buildRoot, ideProjectListFile, allProjectsList::toSet)
    // Try to remove a child project (not in file, only covered by wildcard)
    projects.remove(listOf(GradlePath(buildRoot, ":advertising:core")))

    val updatedFileContents = ideProjectListFile.readText()
    // Should NOT remove anything since :advertising:core is not literally in the file
    assertThat(updatedFileContents).equals(":advertising:**\n:other:project\n")
  }

  @Test
  fun `add prevents exact duplicates only`() {
    val ideProjectListFile = buildRoot.resolve("ide-projects.txt")
    buildRoot.createProjectList(
      ":foo",
      ":bar"
    )
    ideProjectListFile.writeText("""
      :foo
    """.trimIndent())

    val projects = IdeProjects(buildRoot, ideProjectListFile)
    // Try to add :foo again
    projects.add(listOf(GradlePath(buildRoot, ":foo")))

    val updatedFileContents = ideProjectListFile.readText()
    // Should not have added duplicate
    assertThat(updatedFileContents).equals(":foo\n")
  }

  @Test
  fun `add allows overlapping patterns`() {
    val ideProjectListFile = buildRoot.resolve("ide-projects.txt")
    buildRoot.createProjectList(
      ":advertising",
      ":advertising:backend",
      ":advertising:core"
    )
    ideProjectListFile.writeText("""
      :advertising:**
    """.trimIndent())

    val projects = IdeProjects(buildRoot, ideProjectListFile)
    // Add specific project even though wildcard exists
    projects.add(listOf(GradlePath(buildRoot, ":advertising:backend")))

    val updatedFileContents = ideProjectListFile.readText()
    // Should have added it even though covered by wildcard
    assertThat(updatedFileContents).equals(":advertising:**\n:advertising:backend\n")
  }

  @Test
  fun `add allows parent and wildcard to coexist`() {
    val ideProjectListFile = buildRoot.resolve("ide-projects.txt")
    buildRoot.createProjectList(
      ":advertising",
      ":advertising:core"
    )
    ideProjectListFile.writeText("""
      :advertising
    """.trimIndent())

    val projects = IdeProjects(buildRoot, ideProjectListFile)
    // Add wildcard even though parent exists
    projects.add(listOf(GradlePath(buildRoot, ":advertising:**")))

    val updatedFileContents = ideProjectListFile.readText()
    // Should have added wildcard
    assertThat(updatedFileContents).equals(":advertising\n:advertising:**\n")
  }

  @Test
  fun `contains only checks exact match`() {
    val ideProjectListFile = buildRoot.resolve("ide-projects.txt")
    buildRoot.createProjectList(
      ":advertising",
      ":advertising:backend"
    )
    ideProjectListFile.writeText("""
      :advertising:**
      :advertising:backend
    """.trimIndent())

    val projects = IdeProjects(buildRoot, ideProjectListFile)
    // Exact match for wildcard
    assertThat(GradlePath(buildRoot, ":advertising:**") in projects).isTrue()
    // Exact match for specific project
    assertThat(GradlePath(buildRoot, ":advertising:backend") in projects).isTrue()
    // No exact match for parent (only wildcard exists)
    assertThat(GradlePath(buildRoot, ":advertising") in projects).isFalse()
    // No exact match for other child
    assertThat(GradlePath(buildRoot, ":advertising:frontend") in projects).isFalse()
  }

  @Test
  fun `add child project when parent wildcard exists`() {
    val ideProjectListFile = buildRoot.resolve("ide-projects.txt")
    buildRoot.createProjectList(
      ":advertising",
      ":advertising:backend",
      ":advertising:frontend"
    )
    ideProjectListFile.writeText("""
      :advertising:**
    """.trimIndent())

    val projects = IdeProjects(buildRoot, ideProjectListFile)
    // Add specific backend project
    projects.add(listOf(GradlePath(buildRoot, ":advertising:backend")))

    val updatedFileContents = ideProjectListFile.readText()
    // Should have both the wildcard and the specific project
    assertThat(updatedFileContents).equals(":advertising:**\n:advertising:backend\n")
  }

  @Test
  fun `remove child project does not affect parent wildcard`() {
    val ideProjectListFile = buildRoot.resolve("ide-projects.txt")
    buildRoot.createProjectList(
      ":advertising",
      ":advertising:backend",
      ":advertising:frontend"
    )
    ideProjectListFile.writeText("""
      :advertising:**
      :advertising:backend
    """.trimIndent())

    val projects = IdeProjects(buildRoot, ideProjectListFile)
    // Remove the specific backend project
    projects.remove(listOf(GradlePath(buildRoot, ":advertising:backend")))

    val updatedFileContents = ideProjectListFile.readText()
    // Should only remove the specific project, not the wildcard
    assertThat(updatedFileContents).equals(":advertising:**\n")
  }

  @Test
  fun `contains with multiple overlapping patterns`() {
    val ideProjectListFile = buildRoot.resolve("ide-projects.txt")
    buildRoot.createProjectList(
      ":advertising",
      ":advertising:service",
      ":advertising:service:backend"
    )
    ideProjectListFile.writeText("""
      :advertising:**
      :advertising:service:**
    """.trimIndent())

    val projects = IdeProjects(buildRoot, ideProjectListFile)
    // Check what actually exists in the file
    assertThat(GradlePath(buildRoot, ":advertising:**") in projects).isTrue()
    assertThat(GradlePath(buildRoot, ":advertising:service:**") in projects).isTrue()
    // The parent path itself is not in the file
    assertThat(GradlePath(buildRoot, ":advertising") in projects).isFalse()
    assertThat(GradlePath(buildRoot, ":advertising:service") in projects).isFalse()
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