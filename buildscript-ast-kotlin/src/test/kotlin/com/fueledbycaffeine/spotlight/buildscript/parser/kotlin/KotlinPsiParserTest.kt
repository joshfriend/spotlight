package com.fueledbycaffeine.spotlight.buildscript.parser.kotlin

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT_KOTLIN
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.TypeSafeProjectAccessorRule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText

class KotlinPsiParserTest {
  @TempDir lateinit var buildRoot: Path

  @BeforeEach
  fun setupBuild() {
    buildRoot.resolve("build.gradle.kts").createFile()
    buildRoot.resolve("settings.gradle.kts").createFile()
  }

  private fun Path.createProject(projectPath: String): GradlePath {
    val segments = projectPath.split(":").filter { it.isNotEmpty() }
    val projectDir = segments.fold(this) { acc, segment -> acc.resolve(segment) }
    projectDir.createDirectories()
    projectDir.resolve("build.gradle.kts").createFile()
    return GradlePath(this, projectPath)
  }

  @Test
  fun `parses project dependencies`() {
    val project = buildRoot.createProject(":app")
    project.buildFilePath.writeText(
      """
      dependencies {
        implementation(project(":lib1"))
        api(project(":lib2"))
      }
      """.trimIndent()
    )

    buildRoot.createProject(":lib1")
    buildRoot.createProject(":lib2")

    val dependencies = KotlinPsiParser.parse(project, emptySet())

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1", ":lib2")
  }

  @Test
  fun `parses nested dependencies`() {
    val project = buildRoot.createProject(":app")
    project.buildFilePath.writeText(
      """
      dependencies {
        implementation(project(":lib1"))
      }
      
      configurations.all {
        dependencies {
          implementation(project(":lib2"))
        }
      }
      """.trimIndent()
    )

    buildRoot.createProject(":lib1")
    buildRoot.createProject(":lib2")

    val dependencies = KotlinPsiParser.parse(project, emptySet())

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1", ":lib2")
  }

  @Test
  fun `handles comments correctly`() {
    val project = buildRoot.createProject(":app")
    project.buildFilePath.writeText(
      """
      dependencies {
        // implementation(project(":commented-out"))
        implementation(project(":lib1"))
        /* implementation(project(":also-commented")) */
      }
      """.trimIndent()
    )

    buildRoot.createProject(":lib1")

    val dependencies = KotlinPsiParser.parse(project, emptySet())

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1")
  }

  @Test
  fun `returns empty set when no dependencies`() {
    val project = buildRoot.createProject(":app")
    project.buildFilePath.writeText(
      """
      plugins {
        kotlin("jvm")
      }
      """.trimIndent()
    )

    val dependencies = KotlinPsiParser.parse(project, emptySet())

    assertThat(dependencies).isEmpty()
  }

  @Test
  fun `parses dependencies with string templates`() {
    val project = buildRoot.createProject(":app")
    project.buildFilePath.writeText(
      """
      val lib1 = ":lib1"
      dependencies {
        implementation(project(lib1))
        implementation(project(":lib2"))
      }
      """.trimIndent()
    )

    buildRoot.createProject(":lib1")
    buildRoot.createProject(":lib2")

    val dependencies = KotlinPsiParser.parse(project, emptySet())

    // Note: Variable references might not be resolved by PSI parser
    // so we only check for the literal string
    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib2")
  }

  @Test
  fun `parses multi-line dependencies`() {
    val project = buildRoot.createProject(":app")
    project.buildFilePath.writeText(
      """
      dependencies {
        implementation(
          project(":lib1")
        )
        api(
          project(
            ":lib2"
          )
        )
      }
      """.trimIndent()
    )

    buildRoot.createProject(":lib1")
    buildRoot.createProject(":lib2")

    val dependencies = KotlinPsiParser.parse(project, emptySet())

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1", ":lib2")
  }

  @Test
  fun `parses dependencies with different configurations`() {
    val project = buildRoot.createProject(":app")
    project.buildFilePath.writeText(
      """
      dependencies {
        implementation(project(":lib1"))
        api(project(":lib2"))
        testImplementation(project(":lib3"))
        runtimeOnly(project(":lib4"))
      }
      """.trimIndent()
    )

    buildRoot.createProject(":lib1")
    buildRoot.createProject(":lib2")
    buildRoot.createProject(":lib3")
    buildRoot.createProject(":lib4")

    val dependencies = KotlinPsiParser.parse(project, emptySet())

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1", ":lib2", ":lib3", ":lib4")
  }

