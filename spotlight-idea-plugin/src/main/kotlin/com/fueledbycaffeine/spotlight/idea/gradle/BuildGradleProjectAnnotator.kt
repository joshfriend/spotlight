package com.fueledbycaffeine.spotlight.idea.gradle

import com.fueledbycaffeine.spotlight.idea.SpotlightBundle
import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Annotator that validates project paths in build.gradle files.
 * Supports both project(":path") and type-safe accessor (projects.path.to.project) syntax.
 */
class BuildGradleProjectAnnotator : Annotator, DumbAware {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    val file = element.containingFile ?: return
    
    // Only process Gradle build files
    if (!GradleProjectPathUtils.isGradleBuildFile(file.name)) return
    
    val elementText = element.text
    
    val spotlightService = element.project.spotlightService
    val allProjects = spotlightService.allProjects.value
    
    // Check project() calls
    annotateProjectCalls(element, elementText, holder, allProjects)
    
    // Check type-safe accessors
    annotateTypeSafeAccessors(element, elementText, holder, allProjects)
  }
  
  private fun annotateProjectCalls(
    element: PsiElement,
    elementText: String,
    holder: AnnotationHolder,
    allProjects: Set<com.fueledbycaffeine.spotlight.buildscript.GradlePath>
  ) {
    GradleProjectPathUtils.PROJECT_CALL_PATTERN.findAll(elementText).forEach { matchResult ->
      val (quoteChar, projectPath) = matchResult.destructured
      val startOffset = element.textRange.startOffset + matchResult.groups[2]!!.range.first
      val endOffset = element.textRange.startOffset + matchResult.groups[2]!!.range.last + 1
      val range = TextRange.create(startOffset, endOffset)
      
      // The regex only matches COMPLETE project() calls (with closing quote & paren)
      // So if we're here, the string is complete and should match exactly
      val isValid = allProjects.any { it.path == projectPath }
      
      if (!isValid) {
        holder.newAnnotation(
          HighlightSeverity.WEAK_WARNING,
          SpotlightBundle.message("annotator.invalid.path")
        )
          .range(range)
          .create()
      }
    }
  }
  
  private fun annotateTypeSafeAccessors(
    element: PsiElement,
    elementText: String,
    holder: AnnotationHolder,
    allProjects: Set<com.fueledbycaffeine.spotlight.buildscript.GradlePath>
  ) {
    // Build a map of type-safe accessor names to GradlePath objects
    val accessorMap = GradleProjectPathUtils.buildAccessorMap(allProjects)
    
    GradleProjectPathUtils.TYPE_SAFE_ACCESSOR_PATTERN.findAll(elementText).forEach { matchResult ->
      val (typeSafeAccessor) = matchResult.destructured
      // Clean up accessor: remove "projects." prefix and junk suffixes
      val cleanAccessor = GradleProjectPathUtils.cleanTypeSafeAccessor(typeSafeAccessor)
      
      val startOffset = element.textRange.startOffset + matchResult.range.first
      val endOffset = element.textRange.startOffset + matchResult.range.last + 1
      val range = TextRange.create(startOffset, endOffset)
      
      // The regex matches complete accessor expressions, so validate strictly
      // Only allow if it's an exact match (no partial matching for accessors)
      val isValid = accessorMap.containsKey(cleanAccessor)
      
      if (!isValid) {
        holder.newAnnotation(
          HighlightSeverity.WEAK_WARNING,
          SpotlightBundle.message("annotator.invalid.path")
        )
          .range(range)
          .create()
      }
    }
  }
}
