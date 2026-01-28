package com.fueledbycaffeine.spotlight.idea.gradle

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GradleProjectPathUtilsTest {

  @Test
  fun `calculateFuzzyScore returns highest score for exact match`() {
    val score = GradleProjectPathUtils.calculateFuzzyScore("feature-flags:api", "feature-flags:api")
    assertThat(score).isEqualTo(10000)
  }

  @Test
  fun `calculateFuzzyScore returns high score for prefix match`() {
    val score = GradleProjectPathUtils.calculateFuzzyScore("feature-flags:api", "feature")
    assertThat(score).isGreaterThan(800)
  }

  @Test
  fun `calculateFuzzyScore returns good score for contains match`() {
    val score = GradleProjectPathUtils.calculateFuzzyScore("feature-flags:api", "flags")
    assertThat(score).isGreaterThan(700)
  }

  @Test
  fun `calculateFuzzyScore matches acronyms across segments`() {
    // "ff" should match "feature-flags" (f=feature, f=flags)
    val ffScore = GradleProjectPathUtils.calculateFuzzyScore("feature-flags", "ff")
    assertThat(ffScore).isGreaterThan(0)
    
    // "ffapi" should match "feature-flags:api"
    val ffapiScore = GradleProjectPathUtils.calculateFuzzyScore("feature-flags:api", "ffapi")
    assertThat(ffapiScore).isGreaterThan(0)
  }

  @Test
  fun `calculateFuzzyScore prefers better acronym matches`() {
    // "ffapi" should score higher for "feature-flags:api" than for "family:activity:backend:api"
    val featureFlagsScore = GradleProjectPathUtils.calculateFuzzyScore("feature-flags:api", "ffapi")
    val familyScore = GradleProjectPathUtils.calculateFuzzyScore("family:activity:backend:api", "ffapi")
    
    assertThat(featureFlagsScore).isGreaterThan(familyScore)
  }

  @Test
  fun `calculateFuzzyScore prefers shorter paths with same acronym match`() {
    // "ffapi" matches both, but feature-flags:api is shorter (fewer segments)
    val featureFlagsScore = GradleProjectPathUtils.calculateFuzzyScore("feature-flags:api", "ffapi")
    val familyHubScore = GradleProjectPathUtils.calculateFuzzyScore("family:family-hub:backend:api", "ffapi")
    
    assertThat(featureFlagsScore).isGreaterThan(familyHubScore)
  }

  @Test
  fun `calculateFuzzyScore penalizes skipped segments`() {
    // "fc" with "feature:central" has no skipped segments (f→feature, c→central)
    // "fc" with "feature:backend:central" skips "backend" (f→feature, skip backend, c→central)
    val directScore = GradleProjectPathUtils.calculateFuzzyScore("feature:central", "fc")
    val skippedScore = GradleProjectPathUtils.calculateFuzzyScore("feature:backend:central", "fc")
    
    assertThat(directScore).isGreaterThan(skippedScore)
  }

  @Test
  fun `calculateFuzzyScore handles camelCase segments`() {
    // "ff" should match "featureFlags" (f=feature, f=Flags)
    val score = GradleProjectPathUtils.calculateFuzzyScore("featureFlags", "ff")
    assertThat(score).isGreaterThan(0)
  }

  @Test
  fun `calculateFuzzyScore returns zero for no match`() {
    val score = GradleProjectPathUtils.calculateFuzzyScore("feature-flags:api", "xyz")
    assertThat(score).isEqualTo(0)
  }

  @Test
  fun `calculateFuzzyScore handles empty prefix`() {
    val score = GradleProjectPathUtils.calculateFuzzyScore("feature-flags:api", "")
    assertThat(score).isGreaterThan(0) // Should match anything
  }

  @Test
  fun `calculateFuzzyScore handles subsequence matching`() {
    // "fapi" should match "feature-flags:api" as subsequence
    val score = GradleProjectPathUtils.calculateFuzzyScore("feature-flags:api", "fapi")
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
    assertThat(GradleProjectPathUtils.cleanTypeSafeAccessor("projects.feature.api"))
      .isEqualTo("feature.api")
    assertThat(GradleProjectPathUtils.cleanTypeSafeAccessor("feature.api.dependencyProject"))
      .isEqualTo("feature.api")
    assertThat(GradleProjectPathUtils.cleanTypeSafeAccessor("feature.api.path"))
      .isEqualTo("feature.api")
  }
}
