package com.fueledbycaffeine.spotlight.buildscript.graph

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.each
import assertk.assertions.isEqualTo
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.gradlePathRelativeTo
import org.gradle.internal.scripts.ScriptingLanguages
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.FileNotFoundException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.math.exp

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
    val buildScriptPath = projectDir.createBuildFile(extension)
    assertThat(gradlePath.buildFilePath).equals(buildScriptPath)
  }

  @Test fun `can find the dominant buildscript`() {
    val gradlePath = GradlePath(buildRoot, ":foo:bar")
    val projectDir = buildRoot.resolve("foo/bar")
    projectDir.createDirectories()
    val buildscripts = ScriptingLanguages.all().map { lang ->
      val buildScriptPath = projectDir.resolve("build${lang.extension}")
      buildScriptPath.createFile()
    }
    assertThat(gradlePath.buildFilePath).equals(buildscripts.first())
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

    assertThat(gradlePath.findSuccessors(emptySet())).containsExactlyInAnyOrder(
      GradlePath(buildRoot, ":bar"),
    )
  }

  @Test fun `gradlePathRelativeTo converts file to GradlePath`() {
    val projectDir = buildRoot.resolve("foo")
    val gradlePath = projectDir.toFile().gradlePathRelativeTo(buildRoot.toFile())
    assertThat(gradlePath.path).isEqualTo(":foo")
    assertThat(gradlePath.root).isEqualTo(buildRoot)
  }

  @Test fun `typeSafeProjectAccessor conversion`() {
    val projectPaths = mapOf(
      ":foo" to "foo",
      ":foo:bar" to "foo.bar",
      ":kebab-case" to "kebabCase",
      ":snake_case" to "snakeCase",
      ":UpperCase" to "UpperCase",
      ":ALL_CAPS" to "ALLCAPS",
      ":a-b_c-d_e:f-g_h-i_j" to "aBCDE:fGHIJ",
    )
    val convertedPaths = projectPaths.keys.map { path ->
      GradlePath(buildRoot, path).typeSafeAccessorName
    }
    projectPaths.values.zip(convertedPaths).forEach { (actual, expected) ->
      assertThat(actual).equals(expected)
    }
  }

  private fun Path.createBuildFile(extension: String = ".gradle"): Path {
    this.createParentDirectories()
    this.createDirectory()
    return this.resolve("build$extension").apply { createFile() }
  }
}
