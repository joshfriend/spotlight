@file:Suppress("UnstableApiUsage")

package com.fueledbycaffeine.spotlight.idea.action

import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.ALL_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.IDE_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.idea.SpotlightBundle
import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.fueledbycaffeine.spotlight.idea.utils.resolveSpotlightPaths
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger


/**
 * A right-click context action that adds selected projects to [IDE_PROJECTS_LOCATION]
 */
class AddProjectToSpotlightAction : AnAction() {
  override fun actionPerformed(action: AnActionEvent) {
    val project = action.project ?: return
    val projectService = project.spotlightService
    val pathsToAdd = action.resolveSpotlightPaths()

    if (pathsToAdd.isNotEmpty()) {
      logger.info("Add projects to IDE Spotlight: ${pathsToAdd.joinToString { it.path }}")
      projectService.addIdeProjects(pathsToAdd)

      NotificationGroupManager.getInstance()
        .getNotificationGroup("Spotlight")
        .createNotification(
          SpotlightBundle.message(
            "notification.added.paths",
            pathsToAdd.size,
            pathsToAdd.joinToString { it.path }
          ),
          NotificationType.INFORMATION
        )
        .notify(project)
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  /**
   * Only offer the action if one of the selected items is a project enumerated in [ALL_PROJECTS_LOCATION]
   * or is a parent directory containing child Gradle projects, and the pattern that would be added
   * is not already in the file
   */
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.apply {
      icon = AllIcons.General.Add
      val projectService = e.project?.spotlightService
      isVisible = if (projectService != null) {
        e.resolveSpotlightPaths().any { !projectService.isInIdeProjectsFile(it) }
      } else {
        false
      }
    }
  }

  private companion object {
    val logger = Logger.getInstance(AddProjectToSpotlightAction::class.java)
  }
}
