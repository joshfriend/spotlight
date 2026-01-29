package com.fueledbycaffeine.spotlight.idea.annotator

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.idea.gradle.GradleProjectPathUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.file.Path

/**
 * Tests for [SpotlightAnnotator] pattern matching and validation logic.
 */
class SpotlightAnnotatorTest {

  // ===== PROJECT_CALL_PATTERN tests =====

  @Test
  fun `PROJECT_CALL_PATTERN matches valid project calls with double quotes`() {
    // Pattern requires complete calls: project("path")
    val validCalls = mapOf(
      """project(":eternal-blue:holy-roller")""" to ":eternal-blue:holy-roller",
      """project(":rotoscope:hysteria")""" to ":rotoscope:hysteria",
      """project(":the-fear-of-fear:cellar-door")""" to ":the-fear-of-fear:cellar-door",
    )

    validCalls.forEach { (call, expectedPath) ->
      val match = GradleProjectPathUtils.PROJECT_CALL_PATTERN.find(call)
      assertThat(match).isNotNull()
      assertThat(match!!.groupValues[2]).isEqualTo(expectedPath)
    }
  }

  @Test
  fun `PROJECT_CALL_PATTERN matches valid project calls with single quotes`() {
    val validCalls = mapOf(
      """project(':eternal-blue:secret-garden')""" to ":eternal-blue:secret-garden",
      """project(':rotoscope:sew-me-up')""" to ":rotoscope:sew-me-up",
    )

    validCalls.forEach { (call, expectedPath) ->
      val match = GradleProjectPathUtils.PROJECT_CALL_PATTERN.find(call)
      assertThat(match).isNotNull()
      assertThat(match!!.groupValues[2]).isEqualTo(expectedPath)
    }
  }

  @Test
  fun `PROJECT_CALL_PATTERN does not match invalid calls`() {
    val invalidCalls = listOf(
      """project()""",                     // Empty
      """project(variable)""",             // Variable reference
      """projects(":some:path")""",        // Wrong function name
      """project(":unclosed""",            // Unclosed quote - no closing paren
      """someOtherFunction(":not:a:project")""", // Different function
    )

    invalidCalls.forEach { call ->
      val match = GradleProjectPathUtils.PROJECT_CALL_PATTERN.find(call)
      assertThat(match).isNull()
    }
  }

  @Test
  fun `PROJECT_CALL_PATTERN extracts paths from complex gradle content`() {
    val gradleContent = """
      dependencies {
        implementation(project(":eternal-blue:holy-roller"))
        testImplementation(project(':rotoscope:hysteria'))
        api(project(":the-fear-of-fear:cellar-door"))
        runtimeOnly(project(':tsunami-sea:fata-morgana'))
      }
    """.trimIndent()

    val matches = GradleProjectPathUtils.PROJECT_CALL_PATTERN.findAll(gradleContent).toList()
    val extractedPaths = matches.map { it.groupValues[2] }

    assertThat(extractedPaths).containsExactly(
      ":eternal-blue:holy-roller",
      ":rotoscope:hysteria",
      ":the-fear-of-fear:cellar-door",
      ":tsunami-sea:fata-morgana",
    ).inOrder()
  }

  // ===== TYPE_SAFE_ACCESSOR_PATTERN tests =====

  @Test
  fun `TYPE_SAFE_ACCESSOR_PATTERN matches valid accessors`() {
    val validAccessors = listOf(
      "projects.eternalBlue.holyRoller",
      "projects.rotoscope.hysteria",
      "projects.theFearOfFear.cellarDoor",
      "projects.tsunamiSea.fataMorgana",
    )

    validAccessors.forEach { accessor ->
      val match = GradleProjectPathUtils.TYPE_SAFE_ACCESSOR_PATTERN.find(accessor)
      assertThat(match).isNotNull()
      assertThat(match!!.groupValues[1]).isEqualTo(accessor)
    }
  }

  @Test
  fun `TYPE_SAFE_ACCESSOR_PATTERN does not match non-projects accessors`() {
    val invalidAccessors = listOf(
      "libs.someLibrary",
      "versions.kotlin",
      "myProjects.something",
      "project.eternalBlue.holyRoller",  // singular, not plural
    )

    invalidAccessors.forEach { accessor ->
      val match = GradleProjectPathUtils.TYPE_SAFE_ACCESSOR_PATTERN.find(accessor)
      if (match != null) {
        // If there's a match, it shouldn't be for the whole thing
        assertThat(match.groupValues[1]).doesNotContain(accessor)
      }
    }
  }

  @Test
  fun `TYPE_SAFE_ACCESSOR_PATTERN extracts accessors from complex gradle content`() {
    val gradleContent = """
      dependencies {
        implementation(projects.eternalBlue.holyRoller)
        implementation(projects.rotoscope.hysteria)
        api(projects.theFearOfFear.cellarDoor)
        testImplementation(libs.junit)
      }
    """.trimIndent()

    val matches = GradleProjectPathUtils.TYPE_SAFE_ACCESSOR_PATTERN.findAll(gradleContent).toList()
    val extractedAccessors = matches.map { it.groupValues[1] }

    assertThat(extractedAccessors).containsExactly(
      "projects.eternalBlue.holyRoller",
      "projects.rotoscope.hysteria",
      "projects.theFearOfFear.cellarDoor",
    ).inOrder()
  }

