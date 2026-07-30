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
}
