package com.fueledbycaffeine.spotlight.idea.lang

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager

/**
 * Allows Cmd+Click navigation from paths in Spotlight project files to their build files.
 */
class IdeProjectsGotoDeclarationHandler : GotoDeclarationHandler, DumbAware {
  private companion object {
    private val logger = Logger.getInstance(IdeProjectsGotoDeclarationHandler::class.java)
  }
  
  override fun getGotoDeclarationTargets(
    sourceElement: PsiElement?,
    offset: Int,
    editor: Editor?
  ): Array<PsiElement>? {
    if (sourceElement == null || editor == null) return null
    val file = sourceElement.containingFile ?: return null
    
    // Only handle Spotlight project files (ide-projects.txt, all-projects.txt)
    if (!IdeProjectsFileUtils.isSpotlightProjectsFile(file)) return null
    
    // Get the Gradle path at the cursor position
    val gradlePath = IdeProjectsFileUtils.getGradlePathAtOffset(editor, file, offset) ?: return null
    
    // Find the build file
    val buildFilePath = try {
      gradlePath.buildFilePath
    } catch (e: Exception) {
      logger.warn("Failed to get build file path for ${gradlePath.path}", e)
      return null
    }
    
    val buildVirtualFile = VirtualFileManager.getInstance().findFileByNioPath(buildFilePath) ?: return null
    val buildPsiFile = PsiManager.getInstance(file.project).findFile(buildVirtualFile) ?: return null
    
    return arrayOf(buildPsiFile)
  }
}
