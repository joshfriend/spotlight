package com.fueledbycaffeine.spotlight.idea.gradle

import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext

/**
 * Contributes references for project paths in build.gradle files, enabling Cmd+Click navigation.
 * Supports both project(":path") and type-safe accessors (projects.path.to.project).
 */
class BuildGradleProjectReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    // Register for all elements - we'll filter in the provider
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(),
      BuildGradleProjectReferenceProvider()
    )
  }
}

private class BuildGradleProjectReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(
    element: PsiElement,
    context: ProcessingContext
  ): Array<PsiReference> {
    val file = element.containingFile ?: return PsiReference.EMPTY_ARRAY
    val virtualFile = file.virtualFile ?: return PsiReference.EMPTY_ARRAY
    
    // Only provide references in Gradle build files
    if (!GradleProjectPathUtils.isGradleBuildFile(virtualFile.name)) {
      return PsiReference.EMPTY_ARRAY
    }
    
    val elementText = element.text ?: return PsiReference.EMPTY_ARRAY
    
    // For string literals in project() calls, strip quotes and check if it's a project path
    val unquoted = elementText.trim().removeSurrounding("\"").removeSurrounding("'")
    if (unquoted.startsWith(":") && unquoted != elementText) {
      // This looks like a quoted project path
      val range = TextRange.from(elementText.indexOf(unquoted), unquoted.length)
      return arrayOf(BuildGradleProjectReference(element, range, unquoted))
    }
    
    // Handle type-safe accessors by checking if parent/surrounding text contains projects.
    // Match identifiers that are part of projects.xxx.yyy chains
    if (element.parent != null) {
      val parentText = element.parent.text ?: ""
      val accessorMatch = GradleProjectPathUtils.TYPE_SAFE_ACCESSOR_PATTERN.find(parentText)
      if (accessorMatch != null) {
        val typeSafeAccessor = accessorMatch.groupValues[1]
        // Check if our element is part of this accessor
        if (typeSafeAccessor.contains(elementText.trim())) {
          val range = TextRange.from(0, elementText.length)
          return arrayOf(BuildGradleProjectReference(element, range, null, typeSafeAccessor))
        }
      }
    }
    
    return PsiReference.EMPTY_ARRAY
  }
}

private class BuildGradleProjectReference(
  private val element: PsiElement,
  private val range: TextRange,
  private val projectPath: String? = null,
  private val typeSafeAccessor: String? = null
) : PsiReference {
  
  override fun getElement(): PsiElement = element
  
  override fun getRangeInElement(): TextRange = range
  
  override fun resolve(): PsiElement? {
    val project = element.project
    val spotlightService = project.spotlightService
    val allProjects = spotlightService.allProjects.value
    
    // Look up in existing projects only - don't create new GradlePath objects
    val gradlePath = when {
      projectPath != null -> allProjects.find { it.path == projectPath }
      typeSafeAccessor != null -> {
        val accessorMap = GradleProjectPathUtils.buildAccessorMap(allProjects)
        val cleanAccessor = GradleProjectPathUtils.cleanTypeSafeAccessor(typeSafeAccessor)
        accessorMap[cleanAccessor]
      }
      else -> null
    } ?: return null
    
    // Only try to access build file if it exists
    if (!gradlePath.hasBuildFile) return null
    
    val buildFilePath = try {
      gradlePath.buildFilePath
    } catch (_: Exception) {
      // Silently handle cases where path is invalid
      return null
    }
    
    val buildVirtualFile = VirtualFileManager.getInstance()
      .findFileByNioPath(buildFilePath) ?: return null
    
    return PsiManager.getInstance(project).findFile(buildVirtualFile)
  }
  
  override fun getCanonicalText(): String = projectPath ?: typeSafeAccessor ?: ""
  
  override fun handleElementRename(newElementName: String): PsiElement = element
  
  override fun bindToElement(element: PsiElement): PsiElement = this.element
  
  override fun isReferenceTo(element: PsiElement): Boolean {
    val resolved = resolve()
    return resolved != null && resolved == element
  }
  
  override fun isSoft(): Boolean = false
}
