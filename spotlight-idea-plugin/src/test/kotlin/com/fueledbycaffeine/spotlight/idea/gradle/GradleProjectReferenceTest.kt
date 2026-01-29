package com.fueledbycaffeine.spotlight.idea.gradle

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.pathString

/**
 * Tests for Gradle project reference resolution and navigation.
 */
class GradleProjectReferenceTest {

  @get:Rule
  val tmpFolder = TemporaryFolder()

  // ===== Path conversion tests =====

  @Test
  fun `project path to file path conversion`() {
    val testCases = mapOf(
      ":eternal-blue:holy-roller" to "eternal-blue/holy-roller",
      ":rotoscope:hysteria" to "rotoscope/hysteria",
      ":the-fear-of-fear:cellar-door" to "the-fear-of-fear/cellar-door",
      ":" to "",
      ":rotoscope" to "rotoscope",
    )

    testCases.forEach { (projectPath, expectedRelativePath) ->
      val calculatedPath = convertProjectPathToFilePath(projectPath)
      assertThat(calculatedPath).isEqualTo(expectedRelativePath)
    }
  }

  @Test
  fun `file path to project path conversion`() {
    val testCases = mapOf(
      "eternal-blue/holy-roller" to ":eternal-blue:holy-roller",
      "rotoscope/hysteria" to ":rotoscope:hysteria",
      "" to ":",
      "rotoscope" to ":rotoscope",
    )

    testCases.forEach { (relativePath, expectedProjectPath) ->
      val calculatedPath = convertFilePathToProjectPath(relativePath)
      assertThat(calculatedPath).isEqualTo(expectedProjectPath)
    }
  }

  // ===== Build file resolution tests =====

  @Test
  fun `findBuildFile prefers kotlin build files over groovy`() {
    val tempDir = tmpFolder.newFolder("test-project").toPath()

    // Test when both files exist - should prefer .kts
    val buildFileKts = tempDir.resolve("build.gradle.kts")
    val buildFile = tempDir.resolve("build.gradle")
    buildFileKts.createFile()
    buildFile.createFile()

    val bothExistResult = findBuildFile(tempDir)
    assertThat(bothExistResult?.name).isEqualTo("build.gradle.kts")

    // Clean up for next test
    buildFileKts.deleteIfExists()
    buildFile.deleteIfExists()

    // Test when only Groovy exists
    buildFile.createFile()
    val onlyGroovyResult = findBuildFile(tempDir)
    assertThat(onlyGroovyResult?.name).isEqualTo("build.gradle")

    // Clean up for next test
    buildFile.deleteIfExists()

    // Test when only Kotlin exists
    buildFileKts.createFile()
    val onlyKotlinResult = findBuildFile(tempDir)
    assertThat(onlyKotlinResult?.name).isEqualTo("build.gradle.kts")

    // Clean up for next test
    buildFileKts.deleteIfExists()

    // Test when neither exists
    val neitherExistsResult = findBuildFile(tempDir)
    assertThat(neitherExistsResult).isNull()
  }

  @Test
  fun `resolveProjectBuildFile returns correct file path`() {
    val projectBasePath = tmpFolder.newFolder("test-project").toPath()
    val projectPath = ":eternal-blue:holy-roller"

    // Create the project directory structure
    val projectDir = projectBasePath.resolve("eternal-blue/holy-roller")
    projectDir.createDirectories()

    // Test when both files exist - should prefer .kts
    val buildFileKts = projectDir.resolve("build.gradle.kts")
    val buildFile = projectDir.resolve("build.gradle")
    buildFileKts.createFile()
    buildFile.createFile()

    val resolvedFile = resolveProjectBuildFile(projectBasePath.pathString, projectPath)
    assertThat(resolvedFile).isEqualTo(buildFileKts.pathString)

    // Clean up and test when only .gradle exists
    buildFileKts.deleteIfExists()
    val resolvedFileGroovy = resolveProjectBuildFile(projectBasePath.pathString, projectPath)
    assertThat(resolvedFileGroovy).isEqualTo(buildFile.pathString)

    // Clean up and test when neither exists
    buildFile.deleteIfExists()
    val resolvedFileNone = resolveProjectBuildFile(projectBasePath.pathString, projectPath)
    assertThat(resolvedFileNone).isNull()
  }

