package com.fueledbycaffeine.spotlight.buildscript

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.readProjectList
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.FileNotFoundException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

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

    val projects = buildRoot.readProjectList(projectListFile)
    assertThat(projects).equals(expectedProjects)
  }

  @Test
  fun `throws error if path is invalid`() {
    val projectListFile = buildRoot.resolve("projects.txt")
    projectListFile.writeText("""
      :nothing
    """.trimIndent())

    assertThrows<FileNotFoundException> {
      buildRoot.readProjectList(projectListFile)
    }
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

    val projects = buildRoot.readProjectList(projectListFile)
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

    val projects = buildRoot.readProjectList(projectListFile)
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

    val projects = buildRoot.readProjectList(projectListFile)
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
    val projects = SpotlightProjectList(buildRoot, projectListFile)
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
    val projects = SpotlightProjectList(buildRoot, projectListFile)
    projects.add(missing)
    val updatedFileContents = projectListFile.readText()
    assertThat(updatedFileContents).equals(":foo\n:bar\n")
  }

  @Test
  fun `throws error when paths are invalid`() {
    val projectListFile = buildRoot.resolve("projects.txt")
    buildRoot.createProjectList(
      ":foo",
    )
    projectListFile.writeText("""
      :foo
      :bar
      :baz
    """.trimIndent())

    val exception = assertThrows<FileNotFoundException> {
      buildRoot.readProjectList(projectListFile)
    }
    assertThat(exception.message).isNotNull().all {
      contains(":bar")
      contains(":baz")
    }
  }

  @Test
  fun `can read settings dot gradle`() {
    val settingsGradle = buildRoot.resolve("settings.gradle")
    val expectedProjects = buildRoot.createProjectList(
      ":foo",
      ":bar",
      ":this",
      ":and:this",
      ":also:this",
    )
    settingsGradle.writeText(
      """
      if (foo) {
        include ':foo'
        include  ":bar" // because
      }
      // include ':not:this'
      include ':this'
      include  ":and:this"
      include(":also:this")
      """.trimIndent()
    )
    val allProjects = buildRoot.readProjectList(settingsGradle)
    assertThat(allProjects).equals(expectedProjects)
  }

  @Test
  fun `can read non-standard settings dot gradle`() {
    val settingsGradle = buildRoot.resolve("settings_modules_all.gradle")
    val expectedProjects = buildRoot.createProjectList(
      ":foo",
      ":bar",
      ":this",
      ":and:this",
      ":also:this",
    )
    settingsGradle.writeText(
      """
      if (foo) {
        include ':foo'
        include  ":bar" // because
      }
      // include ':not:this'
      include ':this'
      include  ":and:this"
      include(":also:this")
      """.trimIndent()
    )
    val allProjects = buildRoot.readProjectList(settingsGradle)
    assertThat(allProjects).equals(expectedProjects)
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