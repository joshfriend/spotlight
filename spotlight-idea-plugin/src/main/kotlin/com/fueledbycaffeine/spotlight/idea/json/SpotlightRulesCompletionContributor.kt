package com.fueledbycaffeine.spotlight.idea.json

import com.fueledbycaffeine.spotlight.buildscript.SpotlightRulesList.Companion.SPOTLIGHT_RULES_LOCATION
import com.fueledbycaffeine.spotlight.idea.completion.FuzzyMatchingUtils
import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
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
    extend(
      CompletionType.BASIC,
      PlatformPatterns.psiElement().inside(JsonStringLiteral::class.java),
      SpotlightRulesCompletionProvider()
    )
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
    
    // Calculate the prefix from the current string literal content
    val stringLiteral = findContainingStringLiteral(position) ?: return
    val content = stringLiteral.value
    val offsetInLiteral = parameters.offset - stringLiteral.textRange.startOffset - 1
    val prefix = if (offsetInLiteral >= 0) {
      content.substring(0, minOf(offsetInLiteral, content.length))
    } else {
      result.prefixMatcher.prefix
    }

    // Store the content start offset for the insert handler
    val contentStartOffset = stringLiteral.textRange.startOffset + 1
    
    val fuzzyResultSet = result.withPrefixMatcher(FuzzyPrefixMatcher(prefix))
    
    allProjects.forEach { gradlePath ->
      val priority = FuzzyMatchingUtils.calculatePathMatchPriority(gradlePath.path, prefix)
      if (priority < 100) {
        val lookup = LookupElementBuilder.create(gradlePath.path)
          .withIcon(AllIcons.Nodes.Module)
          .withTypeText("Gradle project")
          .withInsertHandler(JsonProjectInsertHandler(contentStartOffset))
        
        fuzzyResultSet.addElement(
          PrioritizedLookupElement.withPriority(lookup, (100 - priority).toDouble())
        )
      }
    }
    
    result.stopHere()
  }

  private fun findContainingStringLiteral(element: PsiElement): JsonStringLiteral? {
    var current: PsiElement? = element
    while (current != null && current !is JsonStringLiteral) {
      current = current.parent
    }
    return current as? JsonStringLiteral
  }
  
  /**
   * Checks if the current position is inside an "includedProjects" array.
   */
  private fun isInsideIncludedProjectsArray(element: PsiElement): Boolean {
    var current: PsiElement? = element
    while (current != null) {
      if (current is JsonArray) {
        val parent = current.parent
        if (parent is JsonProperty && parent.name == "includedProjects") {
          return true
        }
      }
      current = current.parent
    }
    return false
  }
}

/**
 * Custom insert handler to ensure the entire string content is replaced,
 * preventing double colons (::) or quote issues.
 */
private class JsonProjectInsertHandler(private val contentStartOffset: Int) : InsertHandler<LookupElement> {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    val editor = context.editor
    val document = editor.document
    
    // After IntelliJ's default insertion, the document has been modified.
    // We need to replace from contentStartOffset to the closing quote with just our path.
    
    // Find the closing quote after the current position
    val text = document.charsSequence
    var closingQuoteOffset = context.tailOffset
    while (closingQuoteOffset < text.length && text[closingQuoteOffset] != '"') {
      closingQuoteOffset++
    }
    
    // Replace everything from content start to the closing quote
    document.replaceString(contentStartOffset, closingQuoteOffset, item.lookupString)
    
    // Move caret to end of inserted path
    editor.caretModel.moveToOffset(contentStartOffset + item.lookupString.length)
  }
}

/**
 * A PrefixMatcher that uses our fuzzy matching logic to determine if a project matches what's typed.
 */
private class FuzzyPrefixMatcher(prefix: String) : PrefixMatcher(prefix) {
  override fun prefixMatches(name: String): Boolean {
    return FuzzyMatchingUtils.calculatePathMatchPriority(name, prefix) < 100
  }

  override fun cloneWithPrefix(prefix: String): PrefixMatcher = FuzzyPrefixMatcher(prefix)
}
