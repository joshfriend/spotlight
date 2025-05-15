package com.fueledbycaffeine.spotlight.buildscript

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.FileNotFoundException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText

class BuildFileTest {
  @TempDir lateinit var buildRoot: Path

  @Test fun `reads dependencies`() {
    val project = buildRoot.createProject(":foo")
    project.buildFilePath.writeText("""
      dependencies {
        implementation  project(":multiple-spaces-double-quotes")
        implementation  project(':multiple-spaces-single-quotes')

        implementation project(":one-space-double-quotes")
        implementation project(':one-space-single-quotes')

        implementation(project(":parentheses-double-quotes"))
        implementation(project(':parentheses-single-quotes'))

        api(project(':other-configuration')) {
          because "reason"
        }
       
          implementation project(':bad-indentation')

        // implementation(project(':commented'))
      }
      """.trimIndent()
    )

    val buildFile = BuildFile(project)

    assertThat(buildFile.parseDependencies()).containsExactlyInAnyOrder(
      GradlePath(buildRoot, ":multiple-spaces-double-quotes"),
      GradlePath(buildRoot, ":multiple-spaces-single-quotes"),
      GradlePath(buildRoot, ":one-space-double-quotes"),
      GradlePath(buildRoot, ":one-space-single-quotes"),
      GradlePath(buildRoot, ":parentheses-double-quotes"),
      GradlePath(buildRoot, ":parentheses-single-quotes"),
      GradlePath(buildRoot, ":other-configuration"),
      GradlePath(buildRoot, ":bad-indentation"),
    )
  }

  @Test fun `reads known type-safe project accessor dependencies`() {
    val project = buildRoot.createProject(":foo")
    val typeSafeProject = GradlePath(buildRoot, ":type-safe:project")
    typeSafeProject.projectDir.createDirectories()
    typeSafeProject.projectDir.resolve("build.gradle").createFile()
    project.buildFilePath.writeText("""
      dependencies {
        implementation projects.typeSafe.project
      }
      """.trimIndent()
    )
    val buildFile = BuildFile(project)
    val rule = TypeSafeProjectAccessorRule("spotlight", mapOf("typeSafe.project" to typeSafeProject))
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProject)
  }

  @Test fun `reads type-safe project accessor dependencies that use explicit root project`() {
    val project = buildRoot.createProject(":foo")
    val typeSafeProject = GradlePath(buildRoot, ":type-safe:project")
    typeSafeProject.projectDir.createDirectories()
    typeSafeProject.projectDir.resolve("build.gradle").createFile()
    project.buildFilePath.writeText("""
      dependencies {
        implementation projects.spotlight.typeSafe.project
      }
      """.trimIndent()
    )
    val buildFile = BuildFile(project)
    val rule = TypeSafeProjectAccessorRule("spotlight", mapOf("typeSafe.project" to typeSafeProject))
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProject)
  }

  @Test fun `throws error when type-safe accessor is unknown`() {
    val project = buildRoot.createProject(":foo")
    project.buildFilePath.writeText("""
      dependencies {
        implementation projects.typeSafe.project
      }
      """.trimIndent()
    )
    val buildFile = BuildFile(project)
    val rule = TypeSafeProjectAccessorRule("spotlight", mapOf())
    assertThrows<FileNotFoundException> {
      buildFile.parseDependencies(setOf(rule))
    }
  }

  @Test fun `parses implicit dependencies based on project path`() {
    val project = buildRoot.createProject(":features:something")
    project.buildFilePath.writeText(
      """
      dependencies {
        debugImplementation project(":foo")
      }
      """.trimIndent()
    )

    val buildFile = BuildFile(project)

    val rules = setOf<ImplicitDependencyRule>(
      ProjectPathMatchRule(Regex(":features:.*"), setOf(GradlePath(buildRoot, ":bar"))),
    )
    assertThat(buildFile.parseDependencies(rules))
      .containsExactlyInAnyOrder(
        GradlePath(buildRoot, ":foo"),
        GradlePath(buildRoot, ":bar"),
      )
  }

  @Test fun `parses implicit dependencies based on buildscript contents`() {
    val project = buildRoot.createProject(":features:something")
    project.buildFilePath.writeText(
      """
      plugins {
        id 'com.example.feature'
      }

      dependencies {
        debugImplementation project(":foo")
      }
      """.trimIndent()
    )

    val buildFile = BuildFile(project)

    val rules = setOf<ImplicitDependencyRule>(
      BuildscriptMatchRule(Regex("id 'com.example.feature'"), setOf(GradlePath(buildRoot, ":bar"))),
    )
    assertThat(buildFile.parseDependencies(rules))
      .containsExactlyInAnyOrder(
        GradlePath(buildRoot, ":foo"),
        GradlePath(buildRoot, ":bar"),
      )
  }

  @Test fun `ignores duplicates`() {
    val project = buildRoot.createProject(":foo")
    project.buildFilePath.writeText(
      """
      dependencies {
        debugImplementation project(":foo")
        debugImplementation project(":foo")
      }
      """.trimIndent()
    )

    val buildFile = BuildFile(project)

    assertThat(buildFile.parseDependencies()).containsExactlyInAnyOrder(
      GradlePath(buildRoot, ":foo")
    )
  }

  private fun Path.createProject(path: String): GradlePath {
    return GradlePath(this, path).apply {
      projectDir.createDirectories()
      projectDir.resolve("build.gradle").createFile()
    }
  }
}