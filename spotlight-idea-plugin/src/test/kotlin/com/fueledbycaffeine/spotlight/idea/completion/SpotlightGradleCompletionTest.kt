package com.fueledbycaffeine.spotlight.idea.completion

import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Fixture tests that invoke real completion in Gradle build files.
 * Regression coverage for https://github.com/joshfriend/spotlight/issues/126
 */
class SpotlightGradleCompletionTest : BasePlatformTestCase() {

  // IntelliJ 2026.2 bundles MavenDependenciesGradleCompletionContributor (gradle-maven bridge
  // module), which is registered order="first" with no id and calls stopHere() for string
  // literals in dependencies blocks, suppressing Spotlight's results. Ties between two
  // order="first" contributors go to the earlier-registered (bundled) one, so Spotlight cannot
  // win via registration order. Mask it here so we can still cover Spotlight's own Groovy
  // completion logic; this matches IDEs without the Maven plugin (e.g. Android Studio).
  fun testGroovyProjectCallCompletion() {
    maskMavenDependenciesContributor()
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

  private fun maskMavenDependenciesContributor() {
    val epName = ExtensionPointName<CompletionContributorEP>("com.intellij.completion.contributor")
    val remaining = epName.extensionList.filterNot {
      it.implementationClass == MAVEN_GRADLE_CONTRIBUTOR
    }
    ExtensionTestUtil.maskExtensions(epName, remaining, testRootDisposable)
  }

  private companion object {
    const val MAVEN_GRADLE_CONTRIBUTOR =
      "org.jetbrains.plugins.gradle.integrations.maven.codeInsight.completion.MavenDependenciesGradleCompletionContributor"
  }
}
