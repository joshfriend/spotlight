package com.fueledbycaffeine.spotlight.idea.annotator

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.idea.gradle.GradleProjectPathUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.file.Path

/**
 * Integration tests for [SpotlightAnnotator] covering end-to-end scenarios.
 * Tests the actual production code in [GradleProjectPathUtils].
 */
class SpotlightAnnotatorIntegrationTest {

  private val rootDir: Path = Path.of("/test/root")

  // ===== ide-projects.txt validation tests =====

  @Test
  fun `isValidIdeProjectPath validates exact paths`() {
    val allProjects = createTestGradlePaths(
      ":rotoscope:hysteria",
      ":eternal-blue:holy-roller",
    )

    assertThat(GradleProjectPathUtils.isValidIdeProjectPath(":rotoscope:hysteria", allProjects)).isTrue()
    assertThat(GradleProjectPathUtils.isValidIdeProjectPath(":eternal-blue:holy-roller", allProjects)).isTrue()
    assertThat(GradleProjectPathUtils.isValidIdeProjectPath(":nonexistent", allProjects)).isFalse()
  }

  @Test
  fun `isValidIdeProjectPath validates glob patterns`() {
    val allProjects = createTestGradlePaths(
      ":rotoscope:hysteria",
      ":rotoscope:rotoscope",
      ":rotoscope:sew-me-up",
      ":eternal-blue:holy-roller",
      ":eternal-blue:secret-garden",
    )

    // :rotoscope:* should match all rotoscope projects
    assertThat(GradleProjectPathUtils.isValidIdeProjectPath(":rotoscope:*", allProjects)).isTrue()
    
    // :eternal-blue:* should match only eternal-blue projects
    assertThat(GradleProjectPathUtils.isValidIdeProjectPath(":eternal-blue:*", allProjects)).isTrue()
    
    // :nonexistent:* should not match anything
    assertThat(GradleProjectPathUtils.isValidIdeProjectPath(":nonexistent:*", allProjects)).isFalse()
  }

  @Test
  fun `matchesAnyProject with single wildcard`() {
    val allProjects = createTestGradlePaths(
      ":rotoscope:hysteria",
      ":rotoscope:sew-me-up",
      ":eternal-blue:holy-roller",
    )

    assertThat(GradleProjectPathUtils.matchesAnyProject(":rotoscope:*", allProjects)).isTrue()
    assertThat(GradleProjectPathUtils.matchesAnyProject(":eternal-blue:*", allProjects)).isTrue()
    assertThat(GradleProjectPathUtils.matchesAnyProject(":foo:*", allProjects)).isFalse()
  }

  @Test
  fun `matchesAnyProject with question mark wildcard`() {
    val allProjects = createTestGradlePaths(
      ":tsunami-sea:a",
      ":tsunami-sea:b",
      ":eternal-blue:sun-killer",
    )

    // ? matches single character
    assertThat(GradleProjectPathUtils.matchesAnyProject(":tsunami-sea:?", allProjects)).isTrue()
    assertThat(GradleProjectPathUtils.matchesAnyProject(":tsunami-sea:??", allProjects)).isFalse()
  }

  @Test
  fun `containsGlobChar detects wildcards`() {
    assertThat(GradleProjectPathUtils.containsGlobChar(":rotoscope:*")).isTrue()
    assertThat(GradleProjectPathUtils.containsGlobChar(":eternal-blue:?")).isTrue()
    assertThat(GradleProjectPathUtils.containsGlobChar(":rotoscope:hysteria")).isFalse()
    assertThat(GradleProjectPathUtils.containsGlobChar("")).isFalse()
  }

  // ===== build.gradle content parsing tests =====

  @Test
  fun `gradle content with mixed dependencies parses correctly`() {
    val gradleContent = """
      plugins {
        id("com.android.library")
        kotlin("jvm")
      }
      
      dependencies {
        implementation(project(":rotoscope:hysteria"))
        implementation(projects.eternalBlue.holyRoller)
        testImplementation(libs.junit)
        api(project(':the-fear-of-fear:cellar-door'))
        
        // Comment with project(":fake:path") in it - this WILL match since pattern doesn't exclude comments
        runtimeOnly(project(":tsunami-sea:fata-morgana"))
      }
    """.trimIndent()

    // Extract project() calls - note: the comment also matches since it contains a valid project() call
    val projectCalls = GradleProjectPathUtils.PROJECT_CALL_PATTERN.findAll(gradleContent)
      .map { it.groupValues[2] }
      .toList()

    assertThat(projectCalls).containsExactly(
      ":rotoscope:hysteria",
      ":the-fear-of-fear:cellar-door",
      ":fake:path",  // From the comment - pattern doesn't exclude comments
      ":tsunami-sea:fata-morgana",
    ).inOrder()

    // Extract type-safe accessors
    val accessors = GradleProjectPathUtils.TYPE_SAFE_ACCESSOR_PATTERN.findAll(gradleContent)
      .map { it.groupValues[1] }
      .toList()

    assertThat(accessors).containsExactly(
      "projects.eternalBlue.holyRoller",
    )
  }

