package com.fueledbycaffeine.spotlight.idea.completion

import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.IDE_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.PrioritizedLookupElement
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
    // Only set empty dummy identifier for ide-projects.txt files
    // For Gradle files, we can't set it here because it conflicts with Groovy's completion contributor
    if (isIdeProjectsFile(context.file.virtualFile?.path)) {
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
    
    // Get text from start of line to caret - this is what the user is typing
    val textBeforeCaret = document.getText(TextRange(lineStartOffset, caretOffset))

    // Don't provide completions for comment lines
    if (textBeforeCaret.trim().startsWith("#")) return

    // Use the trimmed text as prefix - this ensures proper replacement
    val typedPrefix = textBeforeCaret.trim()
    
    // Use prefix matcher so IntelliJ replaces what's typed with our completion
    val prefixResult = result.withPrefixMatcher(typedPrefix)

    allProjects
      .sortedBy { it.path }
      .forEach { gradlePath ->
        // Don't mark as "already included" if it matches what's being typed
        val isAlreadyIncluded = gradlePath in existingPaths && gradlePath.path != typedPrefix

        val lookupElement = ProjectCompletionUtils.createProjectPathLookup(
          path = gradlePath.path,
          typeText = if (isAlreadyIncluded) "already included" else null,
          bold = !isAlreadyIncluded
        )

        prefixResult.addElement(lookupElement)
      }
    
    // Prevent spelling and other completions from interfering
    result.stopHere()
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
    val lineText = document.getText(TextRange(lineStartOffset, offset))
    val cleanText = cleanDummyIdentifier(lineText)
    
    // Check if we're actually inside a project() string literal or typing a projects. accessor
    // This is the key check - we only provide completions if we're actually in the right context
    val projectCallMatch = Regex("""project\s*\(\s*["']([^"']*)$""").find(cleanText)
    val typeSafeMatch = Regex("""projects\.([\w.]*)$""").find(cleanText)
    
    when {
      projectCallMatch != null -> {
        // We're inside quotes in a project() call - provide path completions
        val prefix = projectCallMatch.groupValues[1]
        val fuzzyResult = result.withPrefixMatcher(FuzzyGradlePrefixMatcher(prefix, isPathMatch = true))
        
        allProjects.forEach { gradlePath ->
          val priority = FuzzyMatchingUtils.calculatePathMatchPriority(gradlePath.path, prefix)
          if (priority < 100) {
            val lookupElement = ProjectCompletionUtils.createProjectPathLookup(
              path = gradlePath.path,
              typeText = "Gradle project"
            )
            fuzzyResult.addElement(
              PrioritizedLookupElement.withPriority(lookupElement, (100 - priority).toDouble())
            )
          }
        }
        // Only stop here when we're actually inside quotes
        result.stopHere()
      }
      typeSafeMatch != null -> {
        // We're typing a type-safe accessor - provide accessor completions
        val prefix = typeSafeMatch.groupValues[1]
        val fuzzyResult = result.withPrefixMatcher(FuzzyGradlePrefixMatcher(prefix, isPathMatch = false))
        
        allProjects.forEach { gradlePath ->
          val accessor = gradlePath.typeSafeAccessorName
          val priority = FuzzyMatchingUtils.calculateAccessorMatchPriority(accessor, prefix)
          if (priority < 100) {
            val lookupElement = ProjectCompletionUtils.createAccessorLookup(
              accessorName = accessor,
              typeText = "Gradle project"
            )
            fuzzyResult.addElement(
              PrioritizedLookupElement.withPriority(lookupElement, (100 - priority).toDouble())
            )
          }
        }
        // Don't stopHere for type-safe accessors - other completions may be relevant
      }
      // If neither match, we're not in a completion context - don't add anything
    }
  }
  
  private fun cleanDummyIdentifier(text: String): String {
    return text.replace(Regex("""${CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED}\w*"""), "")
  }
}

/**
 * Custom PrefixMatcher that uses our fuzzy matching logic.
 */
private class FuzzyGradlePrefixMatcher(
  prefix: String,
  private val isPathMatch: Boolean
) : PrefixMatcher(prefix) {
  
  override fun prefixMatches(name: String): Boolean {
    val priority = if (isPathMatch) {
      FuzzyMatchingUtils.calculatePathMatchPriority(name, prefix)
    } else {
      FuzzyMatchingUtils.calculateAccessorMatchPriority(name, prefix)
    }
    return priority < 100
  }

  override fun cloneWithPrefix(prefix: String): PrefixMatcher {
    return FuzzyGradlePrefixMatcher(prefix, isPathMatch)
  }
}

/**
 * Utility functions for detecting completion context. Exposed for testing.
 */
internal object CompletionContextUtils {

  /** Checks if a file path is an ide-projects.txt file. */
  fun isIdeProjectsFile(path: String?): Boolean {
    return path?.endsWith(IDE_PROJECTS_LOCATION) == true
  }

  /** Checks if a filename is a Gradle build file. */
  fun isGradleBuildFile(fileName: String): Boolean {
    return fileName.endsWith(".gradle") || fileName.endsWith(".gradle.kts")
  }
}

private fun isIdeProjectsFile(path: String?): Boolean {
  return CompletionContextUtils.isIdeProjectsFile(path)
}

private fun isGradleBuildFile(fileName: String): Boolean {
  return CompletionContextUtils.isGradleBuildFile(fileName)
}
