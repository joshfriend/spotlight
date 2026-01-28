package com.fueledbycaffeine.spotlight.idea.lang

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.IDE_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.idea.SpotlightBundle
import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.nio.file.FileSystems

/**
 * Annotates the ide-projects.txt file with validation errors for paths that don't exist.
 */
class IdeProjectsAnnotator : Annotator, DumbAware {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    val file = element.containingFile ?: return
    
    // Only annotate ide-projects.txt files
    if (!isIdeProjectsFile(file)) return
    
    // Only process the file element itself to avoid redundant checks
    if (element != file) return
    
    val project = element.project
    val spotlightService = project.spotlightService
    val allProjects = spotlightService.allProjects.value
    val text = file.text
    val lines = text.lines()
    
    var offset = 0
    for (line in lines) {
      val trimmed = line.trim()
      
      // Skip comments and blank lines
      if (trimmed.startsWith("#") || trimmed.isBlank()) {
        offset += line.length + 1 // +1 for newline
        continue
      }
      
      // Check if the path is valid
      if (!isValidPath(trimmed, allProjects)) {
        val lineStartOffset = offset
        val lineEndOffset = offset + line.length
        
        // Create annotation for the entire line
        holder.newAnnotation(HighlightSeverity.ERROR, SpotlightBundle.message("annotator.invalid.path"))
          .range(TextRange(lineStartOffset, lineEndOffset))
          .textAttributes(DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE)
          .withFix(RemoveInvalidPathIntentionAction(trimmed))
          .create()
      }
      
      offset += line.length + 1 // +1 for newline
    }
  }
  
  private fun isIdeProjectsFile(file: PsiFile): Boolean {
    val virtualFile = file.virtualFile ?: return false
    return virtualFile.path.endsWith(IDE_PROJECTS_LOCATION)
  }
  
  private fun isValidPath(path: String, allProjects: Set<GradlePath>): Boolean {
    // Paths with glob characters are always considered valid if they match at least one project
    if (path.containsGlobChar()) {
      return matchesAnyProject(path, allProjects)
    }
    
    // Direct paths: check if they exist in allProjects
    return allProjects.any { it.path == path }
  }
  
  private fun matchesAnyProject(pattern: String, allProjects: Set<GradlePath>): Boolean {
    val globPattern = "glob:$pattern"
    val pathMatcher = FileSystems.getDefault().getPathMatcher(globPattern)
    return allProjects.any { gradlePath ->
      val pathToMatch = FileSystems.getDefault().getPath(gradlePath.path)
      pathMatcher.matches(pathToMatch)
    }
  }
  
  private fun String.containsGlobChar(): Boolean {
    return contains('*') || contains('?')
  }
}
