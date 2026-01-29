package com.fueledbycaffeine.spotlight.idea.annotator

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.ALL_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.IDE_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.idea.SpotlightBundle
import com.fueledbycaffeine.spotlight.idea.gradle.GradleProjectPathUtils
import com.fueledbycaffeine.spotlight.idea.lang.RemoveInvalidPathIntentionAction
import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Unified annotator for validating Gradle project paths.
 * Handles:
 * - ide-projects.txt files (line-by-line path validation + comment highlighting)
 * - all-projects.txt files (comment highlighting)
 * - build.gradle files (project() calls and type-safe accessor validation)
 */
class SpotlightAnnotator : Annotator, DumbAware {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    val file = element.containingFile ?: return
    
    // Only process the file element itself to avoid duplicate annotations
    // (parent elements contain the same text as children)
    if (element != file) return
    
    when {
      isSpotlightProjectFile(file) -> annotateSpotlightProjectFile(file, holder)
      GradleProjectPathUtils.isGradleBuildFile(file.name) -> {
        val spotlightService = element.project.spotlightService
        val allProjects = spotlightService.allProjects.value
        if (allProjects.isNotEmpty()) {
          annotateGradleBuildFile(file, holder, allProjects)
        }
      }
    }
  }
  
  // ===== Spotlight project files (ide-projects.txt, all-projects.txt) =====
  
  private fun annotateSpotlightProjectFile(
    file: PsiFile,
    holder: AnnotationHolder
  ) {
    val text = file.text
    val lines = text.lines()
    val isIdeProjects = isIdeProjectsFile(file)
    
    // Only validate paths in ide-projects.txt (all-projects.txt is auto-generated)
    val spotlightService = file.project.spotlightService
    val allProjects = if (isIdeProjects) spotlightService.allProjects.value else emptySet()
    
    var offset = 0
    for (line in lines) {
      val trimmed = line.trim()
      
      // Highlight comment lines
      if (trimmed.startsWith("#")) {
        val range = TextRange(offset, offset + line.length)
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
          .range(range)
          .textAttributes(DefaultLanguageHighlighterColors.LINE_COMMENT)
          .create()
        offset += line.length + 1
        continue
      }
      
      // Skip blank lines
      if (trimmed.isBlank()) {
        offset += line.length + 1
        continue
      }
      
      // Validate paths only in ide-projects.txt
      if (isIdeProjects && allProjects.isNotEmpty() && !GradleProjectPathUtils.isValidIdeProjectPath(trimmed, allProjects)) {
        val range = TextRange(offset, offset + line.length)
        val bestMatch = GradleProjectPathUtils.findBestMatchingPath(trimmed, allProjects)
        
        val annotationBuilder = holder.newAnnotation(
          HighlightSeverity.ERROR,
          SpotlightBundle.message("annotator.invalid.path")
        )
          .range(range)
          .textAttributes(DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE)
          .withFix(RemoveInvalidPathIntentionAction(trimmed))
        
        if (bestMatch != null) {
          annotationBuilder.withFix(
            CompleteWithSuggestionAction(
              suggestion = bestMatch.path,
              range = range,
              isTypeSafeAccessor = false
            )
          )
        }
        
        annotationBuilder.create()
      }
      
      offset += line.length + 1
    }
  }
  
  private fun isSpotlightProjectFile(file: PsiFile): Boolean {
    val virtualFile = file.virtualFile ?: return false
    val path = virtualFile.path
    return path.endsWith(IDE_PROJECTS_LOCATION) || path.endsWith(ALL_PROJECTS_LOCATION)
  }
  
  private fun isIdeProjectsFile(file: PsiFile): Boolean {
    val virtualFile = file.virtualFile ?: return false
    return virtualFile.path.endsWith(IDE_PROJECTS_LOCATION)
  }
  
  // ===== build.gradle handling =====
  
  private fun annotateGradleBuildFile(
    file: PsiFile,
    holder: AnnotationHolder,
    allProjects: Set<GradlePath>
  ) {
    val fileText = file.text
    annotateProjectCalls(fileText, holder, allProjects)
    annotateTypeSafeAccessors(fileText, holder, allProjects)
  }
  
  private fun annotateProjectCalls(
    fileText: String,
    holder: AnnotationHolder,
    allProjects: Set<GradlePath>
  ) {
    GradleProjectPathUtils.PROJECT_CALL_PATTERN.findAll(fileText).forEach { matchResult ->
      val (_, projectPath) = matchResult.destructured
      val startOffset = matchResult.groups[2]!!.range.first
      val endOffset = matchResult.groups[2]!!.range.last + 1
      val range = TextRange.create(startOffset, endOffset)
      
      val isValid = GradleProjectPathUtils.isValidProjectPath(projectPath, allProjects)
      
      if (!isValid) {
        val bestMatch = GradleProjectPathUtils.findBestMatchingPath(projectPath, allProjects)
        val annotationBuilder = holder.newAnnotation(
          HighlightSeverity.WEAK_WARNING,
          SpotlightBundle.message("annotator.invalid.path")
        ).range(range)
        
        if (bestMatch != null) {
          annotationBuilder.withFix(
            CompleteWithSuggestionAction(
              suggestion = bestMatch.path,
              range = range,
              isTypeSafeAccessor = false
            )
          )
        }
        
        // Add remove line option with lower priority
        annotationBuilder.withFix(RemoveInvalidLineAction(fileText, startOffset))
        
        annotationBuilder.create()
      }
    }
  }
  
  private fun annotateTypeSafeAccessors(
    fileText: String,
    holder: AnnotationHolder,
    allProjects: Set<GradlePath>
  ) {
    val accessorMap = GradleProjectPathUtils.buildAccessorMap(allProjects)
    
    GradleProjectPathUtils.TYPE_SAFE_ACCESSOR_PATTERN.findAll(fileText).forEach { matchResult ->
      val (typeSafeAccessor) = matchResult.destructured
      val cleanAccessor = GradleProjectPathUtils.cleanTypeSafeAccessor(typeSafeAccessor)
      
      val startOffset = matchResult.range.first
      val endOffset = matchResult.range.last + 1
      val range = TextRange.create(startOffset, endOffset)
      
      val isValid = GradleProjectPathUtils.isValidAccessor(cleanAccessor, accessorMap)
      
      if (!isValid) {
        val bestMatch = GradleProjectPathUtils.findBestMatchingAccessor(cleanAccessor, accessorMap)
        val annotationBuilder = holder.newAnnotation(
          HighlightSeverity.WEAK_WARNING,
          SpotlightBundle.message("annotator.invalid.path")
        ).range(range)
        
        if (bestMatch != null) {
          annotationBuilder.withFix(
            CompleteWithSuggestionAction(
              suggestion = bestMatch.typeSafeAccessorName,
              range = range,
              isTypeSafeAccessor = true
            )
          )
        }
        
        // Add remove line option with lower priority
        annotationBuilder.withFix(RemoveInvalidLineAction(fileText, startOffset))
        
        annotationBuilder.create()
      }
    }
  }
}

