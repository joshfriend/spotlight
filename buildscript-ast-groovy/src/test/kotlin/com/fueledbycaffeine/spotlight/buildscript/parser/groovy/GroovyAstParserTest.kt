package com.fueledbycaffeine.spotlight.buildscript.parser.groovy

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT
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

class GroovyAstParserTest {
  @TempDir lateinit var buildRoot: Path

  @BeforeEach
  fun setupBuild() {
    buildRoot.resolve("build.gradle").createFile()
    buildRoot.resolve("settings.gradle").createFile()
  }

  private fun Path.createProject(projectPath: String): GradlePath {
    val segments = projectPath.split(":").filter { it.isNotEmpty() }
    val projectDir = segments.fold(this) { acc, segment -> acc.resolve(segment) }
    projectDir.createDirectories()
    projectDir.resolve("build.gradle").createFile()
    return GradlePath(this, projectPath)
  }

  @Test
  fun `parses project dependencies with parentheses`() {
    val project = buildRoot.createProject(":app")
    project.buildFilePath.writeText(
      """
      dependencies {
        implementation(project(':lib1'))
        api(project(":lib2"))
      }
      """.trimIndent()
    )

    buildRoot.createProject(":lib1")
    buildRoot.createProject(":lib2")

    val dependencies = GroovyAstParser.parse(project, emptySet())

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1", ":lib2")
  }

  @Test
  fun `parses project dependencies without parentheses`() {
    val project = buildRoot.createProject(":app")
    project.buildFilePath.writeText(
      """
      dependencies {
        implementation project(':lib1')
        api project(":lib2")
      }
      """.trimIndent()
    )

    buildRoot.createProject(":lib1")
    buildRoot.createProject(":lib2")

    val dependencies = GroovyAstParser.parse(project, emptySet())

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1", ":lib2")
  }

  @Test
  fun `parses nested dependencies`() {
    val project = buildRoot.createProject(":app")
    project.buildFilePath.writeText(
      """
      dependencies {
        implementation project(':lib1')
      }
      
      configurations.all {
        dependencies {
          implementation project(':lib2')
        }
      }
      """.trimIndent()
    )

    buildRoot.createProject(":lib1")
    buildRoot.createProject(":lib2")

    val dependencies = GroovyAstParser.parse(project, emptySet())

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1", ":lib2")
  }

  @Test
  fun `handles comments correctly`() {
    val project = buildRoot.createProject(":app")
    project.buildFilePath.writeText(
      """
      dependencies {
        // implementation project(':commented-out')
        implementation project(':lib1')
        /* implementation project(':also-commented') */
      }
      """.trimIndent()
    )

    buildRoot.createProject(":lib1")

    val dependencies = GroovyAstParser.parse(project, emptySet())

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1")
  }

  @Test
  fun `returns empty set when no dependencies`() {
    val project = buildRoot.createProject(":app")
    project.buildFilePath.writeText(
      """
      plugins {
        id 'java'
      }
      """.trimIndent()
    )

    val dependencies = GroovyAstParser.parse(project, emptySet())

    assertThat(dependencies).isEmpty()
  }

  @Test
  fun `parses dependencies with single and double quotes`() {
    val project = buildRoot.createProject(":app")
    project.buildFilePath.writeText(
      """
      dependencies {
        implementation project(":lib1")
        implementation project(':lib2')
      }
      """.trimIndent()
    )

    buildRoot.createProject(":lib1")
    buildRoot.createProject(":lib2")

    val dependencies = GroovyAstParser.parse(project, emptySet())

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1", ":lib2")
  }

  @Test
  fun `clears cache correctly`() {
    val project = buildRoot.createProject(":app")
    project.buildFilePath.writeText(
      """
      dependencies {
        implementation project(':lib1')
      }
      """.trimIndent()
    )

    buildRoot.createProject(":lib1")

    // Parse once to populate cache
    GroovyAstParser.parse(project, emptySet())

    // Parse again - should still work
    val dependencies = GroovyAstParser.parse(project, emptySet())

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1")
  }

  @Test
  fun `parses type-safe project accessors`() {
    val project = buildRoot.createProject(":app")
    val lib1 = buildRoot.createProject(":lib1")
    
    project.buildFilePath.writeText(
      """
      dependencies {
        implementation projects.lib1
      }
      """.trimIndent()
    )

    val typeSafeRule = TypeSafeProjectAccessorRule(
      "spotlight",
      mapOf("lib1" to lib1)
    )

    val dependencies = GroovyAstParser.parse(project, setOf(typeSafeRule))

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1")
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
            dependency projects.lib1.dependencyProject
          }
        }
      }
      """.trimIndent()
    )

    val typeSafeRule = TypeSafeProjectAccessorRule(
      "spotlight",
      mapOf("lib1" to lib1)
    )

    val dependencies = GroovyAstParser.parse(project, setOf(typeSafeRule))

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1")
  }

  @Test
  fun `parses project dependencies in configuration blocks`() {
    val project = buildRoot.createProject(":app")
    project.buildFilePath.writeText(
      """
      configurations {
        customConfig
      }
      
      dependencies {
        customConfig project(':lib1')
      }
      """.trimIndent()
    )

    buildRoot.createProject(":lib1")

    val dependencies = GroovyAstParser.parse(project, emptySet())

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1")
  }

  @Test
  fun `parses project dependencies with configuration closures`() {
    val project = buildRoot.createProject(":app")
    project.buildFilePath.writeText(
      """
      dependencies {
        implementation(project(':lib1')) {
          exclude group: 'com.example'
        }
      }
      """.trimIndent()
    )

    buildRoot.createProject(":lib1")

    val dependencies = GroovyAstParser.parse(project, emptySet())

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1")
  }

  @Test
  fun `handles implicit dependency rules based on buildscript content`() {
    val project = buildRoot.createProject(":app")
    val implicitDep = buildRoot.createProject(":implicit-lib")
    
    project.buildFilePath.writeText(
      """
      plugins {
        id 'com.example.custom-plugin'
      }
      
      dependencies {
        implementation project(':lib1')
      }
      """.trimIndent()
    )

    buildRoot.createProject(":lib1")

    val rule = ImplicitDependencyRule.BuildscriptMatchRule(
      "id.*custom-plugin",
      setOf(implicitDep)
    )

    val dependencies = GroovyAstParser.parse(project, setOf(rule))

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1", ":implicit-lib")
  }

  @Test
  fun `handles deep nesting of dependencies`() {
    val project = buildRoot.createProject(":app")
    project.buildFilePath.writeText(
      """
      subprojects {
        dependencies {
          implementation project(':lib1')
        }
      }
      
      allprojects {
        dependencies {
          testImplementation project(':lib2')
        }
      }
      """.trimIndent()
    )

    buildRoot.createProject(":lib1")
    buildRoot.createProject(":lib2")

    val dependencies = GroovyAstParser.parse(project, emptySet())

    assertThat(dependencies.map { it.path }).containsExactlyInAnyOrder(":lib1", ":lib2")
  }
}