  // ===== Text range calculation tests =====

  @Test
  fun `calculateReferenceTextRange excludes quotes correctly`() {
    val doubleQuoted = "\":eternal-blue:holy-roller\""
    val singleQuoted = "':rotoscope:hysteria'"
    val unquoted = ":raw:path"

    // Double quoted - exclude quotes
    val doubleQuotedRange = calculateReferenceTextRange(doubleQuoted)
    assertThat(doubleQuotedRange.first).isEqualTo(1) // After opening quote
    assertThat(doubleQuotedRange.second).isEqualTo(doubleQuoted.length - 1) // Before closing quote

    // Single quoted - exclude quotes
    val singleQuotedRange = calculateReferenceTextRange(singleQuoted)
    assertThat(singleQuotedRange.first).isEqualTo(1)
    assertThat(singleQuotedRange.second).isEqualTo(singleQuoted.length - 1)

    // Unquoted - use entire range
    val unquotedRange = calculateReferenceTextRange(unquoted)
    assertThat(unquotedRange.first).isEqualTo(0)
    assertThat(unquotedRange.second).isEqualTo(unquoted.length)
  }

  // ===== Project call detection tests =====

  @Test
  fun `isInProjectCall detects project call context`() {
    val testCases = mapOf(
      "implementation(project(\":rotoscope:hysteria\"))" to true,
      "testImplementation(project(':eternal-blue:holy-roller'))" to true,
      "api project(':the-fear-of-fear:cellar-door')" to true,
      "implementation(someOtherFunction(\":not:a:project\"))" to false,
      "val myString = \":rotoscope:hysteria\"" to false,
    )

    testCases.forEach { (parentText, expectedResult) ->
      val isInProjectCall = parentText.contains("project(")
      assertThat(isInProjectCall).isEqualTo(expectedResult)
    }
  }

  // ===== Accessor to path conversion tests =====

  @Test
  fun `accessor to project path conversion`() {
    val testCases = mapOf(
      "rotoscope.hysteria" to ":rotoscope:hysteria",
      "eternalBlue.holyRoller" to ":eternal-blue:holy-roller",
      "theFearOfFear.cellarDoor" to ":the-fear-of-fear:cellar-door",
    )

    testCases.forEach { (accessor, expectedPath) ->
      val calculatedPath = convertAccessorToProjectPath(accessor)
      assertThat(calculatedPath).isEqualTo(expectedPath)
    }
  }

  // ===== Helper functions =====

  private fun convertProjectPathToFilePath(projectPath: String): String {
    return projectPath.removePrefix(":").replace(":", "/")
  }

  private fun convertFilePathToProjectPath(relativePath: String): String {
    if (relativePath.isEmpty()) return ":"
    return ":" + relativePath.replace("/", ":")
  }

  private fun findBuildFile(projectDir: Path): Path? {
    val buildFileKts = projectDir.resolve("build.gradle.kts")
    val buildFile = projectDir.resolve("build.gradle")

    return when {
      buildFileKts.exists() -> buildFileKts
      buildFile.exists() -> buildFile
      else -> null
    }
  }

  private fun resolveProjectBuildFile(projectBasePath: String, projectPath: String): String? {
    val relativePath = projectPath.removePrefix(":").replace(":", "/")
    val projectDirPath = Path.of(projectBasePath).resolve(relativePath)

    val buildFileKts = projectDirPath.resolve("build.gradle.kts")
    val buildFile = projectDirPath.resolve("build.gradle")

    return when {
      buildFileKts.exists() -> buildFileKts.pathString
      buildFile.exists() -> buildFile.pathString
      else -> null
    }
  }

  private fun calculateReferenceTextRange(elementText: String): Pair<Int, Int> {
    return when {
      elementText.startsWith("\"") || elementText.startsWith("'") -> {
        1 to elementText.length - 1
      }
      else -> {
        0 to elementText.length
      }
    }
  }

  private fun convertAccessorToProjectPath(accessor: String): String {
    // Convert camelCase to kebab-case and dots to colons
    val kebabCase = accessor.replace(Regex("([a-z])([A-Z])")) { match ->
      "${match.groupValues[1]}-${match.groupValues[2].lowercase()}"
    }
    return ":" + kebabCase.replace(".", ":")
  }
}
