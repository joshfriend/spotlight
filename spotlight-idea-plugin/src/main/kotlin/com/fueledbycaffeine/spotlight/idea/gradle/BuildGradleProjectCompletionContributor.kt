package com.fueledbycaffeine.spotlight.idea.gradle

import com.fueledbycaffeine.spotlight.idea.completion.ProjectCompletionUtils
import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * Provides autocomplete functionality for project(...) dependencies in Gradle build files.
 * Based on Foundry/Skate's implementation.
 */
class BuildGradleProjectCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(), ProjectPathCompletionProvider())
  }
}

private class ProjectPathCompletionProvider : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val file = parameters.originalFile
    val fileName = file.name
    
    // Only in gradle files
    if (!fileName.endsWith(".gradle") && !fileName.endsWith(".gradle.kts")) {
      return
    }

    val project = parameters.position.project
    val spotlightService = project.spotlightService
    val allProjects = spotlightService.allProjects.value

    if (allProjects.isEmpty()) return

    // Simple check: look at file content around the cursor position
    val document = parameters.editor.document
    val offset = parameters.offset

    // Get text around the cursor to see if we're in a project() call
    val fileText = document.text
    val startOffset = maxOf(0, offset - 100)
    val endOffset = minOf(fileText.length, offset + 100)
    val surroundingText = fileText.substring(startOffset, endOffset)

    if (!surroundingText.contains("project(")) {
      return
    }

    for (gradlePath in allProjects) {
      val lookupElement = ProjectCompletionUtils.createProjectPathLookup(
        path = gradlePath.path,
        typeText = "Gradle project"
      )
      result.addElement(lookupElement)
    }

    // Stop other completion contributors from running for cleaner results
    result.stopHere()
  }
}
