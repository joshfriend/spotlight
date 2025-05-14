package com.fueledbycaffeine.spotlight.utils

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories

class GradlePathExtTest {
  @TempDir
  lateinit var buildRoot: Path

  @Test
  fun `expandChildProjects returns child projects`() {
    val projectDir = buildRoot.resolve("foo")
    val gradlePath = GradlePath(buildRoot, ":foo")
    projectDir.resolve("bar").createBuildFile()
    projectDir.resolve("bar/baz").createBuildFile()
    projectDir.resolve("ignored/build").createBuildFile()
    projectDir.resolve("ignored/src").createBuildFile()

    val childProjects = gradlePath.expandChildProjects()
    assertThat(childProjects).containsExactlyInAnyOrder(
      GradlePath(buildRoot, ":foo:bar"),
      GradlePath(buildRoot, ":foo:bar:baz"),
    )
  }

  private fun Path.createBuildFile(extension: String = ".gradle"): Path {
    this.createParentDirectories()
    this.createDirectory()
    return this.resolve("build$extension").apply { createFile() }
  }
}