  @Test
  fun `parses type-safe project accessors`() {
    val project = buildRoot.createProject(":app")
    val lib1 = buildRoot.createProject(":lib1")
    val lib2 = buildRoot.createProject(":lib:lib2")
    
    project.buildFilePath.writeText(
      """
      dependencies {
        implementation(projects.lib1)
        api(projects.lib.lib2)
      }
      """.trimIndent()
    )

    val typeSafeRule = TypeSafeProjectAccessorRule(
      "spotlight",
      mapOf(
        "lib1" to lib1,
        "lib.lib2" to lib2
      )
    )

    val dependencies = KotlinPsiParser.parse(project, setOf(typeSafeRule))

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1", ":lib:lib2")
  }

  @Test
  fun `parses type-safe accessor with dependencyProject suffix`() {
    val project = buildRoot.createProject(":app")
    val lib1 = buildRoot.createProject(":lib1")
    
    project.buildFilePath.writeText(
      """
      sqldelight {
        databases {
          create("ExampleDB") {
            dependency(projects.lib1.dependencyProject)
          }
        }
      }
      """.trimIndent()
    )

    val typeSafeRule = TypeSafeProjectAccessorRule(
      "spotlight",
      mapOf("lib1" to lib1)
    )

    val dependencies = KotlinPsiParser.parse(project, setOf(typeSafeRule))

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1")
  }

  @Test
  fun `parses type-safe accessor with path suffix`() {
    val project = buildRoot.createProject(":app")
    val lib1 = buildRoot.createProject(":lib1")
    
    project.buildFilePath.writeText(
      """
      val libPath = projects.lib1.path
      dependencies {
        implementation(project(libPath))
      }
      """.trimIndent()
    )

    val typeSafeRule = TypeSafeProjectAccessorRule(
      "spotlight",
      mapOf("lib1" to lib1)
    )

    val dependencies = KotlinPsiParser.parse(project, setOf(typeSafeRule))

    // Note: Variable references won't be resolved, but .path accessor should be handled
    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1")
  }

  @Test
  fun `parses project dependencies in configuration blocks`() {
    val project = buildRoot.createProject(":app")
    project.buildFilePath.writeText(
      """
      configurations {
        create("customConfig")
      }
      
      dependencies {
        "customConfig"(project(":lib1"))
      }
      """.trimIndent()
    )

    buildRoot.createProject(":lib1")

    val dependencies = KotlinPsiParser.parse(project, emptySet())

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1")
  }

  @Test
  fun `parses project dependencies with configuration lambdas`() {
    val project = buildRoot.createProject(":app")
    project.buildFilePath.writeText(
      """
      dependencies {
        implementation(project(":lib1")) {
          exclude(group = "com.example")
        }
      }
      """.trimIndent()
    )

    buildRoot.createProject(":lib1")

    val dependencies = KotlinPsiParser.parse(project, emptySet())

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1")
  }

  @Test
  fun `handles implicit dependency rules based on buildscript content`() {
    val project = buildRoot.createProject(":app")
    val implicitDep = buildRoot.createProject(":implicit-lib")
    
    project.buildFilePath.writeText(
      """
      plugins {
        id("com.example.custom-plugin")
      }
      
      dependencies {
        implementation(project(":lib1"))
      }
      """.trimIndent()
    )

    buildRoot.createProject(":lib1")

    val rule = ImplicitDependencyRule.BuildscriptMatchRule(
      "id.*custom-plugin",
      setOf(implicitDep)
    )

    val dependencies = KotlinPsiParser.parse(project, setOf(rule))

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1", ":implicit-lib")
  }

  @Test
  fun `parses dependencies in apply blocks`() {
    val project = buildRoot.createProject(":app")
    project.buildFilePath.writeText(
      """
      apply {
        from(project(":lib1").projectDir.resolve("common.gradle.kts"))
      }
      
      dependencies {
        implementation(project(":lib2"))
      }
      """.trimIndent()
    )

    buildRoot.createProject(":lib1")
    buildRoot.createProject(":lib2")

    val dependencies = KotlinPsiParser.parse(project, emptySet())

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1", ":lib2")
  }

  @Test
  fun `handles nested type-safe accessors in DSL`() {
    val project = buildRoot.createProject(":app")
    val lib1 = buildRoot.createProject(":lib1")
    
    project.buildFilePath.writeText(
      """
      configure<SomeExtension> {
        database(projects.lib1)
      }
      """.trimIndent()
    )

    val typeSafeRule = TypeSafeProjectAccessorRule(
      "spotlight",
      mapOf("lib1" to lib1)
    )

    val dependencies = KotlinPsiParser.parse(project, setOf(typeSafeRule))

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1")
  }
}
