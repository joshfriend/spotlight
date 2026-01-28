package com.fueledbycaffeine.spotlight.idea.gradle

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.idea.completion.FuzzyMatchingUtils
import com.fueledbycaffeine.spotlight.idea.completion.ProjectCompletionUtils
import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * Provides completion for project paths in build.gradle files.
 * Supports both project(":path") and type-safe accessors (projects.path.to.project).
 */
class BuildGradleProjectCompletionContributor : CompletionContributor() {
  init {
    extend(
      CompletionType.BASIC,
      PlatformPatterns.psiElement(),
      BuildGradleProjectCompletionProvider()
    )
  }
}

private class BuildGradleProjectCompletionProvider : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val file = parameters.originalFile
    val virtualFile = file.virtualFile ?: return
    
    // Only provide completions in Gradle build files
    if (!GradleProjectPathUtils.isGradleBuildFile(virtualFile.name)) return
    
    val project = parameters.editor.project ?: return
    val spotlightService = project.spotlightService
    val allProjects = spotlightService.allProjects.value
    
    if (allProjects.isEmpty()) return
    
    val document = parameters.editor.document
    val offset = parameters.offset
    
    // Get current line to check context
    val lineNumber = document.getLineNumber(offset)
    val lineStartOffset = document.getLineStartOffset(lineNumber)
    val lineEndOffset = document.getLineEndOffset(lineNumber)
    val currentLine = document.getText(
      TextRange(lineStartOffset, lineEndOffset)
    )
    val textBeforeCaret = document.getText(
      TextRange(lineStartOffset, offset)
    )
    
    // Extract what the user has typed so far for sorting purposes
    val typedPrefix = extractTypedPrefix(textBeforeCaret)
    
    // Determine context based on current line
    val inProjectCall = isInsideProjectCall(textBeforeCaret)
    val inTypeSafeContext = isTypingTypeSafeAccessor(textBeforeCaret)
    
    // Use a custom prefix matcher that doesn't filter results
    val resultWithCustomMatcher = result.withPrefixMatcher(object : PrefixMatcher(typedPrefix) {
      override fun prefixMatches(name: String): Boolean = true
      override fun cloneWithPrefix(prefix: String): PrefixMatcher = this
      override fun isStartMatch(name: String): Boolean = true
    })
    
    // Only provide completions in the correct context to avoid confusion
    if (inProjectCall) {
      addProjectCallCompletions(allProjects, resultWithCustomMatcher, typedPrefix)
    } else if (inTypeSafeContext) {
      addTypeSafeAccessorCompletions(allProjects, resultWithCustomMatcher, typedPrefix)
    }
  }
  
  private fun extractTypedPrefix(textBeforeCaret: String): String {
    // Remove IntelliJ's dummy identifier if present
    val cleanText = textBeforeCaret.replace(Regex("""IntellijIdeaRulezzz\w*"""), "")
    
    // For project(":feature...") - extract what's after the opening quote
    // This handles both incomplete strings and when cursor is mid-string
    val projectCallMatch = Regex("""project\s*\(\s*["']([^"']*)$""").find(cleanText)
    if (projectCallMatch != null) {
      return projectCallMatch.groupValues[1]
    }
    
    // For projects.feature... - extract what's after "projects."
    val typeSafeMatch = Regex("""projects\.([\w.]*)$""").find(cleanText)
    if (typeSafeMatch != null) {
      return typeSafeMatch.groupValues[1]
    }
    
    return ""
  }
  
  /**
   * Checks if we're inside a project() call by looking for an opening quote after project(
   * that hasn't been closed yet.
   */
  private fun isInsideProjectCall(textBeforeCaret: String): Boolean {
    val cleanText = textBeforeCaret.replace(Regex("""IntellijIdeaRulezzz\w*"""), "")
    // Look for project(" or project(' that hasn't been closed
    return Regex("""project\s*\(\s*["'][^"']*$""").containsMatchIn(cleanText)
  }
  
  /**
   * Checks if we're typing a type-safe accessor like projects.xxx.yyy
   */
  private fun isTypingTypeSafeAccessor(textBeforeCaret: String): Boolean {
    val cleanText = textBeforeCaret.replace(Regex("""IntellijIdeaRulezzz\w*"""), "")
    // Look for projects. followed by word characters or dots
    return Regex("""projects\.([\w.]*)$""").containsMatchIn(cleanText)
  }
  
  private fun addProjectCallCompletions(
    allProjects: Set<GradlePath>,
    result: CompletionResultSet,
    typedPrefix: String
  ) {
    // Don't filter - just sort by match quality and let IntelliJ's UI handle display
    val sortedProjects = allProjects.sortedWith(
      compareBy<GradlePath> { FuzzyMatchingUtils.calculatePathMatchPriority(it.path, typedPrefix) }
        .thenBy { it.path }
    )
    
    sortedProjects.forEachIndexed { index, gradlePath ->
      val matchPriority = FuzzyMatchingUtils.calculatePathMatchPriority(gradlePath.path, typedPrefix)
      
      // Calculate priority - higher numbers appear first in PrioritizedLookupElement
      // Invert the match priority (lower number = better match = higher display priority)
      val priority = (10000.0 - (matchPriority * 1000.0)) - (index * 0.1)
      
      val lookupElement = ProjectCompletionUtils.createProjectPathLookup(
        path = gradlePath.path,
        priority = priority,
        typeText = "Gradle project"
      )
      
      result.addElement(lookupElement)
    }
  }
  
  private fun addTypeSafeAccessorCompletions(
    allProjects: Set<GradlePath>,
    result: CompletionResultSet,
    typedPrefix: String
  ) {
    // Don't filter - just sort by match quality
    val sortedProjects = allProjects.sortedWith(
      compareBy<GradlePath> { FuzzyMatchingUtils.calculateAccessorMatchPriority(it.typeSafeAccessorName, typedPrefix) }
        .thenBy { it.path }
    )
    
    sortedProjects.forEachIndexed { index, gradlePath ->
      // Use GradlePath's typeSafeAccessorName property for accurate conversion
      val accessorName = gradlePath.typeSafeAccessorName
      val matchPriority = FuzzyMatchingUtils.calculateAccessorMatchPriority(accessorName, typedPrefix)
      
      // Calculate priority - higher numbers appear first in PrioritizedLookupElement
      // Invert the match priority (lower number = better match = higher display priority)
      val priority = (10000.0 - (matchPriority * 1000.0)) - (index * 0.1)
      
      val lookupElement = ProjectCompletionUtils.createAccessorLookup(
        accessorName = accessorName,
        priority = priority,
        typeText = "Gradle project"
      )
      
      result.addElement(lookupElement)
    }
  }
}
