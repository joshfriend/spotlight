package com.fueledbycaffeine.spotlight.idea.search

import com.fueledbycaffeine.spotlight.idea.completion.SpotlightTestFixtures
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for the Gradle project Search Everywhere contributor.
 */
class GradleProjectSearchContributorTest : BasePlatformTestCase() {

  private fun search(pattern: String): List<FoundItemDescriptor<GradleProjectItem>> {
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(project))
    val contributor = GradleProjectSearchContributor(event)
    val results = mutableListOf<FoundItemDescriptor<GradleProjectItem>>()
    try {
      contributor.fetchWeightedElements(pattern, EmptyProgressIndicator()) { descriptor ->
        results.add(descriptor)
        true
      }
    } finally {
      contributor.dispose()
    }
    return results
  }

  private fun setUpProjects(vararg paths: String) {
    SpotlightTestFixtures.createProjectsOnDisk(project, *paths)
    SpotlightTestFixtures.writeAllProjects(project, *paths)
  }

  fun testExactPathMatchHasHighestWeight() {
    setUpProjects(":feature-flags:api", ":feature-flags:impl", ":other:thing")

    val results = search(":feature-flags:api")

    assertThat(results.map { it.item.gradlePath.path }).contains(":feature-flags:api")
    val exact = results.single { it.item.gradlePath.path == ":feature-flags:api" }
    val others = results.filter { it.item.gradlePath.path != ":feature-flags:api" }
    others.forEach { assertThat(exact.weight).isGreaterThan(it.weight) }
  }

  fun testFuzzyAbbreviationMatchesSegments() {
    setUpProjects(":feature-flags:api", ":other:thing")

    val results = search(":ffapi")

    assertThat(results.map { it.item.gradlePath.path }).contains(":feature-flags:api")
  }

  fun testBlankPatternReturnsNothing() {
    setUpProjects(":feature-flags:api")

    assertThat(search("")).isEmpty()
    assertThat(search("  ")).isEmpty()
  }

  fun testUnmatchedPatternReturnsNothing() {
    setUpProjects(":feature-flags:api")

    assertThat(search(":zzz:qqq")).isEmpty()
  }
}
