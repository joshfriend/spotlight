package com.fueledbycaffeine.spotlight.idea.json

import com.fueledbycaffeine.spotlight.buildscript.SpotlightRulesList.Companion.SPOTLIGHT_RULES_LOCATION
import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext

/**
 * Contributes references for project paths in spotlight-rules.json files,
 * enabling Cmd+Click navigation to project build files.
 */
class SpotlightRulesReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(JsonStringLiteral::class.java),
      SpotlightRulesReferenceProvider()
    )
  }
}

private class SpotlightRulesReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(
    element: PsiElement,
    context: ProcessingContext
  ): Array<PsiReference> {
    val file = element.containingFile ?: return PsiReference.EMPTY_ARRAY
    val virtualFile = file.virtualFile ?: return PsiReference.EMPTY_ARRAY
    
    // Only provide references in spotlight-rules.json
    if (!virtualFile.path.endsWith(SPOTLIGHT_RULES_LOCATION)) {
      return PsiReference.EMPTY_ARRAY
    }
    
    // Must be a JSON string literal
    if (element !is JsonStringLiteral) {
      return PsiReference.EMPTY_ARRAY
    }
    
    // Check if this is inside an "includedProjects" array
    if (!isInsideIncludedProjectsArray(element)) {
      return PsiReference.EMPTY_ARRAY
    }
    
    val projectPath = element.value
    // Only provide references for valid-looking project paths
    if (!projectPath.startsWith(":")) {
      return PsiReference.EMPTY_ARRAY
    }
    
    // TextRange is for the content inside the quotes (skip the opening quote)
    val range = TextRange.from(1, projectPath.length)
    return arrayOf(SpotlightRulesProjectReference(element, range, projectPath))
  }
  
  private fun isInsideIncludedProjectsArray(element: PsiElement): Boolean {
    var current: PsiElement? = element.parent
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

private class SpotlightRulesProjectReference(
  private val element: JsonStringLiteral,
  private val range: TextRange,
  private val projectPath: String
) : PsiReference {
  
  override fun getElement(): PsiElement = element
  
  override fun getRangeInElement(): TextRange = range
  
  override fun resolve(): PsiElement? {
    val project = element.project
    val spotlightService = project.spotlightService
    val allProjects = spotlightService.allProjects.value
    
    // Find the matching GradlePath
    val gradlePath = allProjects.find { it.path == projectPath } ?: return null
    
    // Only try to access build file if it exists
    if (!gradlePath.hasBuildFile) return null
    
    val buildFilePath = try {
      gradlePath.buildFilePath
    } catch (_: Exception) {
      return null
    }
    
    val buildVirtualFile = VfsUtil.findFile(buildFilePath, true) ?: return null
    return PsiManager.getInstance(project).findFile(buildVirtualFile)
  }
  
  override fun getCanonicalText(): String = projectPath
  
  override fun handleElementRename(newElementName: String): PsiElement = element
  
  override fun bindToElement(element: PsiElement): PsiElement = this.element
  
  override fun isReferenceTo(element: PsiElement): Boolean {
    val resolved = resolve()
    return resolved != null && resolved == element
  }
  
  override fun isSoft(): Boolean = true
}
