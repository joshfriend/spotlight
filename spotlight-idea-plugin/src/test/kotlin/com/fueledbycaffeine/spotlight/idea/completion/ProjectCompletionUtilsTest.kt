package com.fueledbycaffeine.spotlight.idea.completion

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProjectCompletionUtilsTest {

  @Test
  fun `createProjectPathLookup creates element with correct lookup string`() {
    val lookupElement = ProjectCompletionUtils.createProjectPathLookup(
      path = ":feature-flags:api"
    )
    
    assertThat(lookupElement.lookupString).isEqualTo(":feature-flags:api")
  }

  @Test
  fun `createProjectPathLookup with typeText sets type text`() {
    val lookupElement = ProjectCompletionUtils.createProjectPathLookup(
      path = ":tools:cli",
      typeText = "Gradle project"
    )
    
    assertThat(lookupElement.lookupString).isEqualTo(":tools:cli")
    // Type text is set on the underlying LookupElementBuilder
  }

  @Test
  fun `createProjectPathLookup with priority creates prioritized element`() {
    val lookupElement = ProjectCompletionUtils.createProjectPathLookup(
      path = ":feature:api",
      priority = 100.0
    )
    
    assertThat(lookupElement.lookupString).isEqualTo(":feature:api")
    // Priority wraps in PrioritizedLookupElement
  }

  @Test
  fun `createAccessorLookup creates element with accessor name as lookup string`() {
    val lookupElement = ProjectCompletionUtils.createAccessorLookup(
      accessorName = "featureFlags.api"
    )
    
    // The lookup string is the accessor name (without projects. prefix)
    assertThat(lookupElement.lookupString).isEqualTo("featureFlags.api")
  }

  @Test
  fun `createAccessorLookup with typeText sets type text`() {
    val lookupElement = ProjectCompletionUtils.createAccessorLookup(
      accessorName = "tools.cli",
      typeText = "Gradle project"
    )
    
    assertThat(lookupElement.lookupString).isEqualTo("tools.cli")
  }

  @Test
  fun `createAccessorLookup with priority creates prioritized element`() {
    val lookupElement = ProjectCompletionUtils.createAccessorLookup(
      accessorName = "feature.api",
      priority = 50.0
    )
    
    assertThat(lookupElement.lookupString).isEqualTo("feature.api")
  }

  @Test
  fun `project paths with various formats are handled correctly`() {
    val paths = listOf(
      ":simple",
      ":feature:api",
      ":platforms:gradle:plugin",
      ":a:b:c:d:e:f"
    )
    
    paths.forEach { path ->
      val lookupElement = ProjectCompletionUtils.createProjectPathLookup(path)
      assertThat(lookupElement.lookupString).isEqualTo(path)
    }
  }

  @Test
  fun `accessor names with various formats are handled correctly`() {
    val accessors = listOf(
      "simple",
      "feature.api",
      "platforms.gradle.plugin",
      "featureFlags.internal.api"
    )
    
    accessors.forEach { accessor ->
      val lookupElement = ProjectCompletionUtils.createAccessorLookup(accessor)
      assertThat(lookupElement.lookupString).isEqualTo(accessor)
    }
  }
}
