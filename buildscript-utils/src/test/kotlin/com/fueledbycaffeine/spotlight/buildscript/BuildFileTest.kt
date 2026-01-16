package com.fueledbycaffeine.spotlight.buildscript

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.BuildscriptMatchRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.ProjectPathMatchRule
import com.fueledbycaffeine.spotlight.buildscript.graph.TypeSafeProjectAccessorRule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText

enum class BuildFileType(val buildFile: String) {
  GROOVY(GRADLE_SCRIPT),
  KOTLIN(GRADLE_SCRIPT_KOTLIN)
}

class BuildFileTest {
  @TempDir lateinit var buildRoot: Path

  @BeforeEach fun setupBuild() {
    buildRoot.resolve("build.gradle").createFile()
    buildRoot.resolve("settings.gradle").createFile()
  }

  @Test
  fun `reads dependencies in groovy with flexible syntax`() {
    val project = buildRoot.createProject(":foo")
    
    // Groovy syntax - supports both with and without outer parentheses, and single or double quotes
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

  @Test
  fun `reads dependencies in kotlin`() {
    val project = buildRoot.createProject(":foo", BuildFileType.KOTLIN)
    
    // Kotlin syntax - requires outer parentheses, only supports double quotes
    project.buildFilePath.writeText("""
      dependencies {
        implementation ( project(":multiple-spaces"))

        implementation( project(":one-space"))

        api(project(":other-configuration")) {
          because("reason")
        }
       
          implementation(project(":bad-indentation")) // some comment

        // implementation(project(":commented"))
      }
      """.trimIndent()
    )

    val buildFile = BuildFile(project)

    assertThat(buildFile.parseDependencies()).containsExactlyInAnyOrder(
      GradlePath(buildRoot, ":multiple-spaces"),
      GradlePath(buildRoot, ":one-space"),
      GradlePath(buildRoot, ":other-configuration"),
      GradlePath(buildRoot, ":bad-indentation"),
    )
  }

  @ParameterizedTest
  @EnumSource(BuildFileType::class)
  fun `reads known type-safe project accessor dependencies`(buildFileType: BuildFileType) {
    val project = buildRoot.createProject(":foo", buildFileType)
    // A project path which does not conform to the default expected naming of lowercase kebab
    val typeSafeProject1 = buildRoot.createProject(":type-safe:a-B_c_d_E", buildFileType)
    val typeSafeProject2 = buildRoot.createProject(":type-safe:project-2", buildFileType)
    val typeSafeProjectC = buildRoot.createProject(":type-safe:project-c", buildFileType)
    
    project.buildFilePath.writeText("""
      dependencies {
        implementation(projects.typeSafe.aBCDE)
        implementation(projects.typeSafe.project2) // reason
        implementation(projects.typeSafe.projectC) {
          exclude(group = "com.example")
        }
        // implementation(projects.typeSafe.projectD)
      }
      """.trimIndent())
    
    val buildFile = BuildFile(project)
    val accessorMap = mapOf(
      "typeSafe.aBCDE" to typeSafeProject1,
      "typeSafe.project2" to typeSafeProject2,
      "typeSafe.projectC" to typeSafeProjectC,
    )
    val rule = TypeSafeProjectAccessorRule("spotlight", accessorMap)
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProject1, typeSafeProject2, typeSafeProjectC)
  }