/**
 * QuickFix action that replaces an invalid path with the best autocomplete suggestion.
 * Uses HighPriorityAction to appear above spelling suggestions.
 */
class CompleteWithSuggestionAction(
  private val suggestion: String,
  private val range: TextRange,
  private val isTypeSafeAccessor: Boolean
) : IntentionAction, HighPriorityAction, PriorityAction, DumbAware {
  
  private val displayText: String = if (isTypeSafeAccessor) "projects.$suggestion" else suggestion
  
  override fun getText(): String = SpotlightBundle.message("intention.complete.with.suggestion", displayText)
  
  override fun getFamilyName(): String = SpotlightBundle.message("statusbar.widget.name")
  
  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true
  
  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    editor ?: return
    
    WriteCommandAction.runWriteCommandAction(project) {
      val document = editor.document
      val replacementText = if (isTypeSafeAccessor) "projects.$suggestion" else suggestion
      document.replaceString(range.startOffset, range.endOffset, replacementText)
    }
  }
  
  override fun startInWriteAction(): Boolean = false
  
  override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.TOP
}

/**
 * QuickFix action that removes the entire line containing an invalid project reference.
 * Lower priority than CompleteWithSuggestionAction.
 */
class RemoveInvalidLineAction(
  private val fileText: String,
  private val offsetInLine: Int
) : IntentionAction, LowPriorityAction, PriorityAction, DumbAware {
  
  override fun getText(): String = SpotlightBundle.message("intention.remove.invalid.line")
  
  override fun getFamilyName(): String = SpotlightBundle.message("statusbar.widget.name")
  
  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true
  
  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    editor ?: return
    
    val document = editor.document
    val lineNumber = document.getLineNumber(offsetInLine)
    val lineStartOffset = document.getLineStartOffset(lineNumber)
    val lineEndOffset = document.getLineEndOffset(lineNumber)
    
    WriteCommandAction.runWriteCommandAction(project) {
      // Delete the line including the newline character if not the last line
      val deleteEnd = if (lineEndOffset < document.textLength) lineEndOffset + 1 else lineEndOffset
      val deleteStart = if (lineStartOffset > 0 && deleteEnd == lineEndOffset) lineStartOffset - 1 else lineStartOffset
      document.deleteString(deleteStart, deleteEnd)
    }
  }
  
  override fun startInWriteAction(): Boolean = false
  
  override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.LOW
}
