package com.fueledbycaffeine.spotlight.buildscript

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.BuildscriptMatchRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.ProjectPathMatchRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ParsingConfiguration
import com.fueledbycaffeine.spotlight.buildscript.graph.TypeSafeProjectAccessorRule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText

class BuildFileTest {
  @TempDir lateinit var buildRoot: Path

  @BeforeEach fun setupBuild() {
    buildRoot.resolve("build.gradle").createFile()
    buildRoot.resolve("settings.gradle").createFile()
  }

  companion object {
    @JvmStatic
    fun parsingModes(): Stream<Arguments> = Stream.of(
      Arguments.of(ParsingConfiguration.REGEX, ".gradle"),
      Arguments.of(ParsingConfiguration.REGEX, ".gradle.kts"),
      Arguments.of(ParsingConfiguration.AST, ".gradle"),
      Arguments.of(ParsingConfiguration.AST, ".gradle.kts"),
    )
  }

  @ParameterizedTest
  @MethodSource("parsingModes")
  fun `reads dependencies`(mode: ParsingConfiguration, extension: String) {
    val project = buildRoot.createProject(":foo", extension)
    
    val content = if (extension == ".gradle") {
      // Groovy syntax - supports both with and without outer parentheses, and single or double quotes
      """
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
    } else {
      // Kotlin syntax - requires outer parentheses, only supports double quotes
      """
      dependencies {
        implementation(  project(":multiple-spaces"))

        implementation(project(":one-space"))

        implementation(project(":parentheses"))

        api(project(":other-configuration")) {
          because("reason")
        }
       
          implementation(project(":bad-indentation")) // some comment

        // implementation(project(":commented"))
      }
      """.trimIndent()
    }
    project.buildFilePath.writeText(content)

    val buildFile = BuildFile(project, mode)

    val expected = if (extension == ".gradle") {
      setOf(
        GradlePath(buildRoot, ":multiple-spaces-double-quotes"),
        GradlePath(buildRoot, ":multiple-spaces-single-quotes"),
        GradlePath(buildRoot, ":one-space-double-quotes"),
        GradlePath(buildRoot, ":one-space-single-quotes"),
        GradlePath(buildRoot, ":parentheses-double-quotes"),
        GradlePath(buildRoot, ":parentheses-single-quotes"),
        GradlePath(buildRoot, ":other-configuration"),
        GradlePath(buildRoot, ":bad-indentation"),
      )
    } else {
      setOf(
        GradlePath(buildRoot, ":multiple-spaces"),
        GradlePath(buildRoot, ":one-space"),
        GradlePath(buildRoot, ":parentheses"),
        GradlePath(buildRoot, ":other-configuration"),
        GradlePath(buildRoot, ":bad-indentation"),
      )
    }
    
    assertThat(buildFile.parseDependencies()).containsExactlyInAnyOrder(*expected.toTypedArray())
  }

  @ParameterizedTest
  @MethodSource("parsingModes")
  fun `reads known type-safe project accessor dependencies`(mode: ParsingConfiguration, extension: String) {
    val project = buildRoot.createProject(":foo", extension)
    // A project path which does not conform to the default expected naming of lowercase kebab
    val typeSafeProject1 = buildRoot.createProject(":type-safe:a-B_c_d_E", extension)
    val typeSafeProject2 = buildRoot.createProject(":type-safe:project-2", extension)
    val typeSafeProjectC = buildRoot.createProject(":type-safe:project-c", extension)
    
    val content = if (extension == ".gradle") {
      """
      dependencies {
        implementation projects.typeSafe.aBCDE
        implementation projects.typeSafe.project2 // reason
        implementation(projects.typeSafe.projectC) {
          exclude group: 'com.example'
        }
        // implementation projects.typeSafe.projectD
      }
      """.trimIndent()
    } else {
      """
      dependencies {
        implementation(projects.typeSafe.aBCDE)
        implementation(projects.typeSafe.project2) // reason
        implementation(projects.typeSafe.projectC) {
          exclude(group = "com.example")
        }
        // implementation(projects.typeSafe.projectD)
      }
      """.trimIndent()
    }
    project.buildFilePath.writeText(content)
    
    val buildFile = BuildFile(project, mode)
    val accessorMap = mapOf(
      "typeSafe.aBCDE" to typeSafeProject1,
      "typeSafe.project2" to typeSafeProject2,
      "typeSafe.projectC" to typeSafeProjectC,
    )
    val rule = TypeSafeProjectAccessorRule("spotlight", accessorMap)
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProject1, typeSafeProject2, typeSafeProjectC)
  }



  @ParameterizedTest
  @MethodSource("parsingModes")
  fun `reads type-safe project accessor dependencies that use explicit root project`(mode: ParsingConfiguration, extension: String) {
    val project = buildRoot.createProject(":foo", extension)
    val typeSafeProject = buildRoot.createProject(":type-safe:project", extension)
    
    val content = if (extension == ".gradle") {
      """
      dependencies {
        implementation projects.spotlight.typeSafe.project
      }
      """.trimIndent()
    } else {
      """
      dependencies {
        implementation(projects.spotlight.typeSafe.project)
      }
      """.trimIndent()
    }
    project.buildFilePath.writeText(content)
    
    val buildFile = BuildFile(project, mode)
    val rule = TypeSafeProjectAccessorRule("spotlight", mapOf("typeSafe.project" to typeSafeProject))
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProject)
  }



  @ParameterizedTest
  @MethodSource("parsingModes")
  fun `reads type-safe project accessor dependencies that have trailing text`(mode: ParsingConfiguration, extension: String) {
    val project = buildRoot.createProject(":foo", extension)
    val typeSafeProject = buildRoot.createProject(":type-safe:project", extension)
    
    val content = if (extension == ".gradle") {
      """
      dependencies {
        implementation projects.typeSafe.project // because
      }
      """.trimIndent()
    } else {
      """
      dependencies {
        implementation(projects.typeSafe.project) // because
      }
      """.trimIndent()
    }
    project.buildFilePath.writeText(content)
    
    val buildFile = BuildFile(project, mode)
    val rule = TypeSafeProjectAccessorRule("spotlight", mapOf("typeSafe.project" to typeSafeProject))
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProject)
  }

  @ParameterizedTest
  @MethodSource("parsingModes")
  fun `throws error when type-safe accessor is unknown`(mode: ParsingConfiguration, extension: String) {
    val project = buildRoot.createProject(":foo", extension)
    val typeSafeProjectA = buildRoot.createProject(":type-safe:project-a", extension)
    
    val content = if (extension == ".gradle") {
      """
      dependencies {
        implementation projects.typeSafe.project
      }
      """.trimIndent()
    } else {
      """
      dependencies {
        implementation(projects.typeSafe.project)
      }
      """.trimIndent()
    }
    project.buildFilePath.writeText(content)
    
    val buildFile = BuildFile(project, mode)
    val accessorMap = mapOf(
      "typeSafe.projectA" to typeSafeProjectA,
    )
    val rule = TypeSafeProjectAccessorRule("spotlight", accessorMap)
    assertThrows<NoSuchElementException> {
      buildFile.parseDependencies(setOf(rule))
    }
  }



  @ParameterizedTest
  @MethodSource("parsingModes")
  fun `ignores type-safe accessor if inference disabled`(mode: ParsingConfiguration, extension: String) {
    val project = buildRoot.createProject(":foo", extension)
    
    val content = if (extension == ".gradle") {
      """
      dependencies {
        implementation projects.typeSafe.project
      }
      """.trimIndent()
    } else {
      """
      dependencies {
        implementation(projects.typeSafe.project)
      }
      """.trimIndent()
    }
    project.buildFilePath.writeText(content)
    
    val buildFile = BuildFile(project, mode)
    val dependencies = buildFile.parseDependencies(setOf())
    assertThat(dependencies).isEmpty()
  }

  @ParameterizedTest
  @MethodSource("parsingModes")
  fun `parses implicit dependencies based on project path`(mode: ParsingConfiguration, extension: String) {
    val project = buildRoot.createProject(":features:something", extension)
    // Create the projects that will be dependencies
    buildRoot.createProject(":foo", extension)
    buildRoot.createProject(":bar", extension)
    
    // Use consistent syntax that works in both Groovy and Kotlin
    project.buildFilePath.writeText(
      """
      dependencies {
        debugImplementation(project(":foo"))
      }
      """.trimIndent()
    )

    val buildFile = BuildFile(project, mode)

    val rules = setOf<ImplicitDependencyRule>(
      ProjectPathMatchRule(":features:.*", setOf(GradlePath(buildRoot, ":bar"))),
    )
    assertThat(buildFile.parseDependencies(rules))
      .containsExactlyInAnyOrder(
        GradlePath(buildRoot, ":foo"),
        GradlePath(buildRoot, ":bar"),
      )
  }

  @ParameterizedTest
  @MethodSource("parsingModes")
  fun `parses implicit dependencies based on buildscript contents`(mode: ParsingConfiguration, extension: String) {
    val project = buildRoot.createProject(":features:something", extension)
    // Create the projects that will be dependencies
    buildRoot.createProject(":foo", extension)
    buildRoot.createProject(":bar", extension)
    
    val content = if (extension == ".gradle") {
      """
      plugins {
        id 'com.example.feature'
      }

      dependencies {
        debugImplementation project(":foo")
      }
      """.trimIndent()
    } else {
      """
      plugins {
        id("com.example.feature")
      }

      dependencies {
        debugImplementation(project(":foo"))
      }
      """.trimIndent()
    }
    project.buildFilePath.writeText(content)

    val buildFile = BuildFile(project, mode)

    val rules = setOf<DependencyRule>(
      BuildscriptMatchRule("id.*com\\.example\\.feature", setOf(GradlePath(buildRoot, ":bar"))),
    )
    assertThat(buildFile.parseDependencies(rules))
      .containsExactlyInAnyOrder(
        GradlePath(buildRoot, ":foo"),
        GradlePath(buildRoot, ":bar"),
      )
  }

  @ParameterizedTest
  @MethodSource("parsingModes")
  fun `ignores duplicates`(mode: ParsingConfiguration, extension: String) {
    val project = buildRoot.createProject(":foo", extension)
    project.buildFilePath.writeText(
      """
      dependencies {
        debugImplementation(project(":foo"))
        debugImplementation(project(":foo"))
      }
      """.trimIndent()
    )

    val buildFile = BuildFile(project, mode)

    assertThat(buildFile.parseDependencies()).containsExactlyInAnyOrder(
      GradlePath(buildRoot, ":foo")
    )
  }

  @ParameterizedTest
  @MethodSource("parsingModes")
  fun `ignores type-safe project accessor's trailing dependencyProject API`(mode: ParsingConfiguration, extension: String) {
    val project = buildRoot.createProject(":foo", extension)
    val typeSafeProject = buildRoot.createProject(":type-safe:project", extension)
    
    val content = if (extension == ".gradle") {
      """
      sqldelight {
        databases {
          create("ExampleDB") {
            dependency projects.typeSafe.project.dependencyProject
          }
        }
      }
      """.trimIndent()
    } else {
      """
      sqldelight {
        databases {
          create("ExampleDB") {
            dependency(projects.typeSafe.project.dependencyProject)
          }
        }
      }
      """.trimIndent()
    }
    project.buildFilePath.writeText(content)
    
    val buildFile = BuildFile(project, mode)
    val rule = TypeSafeProjectAccessorRule("spotlight", mapOf("typeSafe.project" to typeSafeProject))
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProject)
  }

  @ParameterizedTest
  @MethodSource("parsingModes")
  fun `ignores other DSL that can look like a type-safe project accessor`(mode: ParsingConfiguration, extension: String) {
    val project = buildRoot.createProject(":foo", extension)
    val typeSafeProject = buildRoot.createProject(":type-safe:project", extension)
    
    val content = if (extension == ".gradle") {
      """
      android {
        namespace = "com.example.projects.foo.bar"
        namespace2 = 'com.example.projects.foo.bar'
      }
      """.trimIndent()
    } else {
      """
      android {
        namespace = "com.example.projects.foo.bar"
      }
      """.trimIndent()
    }
    project.buildFilePath.writeText(content)
    
    val buildFile = BuildFile(project, mode)
    val rule = TypeSafeProjectAccessorRule("spotlight", mapOf("typeSafe.project" to typeSafeProject))
    assertThat(buildFile.parseDependencies(setOf(rule))).isEmpty()
  }

  @ParameterizedTest
  @MethodSource("parsingModes")
  fun `type-safe accessor support ignores projects as part of compound words like subprojects`(mode: ParsingConfiguration, extension: String) {
    val project = buildRoot.createProject(":foo", extension)
    val typeSafeProjectA = buildRoot.createProject(":type-safe:project-a", extension)
    
    val content = if (extension == ".gradle") {
      """
      dependencies {
        implementation projects.typeSafe.projectA
      }

      tasks.named("foo") {
        val allTestTasks = rootProject.subprojects.mapNotNull { it.tasks.findByPath("test") }
        mustRunAfter(allTestTasks)
      }
      """.trimIndent()
    } else {
      """
      dependencies {
        implementation(projects.typeSafe.projectA)
      }

      tasks.named("foo") {
        val allTestTasks = rootProject.subprojects.mapNotNull { it.tasks.findByPath("test") }
        mustRunAfter(allTestTasks)
      }
      """.trimIndent()
    }
    project.buildFilePath.writeText(content)
    
    val buildFile = BuildFile(project, mode)
    val rule = TypeSafeProjectAccessorRule("spotlight", mapOf("typeSafe.projectA" to typeSafeProjectA))

    // Should only find the legitimate typeSafe.projectA reference, not the compound words
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProjectA)
  }

  @ParameterizedTest
  @MethodSource("parsingModes")
  fun `ignores type-safe project accessor's trailing path API`(mode: ParsingConfiguration, extension: String) {
    val project = buildRoot.createProject(":foo", extension)
    val typeSafeProject = buildRoot.createProject(":type-safe:project", extension)
    
    project.buildFilePath.writeText("""
      baselineProfile {
        from(project(projects.typeSafe.project.path))
      }
      """.trimIndent()
    )
    
    val buildFile = BuildFile(project, mode)
    val rule = TypeSafeProjectAccessorRule("spotlight", mapOf("typeSafe.project" to typeSafeProject))
    assertThat(buildFile.parseDependencies(setOf(rule)))
      .containsExactlyInAnyOrder(typeSafeProject)
  }

  @ParameterizedTest
  @MethodSource("parsingModes")
  fun `projects include all intermediate directories that also have build files in them`(mode: ParsingConfiguration, extension: String) {
    // Create a nested :foo:bar:baz that explicitly depends on nothing but implicitly requires its
    // parent dirs.
    val project = buildRoot.createProject(":foo:bar:baz", extension)
    buildRoot.createProject(":foo", extension)
    buildRoot.createProject(":foo:bar", extension)
    project.buildFilePath.writeText(
      """
      dependencies {
      }
      """.trimIndent()
    )

    val buildFile = BuildFile(project, mode)

    assertThat(buildFile.parseDependencies())
      .containsExactlyInAnyOrder(
        GradlePath(buildRoot, ":foo"),
        GradlePath(buildRoot, ":foo:bar"),
      )
  }

  @ParameterizedTest
  @MethodSource("parsingModes")
  fun `reads dependencies inside any wrapper functions`(mode: ParsingConfiguration, extension: String) {
    val project = buildRoot.createProject(":foo", extension)
    
    val content = if (extension == ".gradle") {
      """
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
    } else {
      """
      dependencies {
        // Common DependencyHandler "wrappers"
        implementation(platform(project(":platform:bom")))
        implementation(enforcedPlatform(project(":enforced:platform")))
        testImplementation(testFixtures(project(":test:fixtures")))

        // Kotlin requires parentheses
        implementation(platform(project(":platform:bom2")))
        testImplementation(testFixtures(project(":test:fixtures2")))

        // Deep nesting, not sure how this would happen, but just in case!
        implementation(first(second(third(project(":deeply:nested")))))
      }
      """.trimIndent()
    }
    project.buildFilePath.writeText(content)

    val buildFile = BuildFile(project, mode)

    assertThat(buildFile.parseDependencies()).containsExactlyInAnyOrder(
      GradlePath(buildRoot, ":platform:bom"),
      GradlePath(buildRoot, ":enforced:platform"),
      GradlePath(buildRoot, ":test:fixtures"),
      GradlePath(buildRoot, ":platform:bom2"),
      GradlePath(buildRoot, ":test:fixtures2"),
      GradlePath(buildRoot, ":deeply:nested")
    )
  }

  @ParameterizedTest
  @MethodSource("parsingModes")
  fun `strips all comments before parsing`(mode: ParsingConfiguration, extension: String) {
    // given
    val project = buildRoot.createProject(":foo", extension)
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
    val dependencies = BuildFile(project, mode).parseDependencies()

    // then
    assertThat(dependencies).containsExactlyInAnyOrder(
        GradlePath(project.root, ":foo"),
        GradlePath(project.root, ":bar")
    )
  }

  private fun Path.createProject(path: String, extension: String = ".gradle"): GradlePath {
    return GradlePath(this, path).apply {
      projectDir.createDirectories()
      projectDir.resolve("build$extension").createFile()
    }
  }
}