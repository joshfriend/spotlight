package com.fueledbycaffeine.spotlight.idea.gradle

import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.util.asSafely

/**
 * Overrides Gradle's default navigation for type-safe project accessors to navigate to build files.
 * This takes precedence over the generated accessor classes in org.gradle.accessors.dm package.
 */
class BuildGradleProjectGotoHandler : GotoDeclarationHandler {
  override fun getGotoDeclarationTargets(
    sourceElement: PsiElement?,
    offset: Int,
    editor: Editor?
  ): Array<PsiElement>? {
    if (sourceElement == null || editor == null) return null
    
    val file = sourceElement.containingFile ?: return null
    val virtualFile = file.virtualFile ?: return null
    
    // Only handle Gradle build files
    if (!GradleProjectPathUtils.isGradleBuildFile(virtualFile.name)) return null
    
    val project = sourceElement.project
    val spotlightService = project.spotlightService
    val allProjects = spotlightService.allProjects.value
    
    if (allProjects.isEmpty()) return null
    
    // Get the text context around the cursor
    val document = editor.document
    val lineNumber = document.getLineNumber(offset)
    val lineStart = document.getLineStartOffset(lineNumber)
    val lineEnd = document.getLineEndOffset(lineNumber)
    val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))
    
    // Check for project() call pattern
    val projectMatch = GradleProjectPathUtils.PROJECT_CALL_PATTERN.find(lineText)
    if (projectMatch != null) {
      val projectPath = projectMatch.groupValues[2]
      return findBuildFileForPath(project, allProjects, projectPath)
    }
    
    // Check for type-safe accessor pattern (this is what we want to override)
    val accessorMatch = GradleProjectPathUtils.TYPE_SAFE_ACCESSOR_PATTERN.find(lineText)
    if (accessorMatch != null) {
      val typeSafeAccessor = accessorMatch.groupValues[1]
      val cleanAccessor = GradleProjectPathUtils.cleanTypeSafeAccessor(typeSafeAccessor)
      
      // Build accessor map to find the corresponding GradlePath
      val accessorMap = GradleProjectPathUtils.buildAccessorMap(allProjects)
      val gradlePath = accessorMap[cleanAccessor]
      
      if (gradlePath != null) {
        return findBuildFileForPath(project, allProjects, gradlePath.path)
      }
    }
    
    return null
  }
  
  private fun findBuildFileForPath(
    project: com.intellij.openapi.project.Project,
    allProjects: Set<com.fueledbycaffeine.spotlight.buildscript.GradlePath>,
    projectPath: String
  ): Array<PsiElement>? {
    val gradlePath = allProjects.find { it.path == projectPath } ?: return null
    
    if (!gradlePath.hasBuildFile) return null
    
    val buildFilePath = try {
      gradlePath.buildFilePath
    } catch (e: Exception) {
      logger.warn("Failed to get build file path for $projectPath", e)
      return null
    }
    
    val buildVirtualFile = VirtualFileManager.getInstance()
      .findFileByNioPath(buildFilePath) ?: return null
    
    val psiFile = PsiManager.getInstance(project).findFile(buildVirtualFile) ?: return null
    
    return arrayOf(psiFile)
  }
  
  companion object {
    private val logger = Logger.getInstance(BuildGradleProjectGotoHandler::class.java)
  }
}
