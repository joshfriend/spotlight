package com.fueledbycaffeine.spotlight.idea.lang

import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.ALL_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.IDE_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.idea.SpotlightBundle
import com.fueledbycaffeine.spotlight.idea.gradle.GradleProjectPathUtils
import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware

/**
 * An action that removes all invalid paths from ide-projects.txt or all-projects.txt.
 * Bound to the "Optimize Imports" keyboard shortcut for convenience.
 * Modifies the document directly to avoid VFS sync issues.
 */
class RemoveAllInvalidPathsAction : AnAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  
  override fun update(e: AnActionEvent) {
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    val isSpotlightProjectFile = file != null && 
      (file.path.endsWith(IDE_PROJECTS_LOCATION) || file.path.endsWith(ALL_PROJECTS_LOCATION))
    
    // Only enabled in ide-projects.txt or all-projects.txt
    e.presentation.isEnabled = isSpotlightProjectFile
    e.presentation.text = SpotlightBundle.message("action.remove.all.invalid.paths")
    e.presentation.description = SpotlightBundle.message("action.remove.all.invalid.paths.description")
  }
  
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val spotlightService = project.spotlightService
    val allProjects = spotlightService.allProjects.value
    
    // Read the current file to get all paths
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return
    val text = document.text
    val lines = text.lines()
    
    val invalidPaths = mutableSetOf<String>()
    
    for (line in lines) {
      val trimmed = line.trim()
      
      // Skip comments and blank lines
      if (trimmed.startsWith("#") || trimmed.isBlank()) {
        continue
      }
      
      // Check if the path is valid
      if (!GradleProjectPathUtils.isValidIdeProjectPath(trimmed, allProjects)) {
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
    
    // Filter out invalid paths and rebuild the document
    val newLines = lines.filter { line ->
      val trimmed = line.trim()
      trimmed !in invalidPaths
    }
    val newText = newLines.joinToString("\n")
    
    // Modify document directly to avoid VFS sync issues
    WriteCommandAction.runWriteCommandAction(project) {
      document.setText(newText)
    }
    
    NotificationGroupManager.getInstance()
      .getNotificationGroup("Spotlight")
      .createNotification(
        SpotlightBundle.message("notification.removed.paths", invalidPaths.size),
        NotificationType.INFORMATION
      )
      .notify(project)
  }
}
