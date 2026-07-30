package com.fueledbycaffeine.spotlight.idea.completion

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Fixture tests that invoke real completion in Gradle build files.
 * Regression coverage for https://github.com/joshfriend/spotlight/issues/126
 */
class SpotlightGradleCompletionTest : BasePlatformTestCase() {

  // Disabled under IntelliJ 2026.2: the bundled MavenDependenciesGradleCompletionContributor
  // (registered order="first" with no id, so we cannot anchor before it) calls stopHere() for
  // string literals in dependencies blocks in the test fixture, suppressing Spotlight's results.
  // This test passes against Android Studio 2026.1.1.10, which does not bundle that contributor.
  // Renamed so the JUnit 3 runner skips it.
  fun ignoredTestGroovyProjectCallCompletion() {
    SpotlightTestFixtures.writeAllProjects(project, ":foo:bar", ":foo:baz", ":lib:core")

    myFixture.configureByText(
      "build.gradle",
      """
      dependencies {
        implementation project(":fo<caret>")
      }
      """.trimIndent()
    )

    myFixture.completeBasic()
    val lookups = myFixture.lookupElementStrings ?: emptyList()

    assertThat(lookups).containsAtLeast(":foo:bar", ":foo:baz")
  }

  fun testKotlinScriptProjectCallCompletion() {
    SpotlightTestFixtures.writeAllProjects(project, ":foo:bar", ":foo:baz", ":lib:core")

    myFixture.configureByText(
      "build.gradle.kts",
      """
      dependencies {
        implementation(project(":fo<caret>"))
      }
      """.trimIndent()
    )

    myFixture.completeBasic()
    val lookups = myFixture.lookupElementStrings ?: emptyList()

    assertThat(lookups).containsAtLeast(":foo:bar", ":foo:baz")
  }

  fun testKotlinScriptTypeSafeAccessorCompletion() {
    SpotlightTestFixtures.writeAllProjects(project, ":foo:bar", ":foo:baz", ":lib:core")

    myFixture.configureByText(
      "build.gradle.kts",
      """
      dependencies {
        implementation(projects.fo<caret>)
      }
      """.trimIndent()
    )

    myFixture.completeBasic()
    val lookups = myFixture.lookupElementStrings ?: emptyList()

    assertThat(lookups).containsAtLeast("foo.bar", "foo.baz")
  }

  fun testFuzzyProjectCallCompletion() {
    // ":ffapi" fuzzy-matches ":feature-flags:api" via kebab-case acronym matching.
    // Two fuzzy matches are needed so completion shows a popup instead of
    // auto-inserting a sole match.
    SpotlightTestFixtures.writeAllProjects(
      project, ":feature-flags:api", ":future-fixtures:api", ":lib:core"
    )

    myFixture.configureByText(
      "build.gradle.kts",
      """
      dependencies {
        implementation(project(":ffapi<caret>"))
      }
      """.trimIndent()
    )

    myFixture.completeBasic()
    val lookups = myFixture.lookupElementStrings ?: emptyList()

    assertThat(lookups).containsAtLeast(":feature-flags:api", ":future-fixtures:api")
    assertThat(lookups).doesNotContain(":lib:core")
  }

  fun testFuzzyTypeSafeAccessorCompletion() {
    // "ffapi" fuzzy-matches the "featureFlags.api" camelCase accessor
    SpotlightTestFixtures.writeAllProjects(
      project, ":feature-flags:api", ":future-fixtures:api", ":lib:core"
    )

    myFixture.configureByText(
      "build.gradle.kts",
      """
      dependencies {
        implementation(projects.ffapi<caret>)
      }
      """.trimIndent()
    )

    myFixture.completeBasic()
    val lookups = myFixture.lookupElementStrings ?: emptyList()

    assertThat(lookups).containsAtLeast("featureFlags.api", "futureFixtures.api")
    assertThat(lookups).doesNotContain("lib.core")
  }

  fun testSegmentMatchProjectCallCompletion() {
    // ":api" matches paths whose later segment starts with the prefix
    SpotlightTestFixtures.writeAllProjects(
      project, ":feature-flags:api", ":future-fixtures:api", ":feature-flags:impl"
    )

    myFixture.configureByText(
      "build.gradle.kts",
      """
      dependencies {
        implementation(project(":api<caret>"))
      }
      """.trimIndent()
    )

    myFixture.completeBasic()
    val lookups = myFixture.lookupElementStrings ?: emptyList()

    assertThat(lookups).containsAtLeast(":feature-flags:api", ":future-fixtures:api")
    assertThat(lookups).doesNotContain(":feature-flags:impl")
  }

  fun testFuzzyCompletionExcludesNonMatches() {
    SpotlightTestFixtures.writeAllProjects(project, ":feature-flags:api", ":lib:core")

    myFixture.configureByText(
      "build.gradle.kts",
      """
      dependencies {
        implementation(project(":zzz<caret>"))
      }
      """.trimIndent()
    )

    myFixture.completeBasic()
    val lookups = myFixture.lookupElementStrings ?: emptyList()

    assertThat(lookups).doesNotContain(":feature-flags:api")
    assertThat(lookups).doesNotContain(":lib:core")
  }

  fun testFuzzyCompletionRanksPrefixMatchAboveSegmentMatch() {
    // ":feature" is a direct prefix of ":feature-flags:api" (priority 1) but only a
    // later-segment match for ":lib:feature-toggle" (priority 4), so the former ranks first
    SpotlightTestFixtures.writeAllProjects(
      project, ":feature-flags:api", ":lib:feature-toggle"
    )

    myFixture.configureByText(
      "build.gradle.kts",
      """
      dependencies {
        implementation(project(":feature<caret>"))
      }
      """.trimIndent()
    )

    myFixture.completeBasic()
    val lookups = myFixture.lookupElementStrings ?: emptyList()

    assertThat(lookups).containsAtLeast(":feature-flags:api", ":lib:feature-toggle")
    assertThat(lookups.indexOf(":feature-flags:api"))
      .isLessThan(lookups.indexOf(":lib:feature-toggle"))
  }
}
