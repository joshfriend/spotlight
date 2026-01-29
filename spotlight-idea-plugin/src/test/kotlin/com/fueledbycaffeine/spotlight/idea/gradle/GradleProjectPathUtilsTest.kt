package com.fueledbycaffeine.spotlight.idea.gradle

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GradleProjectPathUtilsTest {

  @Test
  fun `calculateFuzzyScore returns highest score for exact match`() {
    val score = GradleProjectPathUtils.calculateFuzzyScore("eternal-blue:holy-roller", "eternal-blue:holy-roller")
    assertThat(score).isEqualTo(10000)
  }

  @Test
  fun `calculateFuzzyScore returns high score for prefix match`() {
    val score = GradleProjectPathUtils.calculateFuzzyScore("eternal-blue:holy-roller", "eternal")
    assertThat(score).isGreaterThan(800)
  }

  @Test
  fun `calculateFuzzyScore returns good score for contains match`() {
    val score = GradleProjectPathUtils.calculateFuzzyScore("eternal-blue:holy-roller", "holy")
    assertThat(score).isGreaterThan(700)
  }

  @Test
  fun `calculateFuzzyScore matches acronyms across segments`() {
    // "eb" should match "eternal-blue" (e=eternal, b=blue)
    val ebScore = GradleProjectPathUtils.calculateFuzzyScore("eternal-blue", "eb")
    assertThat(ebScore).isGreaterThan(0)
    
    // "ebhr" should match "eternal-blue:holy-roller"
    val ebhrScore = GradleProjectPathUtils.calculateFuzzyScore("eternal-blue:holy-roller", "ebhr")
    assertThat(ebhrScore).isGreaterThan(0)
  }

  @Test
  fun `calculateFuzzyScore prefers better acronym matches`() {
    // "ebhr" should score higher for "eternal-blue:holy-roller" than for a longer path
    val eternalBlueScore = GradleProjectPathUtils.calculateFuzzyScore("eternal-blue:holy-roller", "ebhr")
    val longerPathScore = GradleProjectPathUtils.calculateFuzzyScore("the-fear-of-fear:too-close-too-late", "ebhr")
    
    assertThat(eternalBlueScore).isGreaterThan(longerPathScore)
  }

  @Test
  fun `calculateFuzzyScore prefers shorter paths with same acronym match`() {
    // "rh" matches both, but rotoscope:hysteria is shorter (fewer segments)
    val rotoscopeScore = GradleProjectPathUtils.calculateFuzzyScore("rotoscope:hysteria", "rh")
    val longerScore = GradleProjectPathUtils.calculateFuzzyScore("rotoscope:rotoscope:hysteria", "rh")
    
    assertThat(rotoscopeScore).isGreaterThan(longerScore)
  }

  @Test
  fun `calculateFuzzyScore penalizes skipped segments`() {
    // "rs" with "rotoscope:sew-me-up" has no skipped segments (r→rotoscope, s→sew-me-up)
    // "rs" with "rotoscope:hysteria:sew-me-up" skips "hysteria"
    val directScore = GradleProjectPathUtils.calculateFuzzyScore("rotoscope:sew-me-up", "rs")
    val skippedScore = GradleProjectPathUtils.calculateFuzzyScore("rotoscope:hysteria:sew-me-up", "rs")
    
    assertThat(directScore).isGreaterThan(skippedScore)
  }

  @Test
  fun `calculateFuzzyScore handles camelCase segments`() {
    // "eb" should match "eternalBlue" (e=eternal, b=Blue)
    val score = GradleProjectPathUtils.calculateFuzzyScore("eternalBlue", "eb")
    assertThat(score).isGreaterThan(0)
  }

  @Test
  fun `calculateFuzzyScore returns zero for no match`() {
    val score = GradleProjectPathUtils.calculateFuzzyScore("eternal-blue:holy-roller", "xyz")
    assertThat(score).isEqualTo(0)
  }

  @Test
  fun `calculateFuzzyScore handles empty prefix`() {
    val score = GradleProjectPathUtils.calculateFuzzyScore("eternal-blue:holy-roller", "")
    assertThat(score).isGreaterThan(0) // Should match anything
  }

  @Test
  fun `calculateFuzzyScore handles subsequence matching`() {
    // "eahr" should match "eternal-blue:holy-roller" as subsequence
    val score = GradleProjectPathUtils.calculateFuzzyScore("eternal-blue:holy-roller", "eahr")
    assertThat(score).isGreaterThan(0)
  }

  @Test
  fun `isGradleBuildFile identifies gradle files correctly`() {
    assertThat(GradleProjectPathUtils.isGradleBuildFile("build.gradle")).isTrue()
    assertThat(GradleProjectPathUtils.isGradleBuildFile("build.gradle.kts")).isTrue()
    assertThat(GradleProjectPathUtils.isGradleBuildFile("settings.gradle")).isTrue()
    assertThat(GradleProjectPathUtils.isGradleBuildFile("Main.kt")).isFalse()
    assertThat(GradleProjectPathUtils.isGradleBuildFile(null)).isFalse()
  }

  @Test
  fun `cleanTypeSafeAccessor removes prefixes and suffixes`() {
    assertThat(GradleProjectPathUtils.cleanTypeSafeAccessor("projects.eternalBlue.holyRoller"))
      .isEqualTo("eternalBlue.holyRoller")
    assertThat(GradleProjectPathUtils.cleanTypeSafeAccessor("rotoscope.hysteria.dependencyProject"))
      .isEqualTo("rotoscope.hysteria")
    assertThat(GradleProjectPathUtils.cleanTypeSafeAccessor("rotoscope.hysteria.path"))
      .isEqualTo("rotoscope.hysteria")
  }
}
