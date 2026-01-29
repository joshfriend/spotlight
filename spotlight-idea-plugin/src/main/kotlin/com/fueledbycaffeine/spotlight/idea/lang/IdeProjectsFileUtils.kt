package com.fueledbycaffeine.spotlight.idea.lang

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.ALL_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.IDE_PROJECTS_LOCATION
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import java.nio.file.Path

/**
 * Utility functions for working with Spotlight project files (ide-projects.txt, all-projects.txt).
 */
object IdeProjectsFileUtils {
  /**
   * Checks if the given path is a Spotlight project file (ide-projects.txt or all-projects.txt).
   */
  fun isSpotlightProjectsFile(path: String): Boolean {
    return path.endsWith(IDE_PROJECTS_LOCATION) || path.endsWith(ALL_PROJECTS_LOCATION)
  }
  
  /**
   * Checks if the given file is a Spotlight project file (ide-projects.txt or all-projects.txt).
   */
  fun isSpotlightProjectsFile(file: PsiFile): Boolean {
    val virtualFile = file.virtualFile ?: return false
    return isSpotlightProjectsFile(virtualFile.path)
  }
  
  /**
   * Gets the Gradle path at the given offset in the editor, or null if:
   * - The line is a comment or blank
   * - The line contains glob patterns
   * - The path doesn't have a build file
   */
  fun getGradlePathAtOffset(editor: Editor, file: PsiFile, offset: Int): GradlePath? {
    // Get the line containing the cursor
    val document = editor.document
    val lineNumber = document.getLineNumber(offset)
    val lineStartOffset = document.getLineStartOffset(lineNumber)
    val lineEndOffset = document.getLineEndOffset(lineNumber)
    val lineText = document.getText(TextRange(lineStartOffset, lineEndOffset))
    val trimmedLine = lineText.trim()
    
    // Skip comments and blank lines
    if (trimmedLine.startsWith("#") || trimmedLine.isBlank()) return null
    
    // Skip glob patterns (can't navigate to a pattern)
    if (trimmedLine.contains('*') || trimmedLine.contains('?')) return null
    
    val project = file.project
    val rootDir = Path.of(project.basePath!!)
    val gradlePath = GradlePath(rootDir, trimmedLine)
    
    // Check if build file exists
    if (!gradlePath.hasBuildFile) return null
    
    return gradlePath
  }
}
