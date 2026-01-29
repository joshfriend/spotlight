package com.fueledbycaffeine.spotlight.idea.lang

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
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
import java.nio.file.Path

/**
 * Contributes references for project paths in ide-projects.txt, enabling Cmd+Click navigation
 * with per-line highlighting.
 */
class IdeProjectsReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(),
      IdeProjectsReferenceProvider()
    )
  }
}

private class IdeProjectsReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(
    element: PsiElement,
    context: ProcessingContext
  ): Array<PsiReference> {
    val file = element.containingFile ?: return PsiReference.EMPTY_ARRAY
    
    // Only handle Spotlight project files (ide-projects.txt, all-projects.txt)
    if (!IdeProjectsFileUtils.isSpotlightProjectsFile(file)) return PsiReference.EMPTY_ARRAY
    
    // Only handle LINE tokens (not comments or newlines)
    if (element.node.elementType != SpotlightProjectsTokenTypes.LINE) {
      return PsiReference.EMPTY_ARRAY
    }
    
    val project = file.project
    val rootDir = Path.of(project.basePath ?: return PsiReference.EMPTY_ARRAY)
    
    val elementText = element.text ?: return PsiReference.EMPTY_ARRAY
    val trimmedText = elementText.trim()
    
    // Skip blank lines and glob patterns
    if (trimmedText.isBlank() || trimmedText.contains('*') || trimmedText.contains('?')) {
      return PsiReference.EMPTY_ARRAY
    }
    
    val gradlePath = GradlePath(rootDir, trimmedText)
    if (!gradlePath.hasBuildFile) {
      return PsiReference.EMPTY_ARRAY
    }
    
    // Calculate range for the trimmed content within the element
    val startOffset = elementText.indexOf(trimmedText)
    val range = TextRange(startOffset, startOffset + trimmedText.length)
    
    return arrayOf(IdeProjectsReference(element, range, gradlePath))
  }
}

private class IdeProjectsReference(
  private val element: PsiElement,
  private val range: TextRange,
  private val gradlePath: GradlePath
) : PsiReference {
  
  override fun getElement(): PsiElement = element
  
  override fun getRangeInElement(): TextRange = range
  
  override fun resolve(): PsiElement? {
    val buildFilePath = try {
      gradlePath.buildFilePath
    } catch (_: Exception) {
      return null
    }
    
    val buildVirtualFile = VirtualFileManager.getInstance()
      .findFileByNioPath(buildFilePath) ?: return null
    
    return PsiManager.getInstance(element.project).findFile(buildVirtualFile)
  }
  
  override fun getCanonicalText(): String = gradlePath.path
  
  override fun handleElementRename(newElementName: String): PsiElement = element
  
  override fun bindToElement(element: PsiElement): PsiElement = this.element
  
  override fun isReferenceTo(element: PsiElement): Boolean {
    val resolved = resolve()
    return resolved != null && resolved == element
  }
  
  override fun isSoft(): Boolean = false
}
