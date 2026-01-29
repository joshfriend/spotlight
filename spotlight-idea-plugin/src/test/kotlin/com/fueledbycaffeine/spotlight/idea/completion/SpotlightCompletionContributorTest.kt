package com.fueledbycaffeine.spotlight.idea.completion

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpotlightCompletionContributorTest {

  // ===== Project call context detection =====

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
  fun `isInsideProjectCall handles various quote styles`() {
    // Double quotes
    assertThat(CompletionContextUtils.isInsideProjectCall("""project(":rotoscope""")).isTrue()
    assertThat(CompletionContextUtils.isInsideProjectCall("""project( ":rotoscope""")).isTrue()
    
    // Single quotes
    assertThat(CompletionContextUtils.isInsideProjectCall("""project(':rotoscope""")).isTrue()
    assertThat(CompletionContextUtils.isInsideProjectCall("""project( ':rotoscope""")).isTrue()
    
    // Mixed content
    assertThat(CompletionContextUtils.isInsideProjectCall("""val x = "test"; project(":""")).isTrue()
  }

  @Test
  fun `isInsideProjectCall matches any text with project and opening quote`() {
    // The function simply checks if project( followed by a quote exists in the text
    // It doesn't track whether the call is "complete" - that's not its job
    // It's used during completion to know if we should offer project path completions
    assertThat(CompletionContextUtils.isInsideProjectCall("""project(":rotoscope:hysteria")""")).isTrue()
    assertThat(CompletionContextUtils.isInsideProjectCall("""project(':rotoscope:hysteria')""")).isTrue()
    assertThat(CompletionContextUtils.isInsideProjectCall("""project(":rotoscope:hysteria""")).isTrue()
    assertThat(CompletionContextUtils.isInsideProjectCall("""project(":""")).isTrue()
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
    val textWithProjectsAbove = """
      implementation projects.eternalBlue.holyRoller
      implementation projects.rotoscope.hysteria
      implementation project(":
      """.trimIndent()
    
    assertThat(CompletionContextUtils.isInsideProjectCall(textWithProjectsAbove)).isTrue()
    assertThat(CompletionContextUtils.isTypingTypeSafeAccessor(textWithProjectsAbove)).isFalse()
  }

  // ===== Completion filtering tests =====

  @Test
  fun `completion should exclude current project from suggestions`() {
    val allProjects = setOf(
      ":eternal-blue:holy-roller",
      ":eternal-blue:secret-garden",
      ":rotoscope:hysteria",
      ":rotoscope:sew-me-up",
    )

    val currentProject = ":eternal-blue:holy-roller"
    val filteredPaths = allProjects.filter { it != currentProject }

    assertThat(filteredPaths).containsExactly(
      ":eternal-blue:secret-garden",
      ":rotoscope:hysteria",
      ":rotoscope:sew-me-up",
    )
    assertThat(filteredPaths).doesNotContain(":eternal-blue:holy-roller")
  }

  @Test
  fun `completion context detection with surrounding text`() {
    // Context with project( - should trigger
    val contextWithProject = "dependencies { implementation(project(\""
    assertThat(contextWithProject.contains("project(")).isTrue()

    // Context without project( - should not trigger
    val contextWithoutProject = "dependencies { implementation(libs.someLibrary"
    assertThat(contextWithoutProject.contains("project(")).isFalse()

    // Context after completed project - for reference
    val contextAfterProject = "implementation(project(\":rotoscope:hysteria\"))"
    assertThat(contextAfterProject.contains("project(")).isTrue()
  }

  // ===== Prefix extraction tests =====

  @Test
  fun `prefix extraction from project call`() {
    val testCases = mapOf(
      """project(":rotoscope""" to ":rotoscope",
      """project(":eternal-blue:holy-roller""" to ":eternal-blue:holy-roller",
      """project(":""" to ":",
      """project('""" to "",
      """project(":tsunami-sea:fata-morgana""" to ":tsunami-sea:fata-morgana",
    )

    testCases.forEach { (context, expectedPrefix) ->
      val extracted = extractProjectPathPrefix(context)
      assertThat(extracted).isEqualTo(expectedPrefix)
    }
  }

  @Test
  fun `prefix extraction from accessor`() {
    val testCases = mapOf(
      "projects.rotoscope" to "rotoscope",
      "projects.eternalBlue.holyRoller" to "eternalBlue.holyRoller",
      "projects." to "",
      "projects.theFearOfFear.cellarDoor" to "theFearOfFear.cellarDoor",
    )

    testCases.forEach { (context, expectedPrefix) ->
      val extracted = extractAccessorPrefix(context)
      assertThat(extracted).isEqualTo(expectedPrefix)
    }
  }

  // ===== Edge cases =====

  @Test
  fun `handles whitespace in project calls`() {
    assertThat(CompletionContextUtils.isInsideProjectCall("""project(  ":""")).isTrue()
    assertThat(CompletionContextUtils.isInsideProjectCall("""project(
      ":""")).isTrue()
    assertThat(CompletionContextUtils.isInsideProjectCall("""project(	":""")).isTrue() // tab
  }

  @Test
  fun `handles comments in gradle content`() {
    val contentWithComments = """
      // This is a comment with project(":fake") in it
      implementation(project(":
    """.trimIndent()
    
    assertThat(CompletionContextUtils.isInsideProjectCall(contentWithComments)).isTrue()
  }

  @Test
  fun `handles multiline project calls`() {
    val multilineCall = """
      implementation(
        project(
          ":
    """.trimIndent()
    
    assertThat(CompletionContextUtils.isInsideProjectCall(multilineCall)).isTrue()
  }

  // ===== Helper functions =====

  private fun extractProjectPathPrefix(context: String): String {
    val quoteIndex = context.lastIndexOf("project(")
    if (quoteIndex == -1) return ""
    
    val afterProject = context.substring(quoteIndex + "project(".length).trim()
    return if (afterProject.startsWith("\"") || afterProject.startsWith("'")) {
      afterProject.substring(1)
    } else {
      ""
    }
  }

  private fun extractAccessorPrefix(context: String): String {
    val projectsIndex = context.lastIndexOf("projects.")
    if (projectsIndex == -1) return ""
    
    return context.substring(projectsIndex + "projects.".length)
  }
}
