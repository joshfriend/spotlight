package com.fueledbycaffeine.spotlight.graph

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import com.fueledbycaffeine.spotlight.utils.GradlePath
import com.fueledbycaffeine.spotlight.utils.expandChildProjects
import com.fueledbycaffeine.spotlight.utils.gradlePathRelativeTo
import org.gradle.internal.scripts.ScriptingLanguages
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.FileNotFoundException
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

class GradlePathTest {
  @TempDir lateinit var buildRoot: Path

  @Test fun `can convert gradle paths to filesystem paths`() {
    val gradlePath = GradlePath(buildRoot, ":foo:bar")
    val projectDir = buildRoot.resolve("foo/bar")
    assertThat(gradlePath.projectDir).equals(projectDir)
  }

  @ParameterizedTest
  @ValueSource(strings = [".gradle", ".gradle.kts"])
  fun `can find the groovy buildscript`(extension: String) {
    val gradlePath = GradlePath(buildRoot, ":foo:bar")
    val projectDir = buildRoot.resolve("foo/bar")
    val buildsScriptPath = projectDir.createBuildFile(extension)
    assertThat(gradlePath.buildFilePath).equals(buildsScriptPath)
  }

  @Test fun `can find the dominant buildscript`() {
    val gradlePath = GradlePath(buildRoot, ":foo:bar")
    val projectDir = buildRoot.resolve("foo/bar")
    val defaultScriptingLanguage = ScriptingLanguages.all().first()
    val buildsScriptPath = projectDir.resolve("build${defaultScriptingLanguage.extension}")
    buildsScriptPath.createParentDirectories()
    buildsScriptPath.createFile()
    assertThat(gradlePath.buildFilePath).equals(buildsScriptPath)
  }

  @Test fun `throws error if no buildscript is found`() {
    val gradlePath = GradlePath(buildRoot, ":foo:bar")
    assertThrows<FileNotFoundException>("No build.gradle(.kts) for :foo:bar found") { gradlePath.buildFilePath }
  }

  @Test fun `can list other project dependencies`() {
    val gradlePath = GradlePath(buildRoot, ":foo")
    val projectDir = buildRoot.resolve("foo")
    val buildScriptPath = projectDir.createBuildFile()
    buildScriptPath.writeText("""
      dependencies {
        implementation project(':bar')
      }
    """.trimIndent())

    assertThat(gradlePath.successors).containsExactlyInAnyOrder(
      GradlePath(buildRoot, ":bar"),
    )
  }

  @Test fun `gradlePathRelativeTo converts file to GradlePath`() {
    val projectDir = buildRoot.resolve("foo")
    val gradlePath = projectDir.toFile().gradlePathRelativeTo(buildRoot.toFile())
    assertThat(gradlePath.path).isEqualTo(":foo")
    assertThat(gradlePath.root).isEqualTo(buildRoot)
  }

  @Test fun `expandChildProjects returns child projects`() {
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