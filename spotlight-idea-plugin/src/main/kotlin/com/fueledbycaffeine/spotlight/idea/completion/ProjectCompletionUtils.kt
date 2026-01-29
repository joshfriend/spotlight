package com.fueledbycaffeine.spotlight.idea.completion

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons

/**
 * Shared utilities for project completion across different file types.
 */
object ProjectCompletionUtils {
  
  /**
   * Creates a prioritized lookup element for a Gradle project path.
   * 
   * @param path The project path (e.g., ":feature-flags:api")
   * @param priority Higher values appear first in completion list
   * @param typeText Optional text to show on the right (e.g., "Gradle project")
   * @param bold Whether to show in bold
   */
  fun createProjectPathLookup(
    path: String,
    priority: Double = 0.0,
    typeText: String? = null,
    bold: Boolean = true
  ): LookupElement {
    val lookupElement = LookupElementBuilder.create(path)
      .withIcon(AllIcons.Nodes.Module)
      .withBoldness(bold)
      .let { if (typeText != null) it.withTypeText(typeText) else it }
    
    return if (priority != 0.0) {
      PrioritizedLookupElement.withPriority(lookupElement, priority)
    } else {
      lookupElement
    }
  }
  
  /**
   * Creates a lookup element for a type-safe project accessor.
   * 
   * @param accessorName The accessor name without "projects." prefix (e.g., "featureFlags.api")
   * @param priority Higher values appear first in completion list
   * @param typeText Optional text to show on the right
   */
  fun createAccessorLookup(
    accessorName: String,
    priority: Double = 0.0,
    typeText: String? = null
  ): LookupElement {
    val fullAccessor = "projects.$accessorName"
    
    val lookupElement = LookupElementBuilder.create(accessorName)
      .withPresentableText(fullAccessor)
      .withIcon(AllIcons.Nodes.Module)
      .let { if (typeText != null) it.withTypeText(typeText) else it }
    
    return if (priority != 0.0) {
      PrioritizedLookupElement.withPriority(lookupElement, priority)
    } else {
      lookupElement
    }
  }
}