  @Test
  fun `reads type-safe project accessor dependencies without parentheses in groovy`() {
    val project = buildRoot.createProject(":foo")
    val typeSafeProject1 = buildRoot.createProject(":type-safe:a-B_c_d_E")
    val typeSafeProject2 = buildRoot.createProject(":type-safe:project-2")
    
    project.buildFilePath.writeText("""
      dependencies {
        implementation projects.typeSafe.aBCDE
        implementation projects.typeSafe.project2 // reason
      }
      """.trimIndent())
    
    val buildFile = BuildFile(project)
    val accessorMap = mapOf(
      "typeSafe.aBCDE" to typeSafeProject1,
      "typeSafe.project2" to typeSafeProject2,
    )
    val rule = TypeSafeProjectAccessorRule("spotlight", accessorMap)
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProject1, typeSafeProject2)
  }



  @ParameterizedTest
  @EnumSource(BuildFileType::class)
  fun `reads type-safe project accessor dependencies that use explicit root project`(buildFileType: BuildFileType) {
    val project = buildRoot.createProject(":foo", buildFileType)
    val typeSafeProject = buildRoot.createProject(":type-safe:project", buildFileType)
    
    project.buildFilePath.writeText("""
      dependencies {
        implementation(projects.spotlight.typeSafe.project)
      }
      """.trimIndent())
    
    val buildFile = BuildFile(project)
    val rule = TypeSafeProjectAccessorRule("spotlight", mapOf("typeSafe.project" to typeSafeProject))
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProject)
  }



  @ParameterizedTest
  @EnumSource(BuildFileType::class)
  fun `reads type-safe project accessor dependencies that have trailing text`(buildFileType: BuildFileType) {
    val project = buildRoot.createProject(":foo", buildFileType)
    val typeSafeProject = buildRoot.createProject(":type-safe:project", buildFileType)
    
    project.buildFilePath.writeText("""
      dependencies {
        implementation(projects.typeSafe.project) // because
      }
      """.trimIndent())
    
    val buildFile = BuildFile(project)
    val rule = TypeSafeProjectAccessorRule("spotlight", mapOf("typeSafe.project" to typeSafeProject))
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProject)
  }

  @ParameterizedTest
  @EnumSource(BuildFileType::class)
  fun `throws error when type-safe accessor is unknown`(buildFileType: BuildFileType) {
    val project = buildRoot.createProject(":foo", buildFileType)
    val typeSafeProjectA = buildRoot.createProject(":type-safe:project-a", buildFileType)
    
    project.buildFilePath.writeText("""
      dependencies {
        implementation(projects.typeSafe.project)
      }
      """.trimIndent())
    
    val buildFile = BuildFile(project)
    val accessorMap = mapOf(
      "typeSafe.projectA" to typeSafeProjectA,
    )
    val rule = TypeSafeProjectAccessorRule("spotlight", accessorMap)
    assertThrows<NoSuchElementException> {
      buildFile.parseDependencies(setOf(rule))
    }
  }



  @ParameterizedTest
  @EnumSource(BuildFileType::class)
  fun `ignores type-safe accessor if inference disabled`(buildFileType: BuildFileType) {
    val project = buildRoot.createProject(":foo", buildFileType)
    
    project.buildFilePath.writeText("""
      dependencies {
        implementation(projects.typeSafe.project)
      }
      """.trimIndent())
    
    val buildFile = BuildFile(project)
    val dependencies = buildFile.parseDependencies(setOf())
    assertThat(dependencies).isEmpty()
  }

  @ParameterizedTest
  @EnumSource(BuildFileType::class)
  fun `parses implicit dependencies based on project path`(buildFileType: BuildFileType) {
    val project = buildRoot.createProject(":features:something", buildFileType)
    // Create the projects that will be dependencies
    buildRoot.createProject(":foo", buildFileType)
    buildRoot.createProject(":bar", buildFileType)
    
    // Use consistent syntax that works in both Groovy and Kotlin
    project.buildFilePath.writeText(
      """
      dependencies {
        debugImplementation(project(":foo"))
      }
      """.trimIndent()
    )

    val buildFile = BuildFile(project)

    val rules = setOf<ImplicitDependencyRule>(
      ProjectPathMatchRule(":features:.*".toRegex(), setOf(GradlePath(buildRoot, ":bar"))),
    )
    assertThat(buildFile.parseDependencies(rules))
      .containsExactlyInAnyOrder(
        GradlePath(buildRoot, ":foo"),
        GradlePath(buildRoot, ":bar"),
      )
  }

  @ParameterizedTest
  @EnumSource(BuildFileType::class)
  fun `parses implicit dependencies based on buildscript contents`(buildFileType: BuildFileType) {
    val project = buildRoot.createProject(":features:something", buildFileType)
    // Create the projects that will be dependencies
    buildRoot.createProject(":foo", buildFileType)
    buildRoot.createProject(":bar", buildFileType)
    
    project.buildFilePath.writeText("""
      plugins {
        id("com.example.feature")
      }

      dependencies {
        debugImplementation(project(":foo"))
      }
      """.trimIndent())

    val buildFile = BuildFile(project)

    val rules = setOf<DependencyRule>(
      BuildscriptMatchRule("id.*com\\.example\\.feature".toRegex(), setOf(GradlePath(buildRoot, ":bar"))),
    )
    assertThat(buildFile.parseDependencies(rules))
      .containsExactlyInAnyOrder(
        GradlePath(buildRoot, ":foo"),
        GradlePath(buildRoot, ":bar"),
      )
  }

  @Test
  fun `parses implicit dependencies based on buildscript contents with groovy syntax`() {
    val project = buildRoot.createProject(":features:something")
    buildRoot.createProject(":foo")
    buildRoot.createProject(":bar")
    
    project.buildFilePath.writeText("""
      plugins {
        id 'com.example.feature'
      }

      dependencies {
        debugImplementation project(":foo")
      }
      """.trimIndent())

    val buildFile = BuildFile(project)

    val rules = setOf<DependencyRule>(
      BuildscriptMatchRule("id.*com\\.example\\.feature".toRegex(), setOf(GradlePath(buildRoot, ":bar"))),
    )
    assertThat(buildFile.parseDependencies(rules))
      .containsExactlyInAnyOrder(
        GradlePath(buildRoot, ":foo"),
        GradlePath(buildRoot, ":bar"),
      )
  }

  @ParameterizedTest
  @EnumSource(BuildFileType::class)
  fun `ignores duplicates`(buildFileType: BuildFileType) {
    val project = buildRoot.createProject(":foo", buildFileType)
    project.buildFilePath.writeText(
      """
      dependencies {
        debugImplementation(project(":foo"))
        debugImplementation(project(":foo"))
      }
      """.trimIndent()
    )

    val buildFile = BuildFile(project)

    assertThat(buildFile.parseDependencies()).containsExactlyInAnyOrder(
      GradlePath(buildRoot, ":foo")
    )
  }

  @ParameterizedTest
  @EnumSource(BuildFileType::class)
  fun `ignores type-safe project accessor's trailing dependencyProject API`(buildFileType: BuildFileType) {
    val project = buildRoot.createProject(":foo", buildFileType)
    val typeSafeProject = buildRoot.createProject(":type-safe:project", buildFileType)
    
    project.buildFilePath.writeText("""
      sqldelight {
        databases {
          create("ExampleDB") {
            dependency(projects.typeSafe.project.dependencyProject)
          }
        }
      }
      """.trimIndent())
    
    val buildFile = BuildFile(project)
    val rule = TypeSafeProjectAccessorRule("spotlight", mapOf("typeSafe.project" to typeSafeProject))
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProject)
  }

  @ParameterizedTest
  @EnumSource(BuildFileType::class)
  fun `ignores other DSL that can look like a type-safe project accessor`(buildFileType: BuildFileType) {
    val project = buildRoot.createProject(":foo", buildFileType)
    val typeSafeProject = buildRoot.createProject(":type-safe:project", buildFileType)
    
    project.buildFilePath.writeText("""
      android {
        namespace = "com.example.projects.foo.bar"
      }
      """.trimIndent())
    
    val buildFile = BuildFile(project)
    val rule = TypeSafeProjectAccessorRule("spotlight", mapOf("typeSafe.project" to typeSafeProject))
    assertThat(buildFile.parseDependencies(setOf(rule))).isEmpty()
  }

  @Test
  fun `ignores other DSL with single quotes in groovy`() {
    val project = buildRoot.createProject(":foo")
    val typeSafeProject = buildRoot.createProject(":type-safe:project")
    
    project.buildFilePath.writeText("""
      android {
        namespace = 'com.example.projects.foo.bar'
        namespace2 = "com.example.projects.foo.bar"
      }
      """.trimIndent())
    
    val buildFile = BuildFile(project)
    val rule = TypeSafeProjectAccessorRule("spotlight", mapOf("typeSafe.project" to typeSafeProject))
    assertThat(buildFile.parseDependencies(setOf(rule))).isEmpty()
  }

  @ParameterizedTest
  @EnumSource(BuildFileType::class)
  fun `type-safe accessor support ignores projects as part of compound words like subprojects`(buildFileType: BuildFileType) {
    val project = buildRoot.createProject(":foo", buildFileType)
    val typeSafeProjectA = buildRoot.createProject(":type-safe:project-a", buildFileType)
    
    project.buildFilePath.writeText("""
      dependencies {
        implementation(projects.typeSafe.projectA)
      }

      tasks.named("foo") {
        val allTestTasks = rootProject.subprojects.mapNotNull { it.tasks.findByPath("test") }
        mustRunAfter(allTestTasks)
      }
      """.trimIndent())
    
    val buildFile = BuildFile(project)
    val rule = TypeSafeProjectAccessorRule("spotlight", mapOf("typeSafe.projectA" to typeSafeProjectA))

    // Should only find the legitimate typeSafe.projectA reference, not the compound words
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProjectA)
  }

  @ParameterizedTest
  @EnumSource(BuildFileType::class)
  fun `ignores type-safe project accessor's trailing path API`(buildFileType: BuildFileType) {
    val project = buildRoot.createProject(":foo", buildFileType)
    val typeSafeProject = buildRoot.createProject(":type-safe:project", buildFileType)
    
    project.buildFilePath.writeText("""
      baselineProfile {
        from(project(projects.typeSafe.project.path))
      }
      """.trimIndent()
    )
    
    val buildFile = BuildFile(project)
    val rule = TypeSafeProjectAccessorRule("spotlight", mapOf("typeSafe.project" to typeSafeProject))
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProject)
  }

  @ParameterizedTest
  @EnumSource(BuildFileType::class)
  fun `projects include all intermediate directories that also have build files in them`(buildFileType: BuildFileType) {
    // Create a nested :foo:bar:baz that explicitly depends on nothing but implicitly requires its
    // parent dirs.
    val project = buildRoot.createProject(":foo:bar:baz", buildFileType)
    buildRoot.createProject(":foo", buildFileType)
    buildRoot.createProject(":foo:bar", buildFileType)
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

  @ParameterizedTest
  @EnumSource(BuildFileType::class)
  fun `reads dependencies inside any wrapper functions`(buildFileType: BuildFileType) {
    val project = buildRoot.createProject(":foo", buildFileType)
    
    project.buildFilePath.writeText("""
      dependencies {
        // Common DependencyHandler "wrappers"
        implementation(platform(project(":platform:bom")))
        implementation(enforcedPlatform(project(":enforced:platform")))
        testImplementation(testFixtures(project(":test:fixtures")))

        implementation(platform(project(":platform:bom2")))
        testImplementation(testFixtures(project(":test:fixtures2")))

        // Deep nesting, not sure how this would happen, but just in case!
        implementation(first(second(third(project(":deeply:nested")))))
      }
      """.trimIndent())

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

  @Test
  fun `reads dependencies inside wrapper functions without outer parentheses in groovy`() {
    val project = buildRoot.createProject(":foo")
    
    project.buildFilePath.writeText("""
      dependencies {
        // Groovy allows omitting outer parentheses
        implementation platform(project(":platform:bom"))
        testImplementation testFixtures(project(":test:fixtures"))
      }
      """.trimIndent())

    val buildFile = BuildFile(project)

    assertThat(buildFile.parseDependencies()).containsExactlyInAnyOrder(
      GradlePath(buildRoot, ":platform:bom"),
      GradlePath(buildRoot, ":test:fixtures")
    )
  }

  @ParameterizedTest
  @EnumSource(BuildFileType::class)
  fun `strips all comments before parsing`(buildFileType: BuildFileType) {
    // given
    val project = buildRoot.createProject(":foo", buildFileType)
    project.buildFilePath.writeText(
      """
      /**
       * This is a block comment
       */
      plugins {
        id("java") // plugin
      }

      dependencies {
        /* tricky */ implementation(project(":foo"))
        // implementation(project(":not:this:one"))
        /* implementation(project(":also:not:this:one")) */
      }

      /* 
       * Another block comment 
       * implementation(project(":baz"))
       */

      dependencies {
        implementation(project(":bar")) // implementation(project(":baz"))
      }
      """
    )

    // when
    val dependencies = BuildFile(project).parseDependencies()

    // then
    assertThat(dependencies).containsExactlyInAnyOrder(
        GradlePath(project.root, ":foo"),
        GradlePath(project.root, ":bar")
    )
  }

  private fun Path.createProject(path: String, buildFileType: BuildFileType = BuildFileType.GROOVY): GradlePath {
    return GradlePath(this, path).apply {
      projectDir.createDirectories()
      projectDir.resolve(buildFileType.buildFile).createFile()
    }
  }
}
