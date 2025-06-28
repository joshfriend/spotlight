package com.fueledbycaffeine.spotlight.buildscript

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.FullModeTypeSafeProjectAccessorRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.BuildscriptMatchRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.ProjectPathMatchRule
import com.fueledbycaffeine.spotlight.buildscript.graph.StrictModeTypeSafeProjectAccessorRule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
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
       
          implementation project(':bad-indentation') // some comment

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

  @Test fun `reads known type-safe project accessor dependencies in full mode`() {
    val project = buildRoot.createProject(":foo")
    // A project path which does not conform to the default expected naming of lowercase kebab
    val typeSafeProject1 = buildRoot.createProject(":type-safe:a-B_c_d_E")
    val typeSafeProject2 = buildRoot.createProject(":type-safe:project-2")
    val typeSafeProjectC = buildRoot.createProject(":type-safe:project-c")
    project.buildFilePath.writeText("""
      dependencies {
        implementation projects.typeSafe.aBCDE
        implementation projects.typeSafe.project2 // reason
        implementation(projects.typeSafe.projectC) {
          exclude group: 'com.example'
        }
        // implementation projects.typeSafe.projectD
      }
      """.trimIndent()
    )
    val buildFile = BuildFile(project)
    val accessorMap = mapOf(
      "typeSafe.aBCDE" to typeSafeProject1,
      "typeSafe.project2" to typeSafeProject2,
      "typeSafe.projectC" to typeSafeProjectC,
    )
    val rule = FullModeTypeSafeProjectAccessorRule("spotlight", accessorMap)
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProject1, typeSafeProject2, typeSafeProjectC)
  }

  @Test fun `reads type-safe project accessor dependencies in strict mode`() {
    val project = buildRoot.createProject(":foo")
    val typeSafeProjectA = buildRoot.createProject(":type-safe:project-a")
    val typeSafeProjectB = buildRoot.createProject(":type-safe:project-b")
    val typeSafeProjectC = buildRoot.createProject(":type-safe:project-c")
    project.buildFilePath.writeText("""
      dependencies {
        implementation projects.typeSafe.projectA
        implementation projects.typeSafe.projectB // reason
        implementation(projects.typeSafe.projectC) {
          exclude group: 'com.example'
        }
        // implementation projects.typeSafe.projectD
      }
      """.trimIndent()
    )
    val buildFile = BuildFile(project)
    val rule = StrictModeTypeSafeProjectAccessorRule("spotlight")
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProjectA, typeSafeProjectB, typeSafeProjectC)
  }

  @Test fun `reads type-safe project accessor dependencies that use explicit root project in full mode`() {
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
    val rule = FullModeTypeSafeProjectAccessorRule("spotlight", mapOf("typeSafe.project" to typeSafeProject))
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProject)
  }

  @Test fun `reads type-safe project accessor dependencies that use explicit root project in strict mode`() {
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
    val rule = StrictModeTypeSafeProjectAccessorRule("spotlight")
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProject)
  }

  @Test fun `reads type-safe project accessor dependencies that have trailing text`() {
    val project = buildRoot.createProject(":foo")
    val typeSafeProject = GradlePath(buildRoot, ":type-safe:project")
    typeSafeProject.projectDir.createDirectories()
    typeSafeProject.projectDir.resolve("build.gradle").createFile()
    project.buildFilePath.writeText("""
      dependencies {
        implementation projects.typeSafe.project // because
      }
      """.trimIndent()
    )
    val buildFile = BuildFile(project)
    val rule = FullModeTypeSafeProjectAccessorRule("spotlight", mapOf("typeSafe.project" to typeSafeProject))
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProject)
  }

  @Test fun `throws error when type-safe accessor is unknown if full inference enabled`() {
    val project = buildRoot.createProject(":foo")
    val typeSafeProjectA = buildRoot.createProject(":type-safe:project-a")
    project.buildFilePath.writeText("""
      dependencies {
        implementation projects.typeSafe.project
      }
      """.trimIndent()
    )
    val buildFile = BuildFile(project)
    val accessorMap = mapOf(
      "typeSafe.projectA" to typeSafeProjectA,
    )
    val rule = FullModeTypeSafeProjectAccessorRule("spotlight", accessorMap)
    assertThrows<NoSuchElementException> {
      buildFile.parseDependencies(setOf(rule))
    }
  }

  @Test fun `assumes default path for type-safe accessor if strict inference enabled`() {
    val project = buildRoot.createProject(":foo")
    project.buildFilePath.writeText("""
      dependencies {
        implementation projects.typeSafe.project
      }
      """.trimIndent()
    )
    val buildFile = BuildFile(project)
    val rule = StrictModeTypeSafeProjectAccessorRule("spotlight")
    val dependencies = buildFile.parseDependencies(setOf(rule))
    assertThat(dependencies).containsExactlyInAnyOrder(GradlePath(buildRoot, ":type-safe:project"))
  }

  @Test fun `ignores type-safe accessor if inference disabled`() {
    val project = buildRoot.createProject(":foo")
    project.buildFilePath.writeText("""
      dependencies {
        implementation projects.typeSafe.project
      }
      """.trimIndent()
    )
    val buildFile = BuildFile(project)
    val dependencies = buildFile.parseDependencies(setOf())
    assertThat(dependencies).isEmpty()
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
      ProjectPathMatchRule(":features:.*", setOf(GradlePath(buildRoot, ":bar"))),
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

    val rules = setOf<DependencyRule>(
      BuildscriptMatchRule("id 'com.example.feature'", setOf(GradlePath(buildRoot, ":bar"))),
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

  @Test fun `ignores type-safe project accessor's trailing dependencyProject API`() {
    val project = buildRoot.createProject(":foo")
    val typeSafeProject = GradlePath(buildRoot, ":type-safe:project")
    typeSafeProject.projectDir.createDirectories()
    typeSafeProject.projectDir.resolve("build.gradle").createFile()
    project.buildFilePath.writeText("""
      sqldelight {
        databases {
          create("ExampleDB") {
            dependency projects.typeSafe.project.dependencyProject
          }
        }
      }
      """.trimIndent()
    )
    val buildFile = BuildFile(project)
    val rule = FullModeTypeSafeProjectAccessorRule("spotlight", mapOf("typeSafe.project" to typeSafeProject))
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProject)
  }

  @Test fun `ignores other DSL that can look like a type-safe project accessor`() {
    val project = buildRoot.createProject(":foo")
    val typeSafeProject = GradlePath(buildRoot, ":type-safe:project")
    typeSafeProject.projectDir.createDirectories()
    typeSafeProject.projectDir.resolve("build.gradle").createFile()
    project.buildFilePath.writeText("""
      android {
        namespace = "com.example.projects.foo.bar"
      }
      """.trimIndent()
    )
    val buildFile = BuildFile(project)
    val rule = StrictModeTypeSafeProjectAccessorRule("spotlight")
    assertThat(buildFile.parseDependencies(setOf(rule))).isEmpty()
  }

  @Test fun `type-safe accessor support ignores projects as part of compound words like subprojects`() {
    val project = buildRoot.createProject(":foo")
    val typeSafeProjectA = buildRoot.createProject(":type-safe:project-a")
    project.buildFilePath.writeText("""
      dependencies {
        implementation projects.typeSafe.projectA
      }

      tasks.named("foo") {
        val allTestTasks = rootProject.subprojects.mapNotNull { it.tasks.findByPath("test") }
        mustRunAfter(allTestTasks)
      }
      """.trimIndent()
    )
    val buildFile = BuildFile(project)
    val rule = StrictModeTypeSafeProjectAccessorRule("spotlight")

    // Should only find the legitimate typeSafe.projectA reference, not the compound words
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProjectA)
  }

  @Test fun `ignores type-safe project accessor's trailing path API`() {
    val project = buildRoot.createProject(":foo")
    val typeSafeProject = GradlePath(buildRoot, ":type-safe:project")
    typeSafeProject.projectDir.createDirectories()
    typeSafeProject.projectDir.resolve("build.gradle").createFile()
    project.buildFilePath.writeText("""
      baselineProfile {
        from(project(projects.typeSafe.project.path))
      }
      """.trimIndent()
    )
    val buildFile = BuildFile(project)
    val rule = FullModeTypeSafeProjectAccessorRule("spotlight", mapOf("typeSafe.project" to typeSafeProject))
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProject)
  }

  @Test fun `projects include all intermediate directories that also have build files in them`() {
    // Create a nested :foo:bar:baz that explicitly depends on nothing but implicitly requires its
    // parent dirs.
    val project = buildRoot.createProject(":foo:bar:baz")
    buildRoot.createProject(":foo")
    buildRoot.createProject(":foo:bar")
    project.buildFilePath.writeText(
      """
      dependencies {
      }
      """.trimIndent()
    )

    val buildFile = BuildFile(project)

    assertThat(buildFile.parseDependencies())
      .containsExactlyInAnyOrder(
        GradlePath(buildRoot, ":foo"),
        GradlePath(buildRoot, ":foo:bar"),
      )
  }

  @Test fun `reads dependencies inside any wrapper functions`() {
    val project = buildRoot.createProject(":foo")
    project.buildFilePath.writeText("""
      dependencies {
        // Common DependencyHandler "wrappers"
        implementation(platform(project(":platform:bom")))
        implementation(enforcedPlatform(project(":enforced:platform")))
        testImplementation(testFixtures(project(":test:fixtures")))

        // With spaces (groovy)
        implementation platform(project(":platform:bom2"))
        testImplementation testFixtures(project(":test:fixtures2"))

        // Deep nesting, not sure how this would happen, but just in case!
        implementation(first(second(third(project(":deeply:nested")))))
      }
      """.trimIndent()
    )

    val buildFile = BuildFile(project)

    assertThat(buildFile.parseDependencies()).containsExactlyInAnyOrder(
      GradlePath(buildRoot, ":platform:bom"),
      GradlePath(buildRoot, ":enforced:platform"),
      GradlePath(buildRoot, ":test:fixtures"),
      GradlePath(buildRoot, ":platform:bom2"),
      GradlePath(buildRoot, ":test:fixtures2"),
      GradlePath(buildRoot, ":deeply:nested")
    )
  }

  private fun Path.createProject(path: String, extension: String = ".gradle"): GradlePath {
    return GradlePath(this, path).apply {
      projectDir.createDirectories()
      projectDir.resolve("build$extension").createFile()
    }
  }
}