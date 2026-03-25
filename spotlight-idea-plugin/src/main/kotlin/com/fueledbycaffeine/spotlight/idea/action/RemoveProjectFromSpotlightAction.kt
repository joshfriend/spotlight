@file:Suppress("UnstableApiUsage")

package com.fueledbycaffeine.spotlight.idea.action

import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.IDE_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.idea.SpotlightBundle
import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.fueledbycaffeine.spotlight.idea.utils.gradlePathsSelected
import com.fueledbycaffeine.spotlight.idea.utils.toSpotlightPattern
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger

/**
 * A right-click context action that removes selected projects from [IDE_PROJECTS_LOCATION]
 */
class RemoveProjectFromSpotlightAction : AnAction() {
  override fun actionPerformed(action: AnActionEvent) {
    val project = action.project ?: return
    val spotlightService = project.spotlightService
    val selectedPaths = action.gradlePathsSelected
      .filter { !it.isRootProject }

    val pathsToRemove = selectedPaths.mapNotNull { selectedPath ->
      val pattern = selectedPath.toSpotlightPattern()
      if (spotlightService.isInIdeProjectsFile(pattern)) {
        pattern
      } else {
        null
      }
    }

    if (pathsToRemove.isNotEmpty()) {
      logger.info("Remove projects from IDE Spotlight: ${pathsToRemove.joinToString { it.path }}")
      spotlightService.removeIdeProjects(pathsToRemove)

      NotificationGroupManager.getInstance()
        .getNotificationGroup("Spotlight")
        .createNotification(
          SpotlightBundle.message(
            "notification.removed.from.spotlight.paths",
            pathsToRemove.size,
            pathsToRemove.joinToString { it.path }
          ),
          NotificationType.INFORMATION
        )
        .notify(project)
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  /**
   * Only offer the action if the exact path/pattern for the selected item exists in [IDE_PROJECTS_LOCATION]
   */
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.apply {
      icon = AllIcons.General.Remove
      val spotlightService = e.project?.spotlightService
      isVisible = if (spotlightService != null) {
        e.gradlePathsSelected
          .filter { !it.isRootProject }
          .any { selectedPath ->
            spotlightService.isInIdeProjectsFile(selectedPath.toSpotlightPattern())
          }
      } else {
        false
      }
    }
  }

  private companion object {
    val logger = Logger.getInstance(RemoveProjectFromSpotlightAction::class.java)
  }
}