  // ===== Path validation tests =====

  @Test
  fun `isValidProjectPath validates exact matches`() {
    val allProjects = createTestGradlePaths(
      ":rotoscope:hysteria",
      ":rotoscope:sew-me-up",
      ":eternal-blue:holy-roller",
    )

    assertThat(GradleProjectPathUtils.isValidProjectPath(":rotoscope:hysteria", allProjects)).isTrue()
    assertThat(GradleProjectPathUtils.isValidProjectPath(":rotoscope:sew-me-up", allProjects)).isTrue()
    assertThat(GradleProjectPathUtils.isValidProjectPath(":nonexistent", allProjects)).isFalse()
  }

  @Test
  fun `isValidProjectPath validates prefix paths`() {
    val allProjects = createTestGradlePaths(
      ":eternal-blue:holy-roller",
      ":eternal-blue:secret-garden",
      ":rotoscope:hysteria",
    )

    // :eternal-blue is a valid prefix because :eternal-blue:holy-roller exists
    assertThat(GradleProjectPathUtils.isValidProjectPath(":eternal-blue", allProjects)).isTrue()
    assertThat(GradleProjectPathUtils.isValidProjectPath(":rotoscope", allProjects)).isTrue()
    assertThat(GradleProjectPathUtils.isValidProjectPath(":nonexistent", allProjects)).isFalse()
  }

  @Test
  fun `isValidAccessor validates exact matches`() {
    val allProjects = createTestGradlePaths(
      ":rotoscope:hysteria",
      ":eternal-blue:holy-roller",
    )
    val accessorMap = GradleProjectPathUtils.buildAccessorMap(allProjects)

    assertThat(GradleProjectPathUtils.isValidAccessor("rotoscope.hysteria", accessorMap)).isTrue()
    assertThat(GradleProjectPathUtils.isValidAccessor("eternalBlue.holyRoller", accessorMap)).isTrue()
    assertThat(GradleProjectPathUtils.isValidAccessor("nonexistent", accessorMap)).isFalse()
  }

  @Test
  fun `isValidAccessor validates prefix accessors`() {
    val allProjects = createTestGradlePaths(
      ":eternal-blue:holy-roller",
      ":eternal-blue:secret-garden",
    )
    val accessorMap = GradleProjectPathUtils.buildAccessorMap(allProjects)

    // eternalBlue is a valid prefix because eternalBlue.holyRoller exists
    assertThat(GradleProjectPathUtils.isValidAccessor("eternalBlue", accessorMap)).isTrue()
    assertThat(GradleProjectPathUtils.isValidAccessor("nonexistent", accessorMap)).isFalse()
  }

  // ===== Accessor map building tests =====

  @Test
  fun `buildAccessorMap creates correct mappings`() {
    val allProjects = createTestGradlePaths(
      ":rotoscope:hysteria",
      ":eternal-blue:holy-roller",
      ":the-fear-of-fear:cellar-door",
    )
    val accessorMap = GradleProjectPathUtils.buildAccessorMap(allProjects)

    assertThat(accessorMap).containsKey("rotoscope.hysteria")
    assertThat(accessorMap).containsKey("eternalBlue.holyRoller")
    assertThat(accessorMap).containsKey("theFearOfFear.cellarDoor")
  }

  @Test
  fun `cleanTypeSafeAccessor handles various formats`() {
    // Remove projects. prefix
    assertThat(GradleProjectPathUtils.cleanTypeSafeAccessor("projects.eternalBlue.holyRoller"))
      .isEqualTo("eternalBlue.holyRoller")
    
    // Remove .dependencyProject suffix
    assertThat(GradleProjectPathUtils.cleanTypeSafeAccessor("rotoscope.hysteria.dependencyProject"))
      .isEqualTo("rotoscope.hysteria")
    
    // Remove .path suffix
    assertThat(GradleProjectPathUtils.cleanTypeSafeAccessor("rotoscope.hysteria.path"))
      .isEqualTo("rotoscope.hysteria")
    
    // Handle combined
    assertThat(GradleProjectPathUtils.cleanTypeSafeAccessor("projects.eternalBlue.holyRoller.dependencyProject"))
      .isEqualTo("eternalBlue.holyRoller")
    
    // No changes needed
    assertThat(GradleProjectPathUtils.cleanTypeSafeAccessor("rotoscope.hysteria"))
      .isEqualTo("rotoscope.hysteria")
  }

  // ===== Helper functions =====

  private fun createTestGradlePaths(vararg paths: String): Set<GradlePath> {
    val rootDir = Path.of("/test/root")
    return paths.map { GradlePath(rootDir, it) }.toSet()
  }
}