  @Test
  fun `gradle content validation detects invalid paths`() {
    val allProjects = createTestGradlePaths(
      ":rotoscope:hysteria",
      ":eternal-blue:holy-roller",
    )

    val gradleContent = """
      dependencies {
        implementation(project(":rotoscope:hysteria"))        // Valid
        implementation(project(":invalid:path"))              // Invalid
        api(project(":eternal-blue:holy-roller"))             // Valid
        testImplementation(project(":nonexistent"))           // Invalid
      }
    """.trimIndent()

    val projectCalls = GradleProjectPathUtils.PROJECT_CALL_PATTERN.findAll(gradleContent)
      .map { it.groupValues[2] }
      .toList()

    val validPaths = projectCalls.filter { path ->
      GradleProjectPathUtils.isValidProjectPath(path, allProjects)
    }
    val invalidPaths = projectCalls.filter { path ->
      !GradleProjectPathUtils.isValidProjectPath(path, allProjects)
    }

    assertThat(validPaths).containsExactly(":rotoscope:hysteria", ":eternal-blue:holy-roller")
    assertThat(invalidPaths).containsExactly(":invalid:path", ":nonexistent")
  }

  // ===== Best match suggestion tests =====

  @Test
  fun `findBestMatchingPath returns best fuzzy match`() {
    val allProjects = createTestGradlePaths(
      ":eternal-blue:holy-roller",
      ":eternal-blue:halcyon",
      ":the-fear-of-fear:too-close-too-late",
      ":rotoscope:hysteria",
    )

    // "ebhr" should match ":eternal-blue:holy-roller"
    val bestMatch = GradleProjectPathUtils.findBestMatchingPath(":ebhr", allProjects)
    assertThat(bestMatch?.path).isEqualTo(":eternal-blue:holy-roller")
  }

  @Test
  fun `findBestMatchingPath returns match even for partial prefixes`() {
    val allProjects = createTestGradlePaths(
      ":rotoscope:hysteria",
      ":eternal-blue:holy-roller",
    )

    // Even a poor match will return something due to subsequence matching
    val bestMatch = GradleProjectPathUtils.findBestMatchingPath(":r", allProjects)
    assertThat(bestMatch).isNotNull()
    assertThat(bestMatch?.path).isEqualTo(":rotoscope:hysteria")
  }

  @Test
  fun `findBestMatchingAccessor returns best fuzzy match`() {
    val allProjects = createTestGradlePaths(
      ":eternal-blue:holy-roller",
      ":eternal-blue:halcyon",
      ":rotoscope:hysteria",
    )
    val accessorMap = GradleProjectPathUtils.buildAccessorMap(allProjects)

    val bestMatch = GradleProjectPathUtils.findBestMatchingAccessor("ebhr", accessorMap)
    assertThat(bestMatch?.typeSafeAccessorName).isEqualTo("eternalBlue.holyRoller")
  }

  // ===== File type detection tests =====

  @Test
  fun `isGradleBuildFile correctly identifies gradle files`() {
    // Valid gradle files
    assertThat(GradleProjectPathUtils.isGradleBuildFile("build.gradle")).isTrue()
    assertThat(GradleProjectPathUtils.isGradleBuildFile("build.gradle.kts")).isTrue()
    assertThat(GradleProjectPathUtils.isGradleBuildFile("settings.gradle")).isTrue()
    assertThat(GradleProjectPathUtils.isGradleBuildFile("settings.gradle.kts")).isTrue()
    assertThat(GradleProjectPathUtils.isGradleBuildFile("custom.gradle")).isTrue()
    assertThat(GradleProjectPathUtils.isGradleBuildFile("custom.gradle.kts")).isTrue()

    // Invalid gradle files
    assertThat(GradleProjectPathUtils.isGradleBuildFile("build.gradle.txt")).isFalse()
    assertThat(GradleProjectPathUtils.isGradleBuildFile("gradle.properties")).isFalse()
    assertThat(GradleProjectPathUtils.isGradleBuildFile("libs.versions.toml")).isFalse()
    assertThat(GradleProjectPathUtils.isGradleBuildFile("Main.kt")).isFalse()
    assertThat(GradleProjectPathUtils.isGradleBuildFile(null)).isFalse()
  }

  // ===== Complex annotation scenarios =====

  @Test
  fun `annotator handles nested project calls correctly`() {
    // The annotator should not process parent elements that contain project() 
    // if children also contain project()
    val parentText = """implementation(project(":rotoscope:hysteria"))"""
    val childText = """project(":rotoscope:hysteria")"""

    // Both contain "project(" but the pattern should extract from the most specific element
    val parentMatches = GradleProjectPathUtils.PROJECT_CALL_PATTERN.findAll(parentText).count()
    val childMatches = GradleProjectPathUtils.PROJECT_CALL_PATTERN.findAll(childText).count()

    assertThat(parentMatches).isEqualTo(1)
    assertThat(childMatches).isEqualTo(1)
  }

  @Test
  fun `annotator handles single line project calls in multiline context`() {
    // The regex pattern requires project("path") on a single line
    val gradleContent = """
      dependencies {
        implementation(project(":rotoscope:hysteria"))
        api(project(":eternal-blue:holy-roller"))
      }
    """.trimIndent()

    val matches = GradleProjectPathUtils.PROJECT_CALL_PATTERN.findAll(gradleContent).toList()
    assertThat(matches).hasSize(2)
    assertThat(matches[0].groupValues[2]).isEqualTo(":rotoscope:hysteria")
    assertThat(matches[1].groupValues[2]).isEqualTo(":eternal-blue:holy-roller")
  }

  // ===== Helper functions =====

  private fun createTestGradlePaths(vararg paths: String): Set<GradlePath> {
    return paths.map { GradlePath(rootDir, it) }.toSet()
  }
}
