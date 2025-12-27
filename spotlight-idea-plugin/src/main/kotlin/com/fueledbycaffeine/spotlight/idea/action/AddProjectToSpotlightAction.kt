@file:Suppress("UnstableApiUsage")

package com.fueledbycaffeine.spotlight.idea.action

import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.ALL_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.IDE_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.fueledbycaffeine.spotlight.idea.utils.gradlePathsSelected
import com.fueledbycaffeine.spotlight.idea.utils.isWildcardPattern
import com.fueledbycaffeine.spotlight.idea.utils.toSpotlightPattern
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger


/**
 * A right-click context action that adds selected projects to [IDE_PROJECTS_LOCATION]
 */
class AddProjectToSpotlightAction : AnAction() {
  override fun actionPerformed(action: AnActionEvent) {
    val projectService = action.project?.spotlightService ?: return
    val allProjects = projectService.allProjects.value

    val pathsToAdd = action.gradlePathsSelected
      .filter { !it.isRootProject }
      .mapNotNull { selectedPath ->
        val pattern = selectedPath.toSpotlightPattern()
        when {
          // If directory contains child Gradle projects, use the wildcard pattern
          pattern.isWildcardPattern -> pattern
          // If this is a direct project in allProjects, add it as-is
          selectedPath in allProjects -> selectedPath
          // Otherwise, skip it
          else -> null
        }
      }

    if (pathsToAdd.isNotEmpty()) {
      logger.info("Add projects to IDE Spotlight: ${pathsToAdd.joinToString { it.path }}")
      projectService.addIdeProjects(pathsToAdd)
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
        val allProjects = projectService.allProjects.value
        e.gradlePathsSelected
          .filter { !it.isRootProject }
          .any { selectedPath ->
            val pattern = selectedPath.toSpotlightPattern()
            val isValidProject = selectedPath in allProjects
            (pattern.isWildcardPattern || isValidProject) && !projectService.isInIdeProjectsFile(pattern)
          }
      } else {
        false
      }
    }
  }

  private companion object {
    val logger = Logger.getInstance(AddProjectToSpotlightAction::class.java)
  }
}