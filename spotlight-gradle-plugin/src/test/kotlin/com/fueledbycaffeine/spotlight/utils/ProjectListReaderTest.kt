package com.fueledbycaffeine.spotlight.utils

import assertk.assertThat
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.readProjectList
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.FileNotFoundException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText

class ProjectListReaderTest {
  @TempDir lateinit var buildRoot: Path

  @Test fun `can read project list`() {
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

  @Test fun `throws error if path is invalid`() {
    val projectListFile = buildRoot.resolve("projects.txt")
    projectListFile.writeText("""
      :nothing
    """.trimIndent())

    assertThrows<FileNotFoundException> {
      buildRoot.readProjectList(projectListFile)
    }
  }

  @Test fun `ignores comments`() {
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

  @Test fun `ignores empty lines`() {
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

  @Test fun `ignores duplicates`() {
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