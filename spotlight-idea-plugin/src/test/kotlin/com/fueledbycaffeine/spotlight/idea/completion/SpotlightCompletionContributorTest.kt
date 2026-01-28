package com.fueledbycaffeine.spotlight.idea.completion

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpotlightCompletionContributorTest {

  @Test
  fun `isInsideProjectCall detects project call context`() {
    // Valid project call contexts - must have quote after project(
    assertThat(CompletionContextUtils.isInsideProjectCall("""implementation(project("""")).isTrue()
    assertThat(CompletionContextUtils.isInsideProjectCall("""implementation(project(":""")).isTrue()
    assertThat(CompletionContextUtils.isInsideProjectCall("""implementation(project(":feature""")).isTrue()
    assertThat(CompletionContextUtils.isInsideProjectCall("""implementation(project( ":""")).isTrue()
    assertThat(CompletionContextUtils.isInsideProjectCall("""implementation(project('""")).isTrue()
    assertThat(CompletionContextUtils.isInsideProjectCall("""api project(":""")).isTrue()

    // Not in project call context - no quote yet
    assertThat(CompletionContextUtils.isInsideProjectCall("""implementation(project(""")).isFalse()
    assertThat(CompletionContextUtils.isInsideProjectCall("""implementation(""")).isFalse()
    assertThat(CompletionContextUtils.isInsideProjectCall("""dependencies {""")).isFalse()
    assertThat(CompletionContextUtils.isInsideProjectCall("""implementation(libs.someLib)""")).isFalse()
  }

  @Test
  fun `isInsideProjectCall handles IntelliJ dummy identifier`() {
    // With dummy identifier that IntelliJ inserts during completion
    assertThat(CompletionContextUtils.isInsideProjectCall(
      """implementation(project(":featureIntellijIdeaRulezzz"""
    )).isTrue()
    
    // Not in project call - no quote after project(
    assertThat(CompletionContextUtils.isInsideProjectCall(
      """implementation(project(IntellijIdeaRulezzz"""
    )).isFalse()
    
    assertThat(CompletionContextUtils.isInsideProjectCall(
      """implementation(IntellijIdeaRulezzz"""
    )).isFalse()
  }

  @Test
  fun `isTypingTypeSafeAccessor detects accessor context`() {
    // Valid type-safe accessor contexts
    assertThat(CompletionContextUtils.isTypingTypeSafeAccessor("""implementation(projects.""")).isTrue()
    assertThat(CompletionContextUtils.isTypingTypeSafeAccessor("""implementation(projects.feature""")).isTrue()
    assertThat(CompletionContextUtils.isTypingTypeSafeAccessor("""implementation(projects.feature.api""")).isTrue()
    assertThat(CompletionContextUtils.isTypingTypeSafeAccessor("""api(projects.""")).isTrue()

    // Not in type-safe accessor context
    assertThat(CompletionContextUtils.isTypingTypeSafeAccessor("""implementation(project(":""")).isFalse()
    assertThat(CompletionContextUtils.isTypingTypeSafeAccessor("""implementation(libs.""")).isFalse()
    assertThat(CompletionContextUtils.isTypingTypeSafeAccessor("""val projects = """)).isFalse()
    
    // Should NOT match when inside project() call - project() takes precedence
    assertThat(CompletionContextUtils.isTypingTypeSafeAccessor("""projects.foo\nimplementation(project(":""")).isFalse()
  }

  @Test
  fun `isTypingTypeSafeAccessor handles IntelliJ dummy identifier`() {
    assertThat(CompletionContextUtils.isTypingTypeSafeAccessor(
      """implementation(projects.featureIntellijIdeaRulezzz"""
    )).isTrue()
  }

  @Test
  fun `isGradleBuildFile identifies gradle files`() {
    assertThat(CompletionContextUtils.isGradleBuildFile("build.gradle")).isTrue()
    assertThat(CompletionContextUtils.isGradleBuildFile("build.gradle.kts")).isTrue()
    assertThat(CompletionContextUtils.isGradleBuildFile("settings.gradle")).isTrue()
    assertThat(CompletionContextUtils.isGradleBuildFile("settings.gradle.kts")).isTrue()
    assertThat(CompletionContextUtils.isGradleBuildFile("libs.versions.toml")).isFalse()
    assertThat(CompletionContextUtils.isGradleBuildFile("Main.kt")).isFalse()
    assertThat(CompletionContextUtils.isGradleBuildFile("build.gradle.txt")).isFalse()
  }

  @Test
  fun `isIdeProjectsFile identifies ide-projects txt`() {
    assertThat(CompletionContextUtils.isIdeProjectsFile("/path/to/gradle/ide-projects.txt")).isTrue()
    assertThat(CompletionContextUtils.isIdeProjectsFile("gradle/ide-projects.txt")).isTrue()
    assertThat(CompletionContextUtils.isIdeProjectsFile("/path/to/other.txt")).isFalse()
    assertThat(CompletionContextUtils.isIdeProjectsFile(null)).isFalse()
  }

  @Test
  fun `cleanDummyIdentifier removes IntelliJ markers`() {
    assertThat(CompletionContextUtils.cleanDummyIdentifier("someTextIntellijIdeaRulezzz"))
      .isEqualTo("someText")
    assertThat(CompletionContextUtils.cleanDummyIdentifier("project(:featureIntellijIdeaRulezzz123"))
      .isEqualTo("project(:feature")
    assertThat(CompletionContextUtils.cleanDummyIdentifier("normalText"))
      .isEqualTo("normalText")
  }

  @Test
  fun `context detection with complex gradle content`() {
    val gradleContent = """
      dependencies {
        implementation(project(":
    """.trimIndent()
    
    assertThat(CompletionContextUtils.isInsideProjectCall(gradleContent)).isTrue()
    assertThat(CompletionContextUtils.isTypingTypeSafeAccessor(gradleContent)).isFalse()
  }

  @Test
  fun `context detection prioritizes project call over accessor`() {
    // When inside project(), should detect project call even if "projects" appears earlier
    val textWithBoth = """
      // Using projects.feature elsewhere
      implementation(project(":
    """.trimIndent()
    
    assertThat(CompletionContextUtils.isInsideProjectCall(textWithBoth)).isTrue()
    // Type-safe accessor check explicitly returns false when inside project() call
    assertThat(CompletionContextUtils.isTypingTypeSafeAccessor(textWithBoth)).isFalse()
  }

  @Test
  fun `context detection with projects on previous lines`() {
    // Even with projects.xxx on previous lines, project() call takes precedence
    val textWithProjectsAbove = """implementation projects.di.scoping
implementation projects.ffapi
implementation project(":"""
    
    assertThat(CompletionContextUtils.isInsideProjectCall(textWithProjectsAbove)).isTrue()
    assertThat(CompletionContextUtils.isTypingTypeSafeAccessor(textWithProjectsAbove)).isFalse()
  }
}
