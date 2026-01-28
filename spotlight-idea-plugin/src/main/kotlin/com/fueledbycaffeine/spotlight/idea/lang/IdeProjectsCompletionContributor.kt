package com.fueledbycaffeine.spotlight.idea.lang

import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.IDE_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.idea.completion.ProjectCompletionUtils
import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * Provides completion suggestions for Gradle paths in the ide-projects.txt file.
 */
class IdeProjectsCompletionContributor : CompletionContributor(), DumbAware {
  init {
    extend(
      CompletionType.BASIC,
      PlatformPatterns.psiElement(),
      IdeProjectsCompletionProvider()
    )
  }
  
  override fun beforeCompletion(context: CompletionInitializationContext) {
    // Don't insert dummy identifier for completion
    context.dummyIdentifier = ""
  }
}

private class IdeProjectsCompletionProvider : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val file = parameters.originalFile
    val virtualFile = file.virtualFile ?: return
    
    // Only provide completions in ide-projects.txt
    if (!virtualFile.path.endsWith(IDE_PROJECTS_LOCATION)) return
    
    val project = parameters.editor.project ?: return
    val spotlightService = project.spotlightService
    val allProjects = spotlightService.allProjects.value
    val existingPaths = spotlightService.ideProjects.value
    
    // Get the current line text up to the cursor
    val editor = parameters.editor
    val document = editor.document
    val caretOffset = parameters.offset
    val lineNumber = document.getLineNumber(caretOffset)
    val lineStartOffset = document.getLineStartOffset(lineNumber)
    val lineEndOffset = document.getLineEndOffset(lineNumber)
    val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStartOffset, lineEndOffset))
    val textBeforeCaret = document.getText(com.intellij.openapi.util.TextRange(lineStartOffset, caretOffset))
    
    // Don't provide completions for comment lines
    if (lineText.trim().startsWith("#")) return
    
    // Get the current line's path (if editing an existing path)
    val currentLinePath = lineText.trim()
    
    // Get the actual prefix (text on current line before cursor, trimmed)
    val prefix = textBeforeCaret.trim()
    
    // Use IntelliJ's default prefix matcher for proper fuzzy matching
    val prefixResult = if (prefix.isEmpty()) {
      result
    } else {
      result.withPrefixMatcher(prefix)
    }
    
    // Provide matching available projects
    allProjects
      .sortedBy { it.path }
      .forEach { gradlePath ->
        // Don't mark as "already included" if it's the current line being edited
        val isAlreadyIncluded = gradlePath in existingPaths && gradlePath.path != currentLinePath
        
        val lookupElement = ProjectCompletionUtils.createProjectPathLookup(
          path = gradlePath.path,
          typeText = if (isAlreadyIncluded) "already included" else null,
          bold = !isAlreadyIncluded
        )
        
        prefixResult.addElement(lookupElement)
      }
  }
}
