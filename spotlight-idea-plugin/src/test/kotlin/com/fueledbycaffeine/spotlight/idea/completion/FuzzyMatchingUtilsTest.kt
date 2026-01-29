package com.fueledbycaffeine.spotlight.idea.completion

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FuzzyMatchingUtilsTest {

  // ===========================================
  // Path Match Priority Tests
  // ===========================================

  @Test
  fun `exact path match returns priority 0`() {
    val priority = FuzzyMatchingUtils.calculatePathMatchPriority(
      ":feature-flags:api",
      ":feature-flags:api"
    )
    assertThat(priority).isEqualTo(0)
  }

  @Test
  fun `path starts with prefix returns priority 1`() {
    val priority = FuzzyMatchingUtils.calculatePathMatchPriority(
      ":feature-flags:api",
      ":feature-f"
    )
    assertThat(priority).isEqualTo(1)
  }

  @Test
  fun `first segment starts with prefix returns priority 2`() {
    val priority = FuzzyMatchingUtils.calculatePathMatchPriority(
      ":feature-flags:api",
      "feature"
    )
    assertThat(priority).isEqualTo(2)
  }

  @Test
  fun `fuzzy match with strict acronym returns priority 3`() {
    // "ff" should match "feature-flags" via strict acronym (f->feature, f->flags)
    val priority = FuzzyMatchingUtils.calculatePathMatchPriority(
      ":feature-flags:api",
      ":ffapi"
    )
    assertThat(priority).isEqualTo(3)
  }

  @Test
  fun `fuzzy match works without leading colon in prefix`() {
    // User can type "ffapi" without the leading colon and still match ":feature-flags:api"
    val priority = FuzzyMatchingUtils.calculatePathMatchPriority(
      ":feature-flags:api",
      "ffapi"
    )
    assertThat(priority).isEqualTo(3)
  }

  @Test
  fun `fuzzy match with prefix-per-word returns priority 3`() {
    // "caos" should match "cash-os" via prefix-per-word (ca->cash, os->os)
    val priority = FuzzyMatchingUtils.calculatePathMatchPriority(
      ":cash-os:service",
      ":caos"
    )
    assertThat(priority).isEqualTo(3)
  }

  @Test
  fun `later segment starting with prefix returns priority 3 via fuzzy match`() {
    // When a non-first segment starts with the prefix, fuzzyMatchesPath handles it
    // This is effectively the same as "any segment starts with" but with priority 3
    val priority = FuzzyMatchingUtils.calculatePathMatchPriority(
      ":backend:service",
      "serv"
    )
    // Matches via fuzzy (segment "service" starts with "serv")
    assertThat(priority).isEqualTo(3)
  }

  @Test
  fun `any segment contains prefix returns priority 5`() {
    val priority = FuzzyMatchingUtils.calculatePathMatchPriority(
      ":feature-flags:api",
      "flag"
    )
    assertThat(priority).isEqualTo(5)
  }

  @Test
  fun `full path contains prefix returns priority 6`() {
    // Use a prefix that spans segment boundaries (not contained in any single segment)
    val priority = FuzzyMatchingUtils.calculatePathMatchPriority(
      ":feature-flags:api",
      "gs:a"
    )
    assertThat(priority).isEqualTo(6)
  }

  @Test
  fun `no match returns priority 100`() {
    val priority = FuzzyMatchingUtils.calculatePathMatchPriority(
      ":feature-flags:api",
      "xyz"
    )
    assertThat(priority).isEqualTo(100)
  }

  @Test
  fun `empty prefix returns priority 100`() {
    val priority = FuzzyMatchingUtils.calculatePathMatchPriority(
      ":feature-flags:api",
      ""
    )
    assertThat(priority).isEqualTo(100)
  }

  // ===========================================
  // Accessor Match Priority Tests
  // ===========================================

  @Test
  fun `exact accessor match returns priority 0`() {
    val priority = FuzzyMatchingUtils.calculateAccessorMatchPriority(
      "featureFlags.api",
      "featureFlags.api"
    )
    assertThat(priority).isEqualTo(0)
  }

  @Test
  fun `accessor starts with prefix returns priority 1`() {
    val priority = FuzzyMatchingUtils.calculateAccessorMatchPriority(
      "featureFlags.api",
      "featureF"
    )
    assertThat(priority).isEqualTo(1)
  }

  @Test
  fun `fuzzy match with camelCase acronym returns priority 3`() {
    // "ffa" should match "featureFlags.api" via camelCase acronym
    val priority = FuzzyMatchingUtils.calculateAccessorMatchPriority(
      "featureFlags.api",
      "ffa"
    )
    assertThat(priority).isEqualTo(3)
  }

  @Test
  fun `fuzzy match with camelCase prefix-per-word returns priority 3`() {
    // "caos" should match "cashOs" via prefix-per-word (ca->cash, os->Os)
    val priority = FuzzyMatchingUtils.calculateAccessorMatchPriority(
      "cashOs.service",
      "caos"
    )
    assertThat(priority).isEqualTo(3)
  }

  // ===========================================
  // Fuzzy Path Matching Tests
  // ===========================================

  @Test
  fun `fuzzyMatchesPath matches strict acronym across segments`() {
    // "ff" matches "feature-flags" (f->feature, f->flags)
    val matches = FuzzyMatchingUtils.fuzzyMatchesPath(
      "ff",
      listOf("feature-flags")
    )
    assertThat(matches).isTrue()
  }

  @Test
  fun `fuzzyMatchesPath matches acronym across multiple segments`() {
    // "ffapi" matches segments: "ff" from "feature-flags", "api" from "api"
    val matches = FuzzyMatchingUtils.fuzzyMatchesPath(
      "ffapi",
      listOf("feature-flags", "api")
    )
    assertThat(matches).isTrue()
  }

  @Test
  fun `fuzzyMatchesPath matches prefix-per-word in single segment`() {
    // "caos" matches "cash-os" (ca->cash, os->os)
    val matches = FuzzyMatchingUtils.fuzzyMatchesPath(
      "caos",
      listOf("cash-os")
    )
    assertThat(matches).isTrue()
  }

  @Test
  fun `fuzzyMatchesPath does not match single word segment with multi-char prefix`() {
    // "ff" should NOT match "formatter" (only one word, can't acronym match)
    val matches = FuzzyMatchingUtils.fuzzyMatchesPath(
      "ff",
      listOf("formatter")
    )
    assertThat(matches).isFalse()
  }

  @Test
  fun `fuzzyMatchesPath does not match when prefix exceeds available characters`() {
    val matches = FuzzyMatchingUtils.fuzzyMatchesPath(
      "ffapiservice",
      listOf("feature-flags", "api")
    )
    assertThat(matches).isFalse()
  }

  @Test
  fun `fuzzyMatchesPath handles multiple kebab segments`() {
    // "ffhl" matches "feature-flags" + "holy-land"
    val matches = FuzzyMatchingUtils.fuzzyMatchesPath(
      "ffhl",
      listOf("feature-flags", "holy-land")
    )
    assertThat(matches).isTrue()
  }

  @Test
  fun `fuzzyMatchesPath skips non-matching segments`() {
    // "api" should match even if first segment doesn't match
    val matches = FuzzyMatchingUtils.fuzzyMatchesPath(
      "api",
      listOf("feature-flags", "api")
    )
    assertThat(matches).isTrue()
  }

  // ===========================================
  // Fuzzy Accessor Matching Tests  
  // ===========================================

  @Test
  fun `fuzzyMatchesAccessor matches camelCase acronym`() {
    // "ff" matches "featureFlags" (f->feature, f->Flags)
    val matches = FuzzyMatchingUtils.fuzzyMatchesAccessor(
      "ff",
      listOf("featureFlags")
    )
    assertThat(matches).isTrue()
  }

  @Test
  fun `fuzzyMatchesAccessor matches prefix-per-word in camelCase`() {
    // "caos" matches "cashOs" (ca->cash, os->Os)
    val matches = FuzzyMatchingUtils.fuzzyMatchesAccessor(
      "caos",
      listOf("cashOs")
    )
    assertThat(matches).isTrue()
  }

  @Test
  fun `fuzzyMatchesAccessor matches across multiple segments`() {
    // "ffapi" matches ["featureFlags", "api"]
    val matches = FuzzyMatchingUtils.fuzzyMatchesAccessor(
      "ffapi",
      listOf("featureFlags", "api")
    )
    assertThat(matches).isTrue()
  }

  @Test
  fun `fuzzyMatchesAccessor prioritizes exact segment match`() {
    // "api" should match the "api" segment exactly
    val matches = FuzzyMatchingUtils.fuzzyMatchesAccessor(
      "api",
      listOf("featureFlags", "api")
    )
    assertThat(matches).isTrue()
  }

  // ===========================================
  // Sorting/Priority Comparison Tests
  // ===========================================

  @Test
  fun `feature-flags-api ranks higher than formatter-api for ffapi search`() {
    val featureFlagsApiPriority = FuzzyMatchingUtils.calculatePathMatchPriority(
      ":feature-flags:api",
      ":ffapi"
    )
    val formatterApiPriority = FuzzyMatchingUtils.calculatePathMatchPriority(
      ":bitcoin:formatter:api",
      ":ffapi"
    )
    
    // feature-flags:api should have lower (better) priority
    assertThat(featureFlagsApiPriority).isLessThan(formatterApiPriority)
  }

  @Test
  fun `cash-os ranks higher than card-onboarding for caos search`() {
    val cashOsPriority = FuzzyMatchingUtils.calculatePathMatchPriority(
      ":cash-os",
      ":caos"
    )
    val cardOnboardingPriority = FuzzyMatchingUtils.calculatePathMatchPriority(
      ":card-onboarding",
      ":caos"
    )
    
    // cash-os should have lower (better) priority
    assertThat(cashOsPriority).isLessThan(cardOnboardingPriority)
  }

  @Test
  fun `exact match ranks higher than fuzzy match`() {
    val exactPriority = FuzzyMatchingUtils.calculatePathMatchPriority(
      ":feature-flags:api",
      ":feature-flags:api"
    )
    val fuzzyPriority = FuzzyMatchingUtils.calculatePathMatchPriority(
      ":feature-flags:api",
      ":ffapi"
    )
    
    assertThat(exactPriority).isLessThan(fuzzyPriority)
  }

  @Test
  fun `prefix match ranks higher than fuzzy match`() {
    val prefixPriority = FuzzyMatchingUtils.calculatePathMatchPriority(
      ":feature-flags:api",
      ":feature"
    )
    val fuzzyPriority = FuzzyMatchingUtils.calculatePathMatchPriority(
      ":feature-flags:api",
      ":ffapi"
    )
    
    assertThat(prefixPriority).isLessThan(fuzzyPriority)
  }

  // ===========================================
  // Edge Cases
  // ===========================================

  @Test
  fun `empty segments list returns no match`() {
    val matches = FuzzyMatchingUtils.fuzzyMatchesPath("ff", emptyList())
    assertThat(matches).isFalse()
  }

  @Test
  fun `empty prefix returns no match for path`() {
    val matches = FuzzyMatchingUtils.fuzzyMatchesPath("", listOf("feature-flags"))
    assertThat(matches).isFalse()
  }

  @Test
  fun `single character prefix with single word segment returns match via start`() {
    // Single char should match start of segment
    val priority = FuzzyMatchingUtils.calculatePathMatchPriority(
      ":feature-flags",
      "f"
    )
    // Should match via "first segment starts with prefix"
    assertThat(priority).isEqualTo(2)
  }

  @Test
  fun `handles paths with many segments`() {
    val priority = FuzzyMatchingUtils.calculatePathMatchPriority(
      ":account:backend:api:service:impl",
      ":abasi"
    )
    // Should fuzzy match: a->account, b->backend, a->api, s->service, i->impl
    assertThat(priority).isEqualTo(3)
  }

  @Test
  fun `handles deeply nested kebab segments`() {
    val matches = FuzzyMatchingUtils.fuzzyMatchesPath(
      "socs",
      listOf("some-other-cool-stuff")
    )
    // s->some, o->other, c->cool, s->stuff
    assertThat(matches).isTrue()
  }

  @Test
  fun `prefix-per-word works with longer prefixes`() {
    // "feafl" should match "feature-flags" (fea->feature, fl->flags)
    val matches = FuzzyMatchingUtils.fuzzyMatchesPath(
      "feafl",
      listOf("feature-flags")
    )
    assertThat(matches).isTrue()
  }

  @Test
  fun `prefix-per-word requires matching at least 2 words`() {
    // "fea" matching just "feature" in "feature-flags" shouldn't count as acronym
    // It should fall back to start-of-segment matching instead
    val priority = FuzzyMatchingUtils.calculatePathMatchPriority(
      ":feature-flags",
      "fea"
    )
    // Should match via "first segment starts with prefix" (priority 2), not fuzzy (priority 3)
    assertThat(priority).isEqualTo(2)
  }
}
