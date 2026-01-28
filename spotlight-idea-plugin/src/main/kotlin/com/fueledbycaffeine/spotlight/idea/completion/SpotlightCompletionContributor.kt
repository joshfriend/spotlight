package com.fueledbycaffeine.spotlight.idea.completion

import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.IDE_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * Unified completion contributor for Spotlight project paths.
 * Handles completions in:
 * - ide-projects.txt files (plain text project paths)
 * - build.gradle files with project(":path") calls
 * - build.gradle files with type-safe accessors (projects.path.to.project)
 */
class SpotlightCompletionContributor : CompletionContributor(), DumbAware {
  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(), SpotlightCompletionProvider())
  }

  override fun beforeCompletion(context: CompletionInitializationContext) {
    val fileName = context.file.name
    // Set empty dummy identifier for proper prefix matching in supported files
    if (isIdeProjectsFile(context.file.virtualFile?.path) ||
        isGradleBuildFile(fileName)) {
      context.dummyIdentifier = ""
    }
  }
}

private class SpotlightCompletionProvider : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val file = parameters.originalFile
    val virtualFile = file.virtualFile ?: return
    val fileName = file.name

    when {
      isIdeProjectsFile(virtualFile.path) -> addIdeProjectsCompletions(parameters, result)
      isGradleBuildFile(fileName) -> addGradleBuildCompletions(parameters, result)
    }
  }

  private fun addIdeProjectsCompletions(
    parameters: CompletionParameters,
    result: CompletionResultSet
  ) {
    val project = parameters.editor.project ?: return
    val spotlightService = project.spotlightService
    val allProjects = spotlightService.allProjects.value
    val existingPaths = spotlightService.ideProjects.value

    val document = parameters.editor.document
    val caretOffset = parameters.offset
    val lineNumber = document.getLineNumber(caretOffset)
    val lineStartOffset = document.getLineStartOffset(lineNumber)
    val lineEndOffset = document.getLineEndOffset(lineNumber)
    val lineText = document.getText(TextRange(lineStartOffset, lineEndOffset))

    // Don't provide completions for comment lines
    if (lineText.trim().startsWith("#")) return

    // Get the current line's path (if editing an existing path)
    val currentLinePath = lineText.trim()

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

        result.addElement(lookupElement)
      }
  }

  private fun addGradleBuildCompletions(
    parameters: CompletionParameters,
    result: CompletionResultSet
  ) {
    val project = parameters.position.project
    val spotlightService = project.spotlightService
    val allProjects = spotlightService.allProjects.value

    if (allProjects.isEmpty()) return

    val document = parameters.editor.document
    val offset = parameters.offset
    val lineNumber = document.getLineNumber(offset)
    val lineStartOffset = document.getLineStartOffset(lineNumber)
    val textBeforeCaret = document.getText(TextRange(lineStartOffset, offset))

    val inProjectCall = isInsideProjectCall(textBeforeCaret)
    val inTypeSafeContext = isTypingTypeSafeAccessor(textBeforeCaret)

    when {
      inProjectCall -> {
        // Extract the prefix (what's typed after the opening quote)
        val prefix = extractProjectCallPrefix(textBeforeCaret)
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
      inTypeSafeContext -> {
        // Extract the prefix (what's typed after "projects.")
        val prefix = extractAccessorPrefix(textBeforeCaret)
        val prefixResult = if (prefix.isEmpty()) result else result.withPrefixMatcher(prefix)
        
        allProjects
          .sortedBy { it.path }
          .forEach { gradlePath ->
            val lookupElement = ProjectCompletionUtils.createAccessorLookup(
              accessorName = gradlePath.typeSafeAccessorName,
              typeText = "Gradle project"
            )
            prefixResult.addElement(lookupElement)
          }
        result.stopHere()
      }
    }
  }
  
  /** Extracts the typed prefix from inside a project() call. */
  private fun extractProjectCallPrefix(textBeforeCaret: String): String {
    val cleanText = CompletionContextUtils.cleanDummyIdentifier(textBeforeCaret)
    // Match everything after the last quote in project("xxx or project('xxx
    val match = Regex("""project\s*\(\s*["']([^"']*)$""").find(cleanText)
    return match?.groupValues?.get(1) ?: ""
  }
  
  /** Extracts the typed prefix from a type-safe accessor context. */
  private fun extractAccessorPrefix(textBeforeCaret: String): String {
    val cleanText = CompletionContextUtils.cleanDummyIdentifier(textBeforeCaret)
    // Match everything after "projects."
    val match = Regex("""projects\.([\w.]*)$""").find(cleanText)
    return match?.groupValues?.get(1) ?: ""
  }

  private fun isInsideProjectCall(textBeforeCaret: String): Boolean {
    return CompletionContextUtils.isInsideProjectCall(textBeforeCaret)
  }

  private fun isTypingTypeSafeAccessor(textBeforeCaret: String): Boolean {
    return CompletionContextUtils.isTypingTypeSafeAccessor(textBeforeCaret)
  }
}

/**
 * Utility functions for detecting completion context. Exposed for testing.
 */
internal object CompletionContextUtils {
  
  /** 
   * Checks if the cursor is inside a project() call.
   * Looks for project( followed by a quote, indicating we're in the string argument.
   */
  fun isInsideProjectCall(textBeforeCaret: String): Boolean {
    val cleanText = cleanDummyIdentifier(textBeforeCaret)
    // Match project( followed by optional whitespace and an opening quote
    return Regex("""project\s*\(\s*["']""").containsMatchIn(cleanText)
  }

  /** 
   * Checks if the cursor is typing a type-safe accessor like projects.xxx.yyy
   * Only matches if NOT inside a project() call.
   */
  fun isTypingTypeSafeAccessor(textBeforeCaret: String): Boolean {
    val cleanText = cleanDummyIdentifier(textBeforeCaret)
    // Don't match if we're inside a project() call
    if (isInsideProjectCall(textBeforeCaret)) return false
    return Regex("""projects\.([\w.]*)$""").containsMatchIn(cleanText)
  }

  /** Checks if a file path is an ide-projects.txt file. */
  fun isIdeProjectsFile(path: String?): Boolean {
    return path?.endsWith(IDE_PROJECTS_LOCATION) == true
  }

  /** Checks if a filename is a Gradle build file. */
  fun isGradleBuildFile(fileName: String): Boolean {
    return fileName.endsWith(".gradle") || fileName.endsWith(".gradle.kts")
  }

  /** Removes IntelliJ's dummy identifier from text for pattern matching. */
  fun cleanDummyIdentifier(text: String): String {
    return text.replace(Regex("""IntellijIdeaRulezzz\w*"""), "")
  }
}

private fun isIdeProjectsFile(path: String?): Boolean {
  return CompletionContextUtils.isIdeProjectsFile(path)
}

private fun isGradleBuildFile(fileName: String): Boolean {
  return CompletionContextUtils.isGradleBuildFile(fileName)
}
