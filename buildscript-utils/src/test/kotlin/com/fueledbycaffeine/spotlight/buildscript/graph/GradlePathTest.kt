package com.fueledbycaffeine.spotlight.buildscript.graph

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.gradlePathRelativeTo
import com.fueledbycaffeine.spotlight.buildscript.minimize
import java.io.FileNotFoundException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import org.gradle.internal.scripts.ScriptingLanguages
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class GradlePathTest {
  @TempDir lateinit var buildRoot: Path

  @Test fun `can convert gradle paths to filesystem paths`() {
    val gradlePath = GradlePath(buildRoot, ":foo:bar")
    val projectDir = buildRoot.resolve("foo/bar")
    assertThat(gradlePath.projectDir).equals(projectDir)
  }

  @ParameterizedTest
  @ValueSource(strings = [".gradle", ".gradle.kts"])
  fun `can find the buildscript`(extension: String) {
    val gradlePath = GradlePath(buildRoot, ":foo:bar")
    val projectDir = buildRoot.resolve("foo/bar")
    val buildScriptPath = projectDir.createBuildFile(extension)
    assertThat(gradlePath.buildFilePath).equals(buildScriptPath)
  }

  @ParameterizedTest
  @ValueSource(strings = [".gradle", ".gradle.kts"])
  fun `can find the settings script`(extension: String) {
    val gradlePath = GradlePath(buildRoot, ":")
    buildRoot.createSettingsFile(extension)
    assertThat(gradlePath.hasSettingsFile).isTrue()
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

  @Test fun `can get parent path`() {
    val fooBar = GradlePath(buildRoot, ":foo:bar")
    val foo = GradlePath(buildRoot, ":foo")
    val root = GradlePath(buildRoot, ":")
    assertThat(fooBar.parent).isEqualTo(foo)
    assertThat(foo.parent).isEqualTo(root)
    assertThat(root.parent).isNull()
  }

  @Test fun `can tell if it is root project`() {
    val foo = GradlePath(buildRoot, ":foo")
    val root = GradlePath(buildRoot, ":")
    assertThat(foo.isRootProject).isFalse()
    assertThat(root.isRootProject).isTrue()
  }

  @Test fun `minimize removes nested paths when parent is present`() {
    val paths = listOf(
      GradlePath(buildRoot, ":foo"),
      GradlePath(buildRoot, ":foo:bar"),
      GradlePath(buildRoot, ":foo:bar:baz"),
      GradlePath(buildRoot, ":other")
    )
    
    val minimized = paths.minimize()
    
    assertThat(minimized).containsExactlyInAnyOrder(
      GradlePath(buildRoot, ":foo"),
      GradlePath(buildRoot, ":other")
    )
  }

  @Test fun `minimize preserves non-overlapping paths`() {
    val paths = listOf(
      GradlePath(buildRoot, ":foo"),
      GradlePath(buildRoot, ":bar"),
      GradlePath(buildRoot, ":baz")
    )
    
    val minimized = paths.minimize()
    
    assertThat(minimized).containsExactlyInAnyOrder(
      GradlePath(buildRoot, ":foo"),
      GradlePath(buildRoot, ":bar"),
      GradlePath(buildRoot, ":baz")
    )
  }

  @Test fun `minimize handles empty collection`() {
    val paths = emptyList<GradlePath>()
    
    val minimized = paths.minimize()
    
    assertThat(minimized).isEmpty()
  }

  @Test fun `minimize handles single path`() {
    val paths = listOf(GradlePath(buildRoot, ":foo"))
    
    val minimized = paths.minimize()
    
    assertThat(minimized).containsOnly(GradlePath(buildRoot, ":foo"))
  }

  @Test fun `minimize works with unsorted input`() {
    val paths = listOf(
      GradlePath(buildRoot, ":foo:bar:baz"),
      GradlePath(buildRoot, ":foo"),
      GradlePath(buildRoot, ":foo:bar"),
      GradlePath(buildRoot, ":other:nested")
    )
    
    val minimized = paths.minimize()
    
    assertThat(minimized).containsExactlyInAnyOrder(
      GradlePath(buildRoot, ":foo"),
      GradlePath(buildRoot, ":other:nested")
    )
  }

  @Test fun `minimize handles root project with nested paths`() {
    val paths = listOf(
      GradlePath(buildRoot, ":"),
      GradlePath(buildRoot, ":foo"),
      GradlePath(buildRoot, ":foo:bar")
    )
    
    val minimized = paths.minimize()
    
    assertThat(minimized).containsOnly(GradlePath(buildRoot, ":"))
  }

  @Test fun `minimize preserves paths with similar but non-prefixed names`() {
    val paths = listOf(
      GradlePath(buildRoot, ":foo"),
      GradlePath(buildRoot, ":foobar"),
      GradlePath(buildRoot, ":foo-bar")
    )
    
    val minimized = paths.minimize()
    
    assertThat(minimized).containsExactlyInAnyOrder(
      GradlePath(buildRoot, ":foo"),
      GradlePath(buildRoot, ":foo-bar"),
      GradlePath(buildRoot, ":foobar")
    )
  }

  @Test fun `minimize works with complex nested hierarchy`() {
    val paths = listOf(
      GradlePath(buildRoot, ":app"),
      GradlePath(buildRoot, ":app:ui"),
      GradlePath(buildRoot, ":app:ui:components"),
      GradlePath(buildRoot, ":lib"),
      GradlePath(buildRoot, ":lib:core"),
      GradlePath(buildRoot, ":lib:utils"),
      GradlePath(buildRoot, ":tools")
    )
    
    val minimized = paths.minimize()
    
    assertThat(minimized).containsExactlyInAnyOrder(
      GradlePath(buildRoot, ":app"),
      GradlePath(buildRoot, ":lib"),
      GradlePath(buildRoot, ":tools")
    )
  }

  private fun Path.createBuildFile(extension: String = ".gradle"): Path {
    this.createDirectories()
    return this.resolve("build$extension").apply { createFile() }
  }

  private fun Path.createSettingsFile(extension: String = ".gradle"): Path {
    this.createDirectories()
    return this.resolve("settings$extension").apply { createFile() }
  }
}
