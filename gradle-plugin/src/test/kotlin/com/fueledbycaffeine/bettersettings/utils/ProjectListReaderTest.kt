package com.fueledbycaffeine.bettersettings.utils

import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class ProjectListReaderTest {
  @TempDir lateinit var buildRoot: Path

  @Test fun `can read project list`() {
    val projectListFile = buildRoot.resolve("projects.txt")
    projectListFile.writeText("""
      :foo
      :foo:bar
      :foo:bar-baz
    """.trimIndent())

    val projects = buildRoot.readProjectList(projectListFile)
    assertThat(projects).containsExactly(
      GradlePath(buildRoot, ":foo"),
      GradlePath(buildRoot, ":foo:bar"),
      GradlePath(buildRoot, ":foo:bar-baz"),
    )
  }

  @Test fun `ignores comments`() {
    val projectListFile = buildRoot.resolve("projects.txt")
    projectListFile.writeText("""
      # A comment
      :foo
    """.trimIndent())

    val projects = buildRoot.readProjectList(projectListFile)
    assertThat(projects).containsExactly(
      GradlePath(buildRoot, ":foo"),
    )
  }

  @Test fun `ignores empty lines`() {
    val projectListFile = buildRoot.resolve("projects.txt")
    projectListFile.writeText("""
      :foo
      
      :foo:bar
    """.trimIndent())

    val projects = buildRoot.readProjectList(projectListFile)
    assertThat(projects).containsExactly(
      GradlePath(buildRoot, ":foo"),
      GradlePath(buildRoot, ":foo:bar"),
    )
  }

  @Test fun `ignores duplicates`() {
    val projectListFile = buildRoot.resolve("projects.txt")
    projectListFile.writeText("""
      :foo
      :foo
    """.trimIndent())

    val projects = buildRoot.readProjectList(projectListFile)
    assertThat(projects).containsExactly(
      GradlePath(buildRoot, ":foo"),
    )
  }
}