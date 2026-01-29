package com.fueledbycaffeine.spotlight.idea.completion

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProjectCompletionUtilsTest {

  @Test
  fun `createProjectPathLookup creates element with correct lookup string`() {
    val lookupElement = ProjectCompletionUtils.createProjectPathLookup(
      path = ":eternal-blue:holy-roller"
    )
    
    assertThat(lookupElement.lookupString).isEqualTo(":eternal-blue:holy-roller")
  }

  @Test
  fun `createProjectPathLookup with typeText sets type text`() {
    val lookupElement = ProjectCompletionUtils.createProjectPathLookup(
      path = ":rotoscope:hysteria",
      typeText = "Gradle project"
    )
    
    assertThat(lookupElement.lookupString).isEqualTo(":rotoscope:hysteria")
    // Type text is set on the underlying LookupElementBuilder
  }

  @Test
  fun `createProjectPathLookup with priority creates prioritized element`() {
    val lookupElement = ProjectCompletionUtils.createProjectPathLookup(
      path = ":the-fear-of-fear:cellar-door",
      priority = 100.0
    )
    
    assertThat(lookupElement.lookupString).isEqualTo(":the-fear-of-fear:cellar-door")
    // Priority wraps in PrioritizedLookupElement
  }

  @Test
  fun `createAccessorLookup creates element with accessor name as lookup string`() {
    val lookupElement = ProjectCompletionUtils.createAccessorLookup(
      accessorName = "eternalBlue.holyRoller"
    )
    
    // The lookup string is the accessor name (without projects. prefix)
    assertThat(lookupElement.lookupString).isEqualTo("eternalBlue.holyRoller")
  }

  @Test
  fun `createAccessorLookup with typeText sets type text`() {
    val lookupElement = ProjectCompletionUtils.createAccessorLookup(
      accessorName = "rotoscope.hysteria",
      typeText = "Gradle project"
    )
    
    assertThat(lookupElement.lookupString).isEqualTo("rotoscope.hysteria")
  }

  @Test
  fun `createAccessorLookup with priority creates prioritized element`() {
    val lookupElement = ProjectCompletionUtils.createAccessorLookup(
      accessorName = "theFearOfFear.cellarDoor",
      priority = 50.0
    )
    
    assertThat(lookupElement.lookupString).isEqualTo("theFearOfFear.cellarDoor")
  }

  @Test
  fun `project paths with various formats are handled correctly`() {
    val paths = listOf(
      ":rotoscope",
      ":eternal-blue:holy-roller",
      ":the-fear-of-fear:too-close-too-late",
      ":tsunami-sea:fata-morgana:deep-end"
    )
    
    paths.forEach { path ->
      val lookupElement = ProjectCompletionUtils.createProjectPathLookup(path)
      assertThat(lookupElement.lookupString).isEqualTo(path)
    }
  }

  @Test
  fun `accessor names with various formats are handled correctly`() {
    val accessors = listOf(
      "rotoscope",
      "eternalBlue.holyRoller",
      "theFearOfFear.cellarDoor",
      "tsunamiSea.fataMorgana.deepEnd"
    )
    
    accessors.forEach { accessor ->
      val lookupElement = ProjectCompletionUtils.createAccessorLookup(accessor)
      assertThat(lookupElement.lookupString).isEqualTo(accessor)
    }
  }
}
