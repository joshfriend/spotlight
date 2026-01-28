package com.fueledbycaffeine.spotlight.idea.lang

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.IDE_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.idea.SpotlightBundle
import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import java.nio.file.FileSystems

/**
 * An action that removes all invalid paths from the ide-projects.txt file.
 * Bound to the "Optimize Imports" keyboard shortcut for convenience.
 */
class RemoveAllInvalidPathsAction : AnAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  
  override fun update(e: AnActionEvent) {
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    val isIdeProjectsFile = file != null && file.path.endsWith(IDE_PROJECTS_LOCATION)
    
    // Only enabled in ide-projects.txt, where it overrides Optimize Imports shortcut
    e.presentation.isEnabled = isIdeProjectsFile
    e.presentation.text = SpotlightBundle.message("action.remove.all.invalid.paths")
    e.presentation.description = SpotlightBundle.message("action.remove.all.invalid.paths.description")
  }
  
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val spotlightService = project.spotlightService
    val allProjects = spotlightService.allProjects.value
    val rootDir = java.nio.file.Path.of(project.basePath!!)
    
    // Read the current file to get all paths
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return
    val text = document.text
    val lines = text.lines()
    
    val invalidPaths = mutableListOf<String>()
    
    for (line in lines) {
      val trimmed = line.trim()
      
      // Skip comments and blank lines
      if (trimmed.startsWith("#") || trimmed.isBlank()) {
        continue
      }
      
      // Check if the path is valid
      if (!isValidPath(trimmed, allProjects)) {
        invalidPaths.add(trimmed)
      }
    }
    
    if (invalidPaths.isEmpty()) {
      NotificationGroupManager.getInstance()
        .getNotificationGroup("Spotlight")
        .createNotification(
          SpotlightBundle.message("notification.no.invalid.paths"),
          NotificationType.INFORMATION
        )
        .notify(project)
      return
    }
    
    // Save the document first to avoid VFS conflict when service writes to disk
    FileDocumentManager.getInstance().saveDocument(document)
    
    // Remove all invalid paths
    val gradlePaths = invalidPaths.map { GradlePath(rootDir, it) }
    spotlightService.removeIdeProjects(gradlePaths)
    
    // Refresh the file to reload the changes
    file.refresh(false, false)
    
    NotificationGroupManager.getInstance()
      .getNotificationGroup("Spotlight")
      .createNotification(
        SpotlightBundle.message("notification.removed.paths", invalidPaths.size),
        NotificationType.INFORMATION
      )
      .notify(project)
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
