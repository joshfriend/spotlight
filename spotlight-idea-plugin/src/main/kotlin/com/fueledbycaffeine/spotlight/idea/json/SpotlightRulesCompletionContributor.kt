package com.fueledbycaffeine.spotlight.idea.json

import com.fueledbycaffeine.spotlight.buildscript.SpotlightRulesList.Companion.SPOTLIGHT_RULES_LOCATION
import com.fueledbycaffeine.spotlight.idea.completion.ProjectCompletionUtils
import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext

/**
 * Provides path completion for project paths in spotlight-rules.json files.
 * Specifically targets the "includedProjects" array values.
 */
class SpotlightRulesCompletionContributor : CompletionContributor(), DumbAware {
  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(), SpotlightRulesCompletionProvider())
  }
}

private class SpotlightRulesCompletionProvider : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val file = parameters.originalFile
    val virtualFile = file.virtualFile ?: return
    
    // Only provide completions in spotlight-rules.json
    if (!virtualFile.path.endsWith(SPOTLIGHT_RULES_LOCATION)) return
    
    val position = parameters.position
    
    // Check if we're inside an "includedProjects" array
    if (!isInsideIncludedProjectsArray(position)) return
    
    val project = parameters.editor.project ?: return
    val spotlightService = project.spotlightService
    val allProjects = spotlightService.allProjects.value
    
    if (allProjects.isEmpty()) return
    
    // Get text typed so far (between quotes)
    val prefix = extractTypedPrefix(position)
    val prefixResult = if (prefix.isEmpty()) result else result.withPrefixMatcher(prefix)
    
    allProjects
      .sortedBy { it.path }
      .forEach { gradlePath ->
        val lookupElement = ProjectCompletionUtils.createProjectPathLookup(
          path = gradlePath.path,
          typeText = "Gradle project"
        )
        prefixResult.addElement(lookupElement)
      }
    
    result.stopHere()
  }
  
  /**
   * Checks if the current position is inside an "includedProjects" array.
   */
  private fun isInsideIncludedProjectsArray(element: PsiElement): Boolean {
    // Walk up the PSI tree looking for a JsonArray
    var current: PsiElement? = element
    while (current != null) {
      if (current is JsonArray) {
        // Check if the parent is a property named "includedProjects"
        val parent = current.parent
        if (parent is JsonProperty && parent.name == "includedProjects") {
          return true
        }
      }
      current = current.parent
    }
    return false
  }
  
  /**
   * Extracts the prefix that the user has typed inside the string literal.
   */
  private fun extractTypedPrefix(element: PsiElement): String {
    // Find the containing string literal
    var current: PsiElement? = element
    while (current != null && current !is JsonStringLiteral) {
      current = current.parent
    }
    
    if (current is JsonStringLiteral) {
      // Get the text without quotes and dummy identifier
      val text = current.value
      // Remove any IntelliJ completion dummy identifiers
      return text.replace(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, "")
        .replace(CompletionUtilCore.DUMMY_IDENTIFIER, "")
    }
    
    return ""
  }
}
