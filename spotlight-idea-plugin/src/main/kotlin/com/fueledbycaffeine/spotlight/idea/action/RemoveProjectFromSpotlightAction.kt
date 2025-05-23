@file:Suppress("UnstableApiUsage")

package com.fueledbycaffeine.spotlight.idea.action

import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.IDE_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.idea.utils.gradlePathsSelected
import com.fueledbycaffeine.spotlight.idea.utils.spotlightIdeProjectList
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger

private val logger = Logger.getInstance(RemoveProjectFromSpotlightAction::class.java)

/**
 * A right-click context action that removes selected projects from [IDE_PROJECTS_LOCATION]
 */
class RemoveProjectFromSpotlightAction : AnAction() {
  override fun actionPerformed(action: AnActionEvent) {
    val project = action.project ?: return
    val loadedProjects = project.spotlightIdeProjectList.read()
    val pathsInBuild = action.gradlePathsSelected.intersect(loadedProjects)
    logger.info("Remove projects from IDE Spotlight: ${pathsInBuild.joinToString { it.path }}")
    project.spotlightIdeProjectList.remove(pathsInBuild)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  /**
   * Only offer the action if one of the selected items is a project enumerated in [IDE_PROJECTS_LOCATION]
   */
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.apply {
      icon = AllIcons.General.Remove
      val project = e.project
      isVisible = if (project != null) {
        val loadedProjects = project.spotlightIdeProjectList.read()
        e.gradlePathsSelected.any { it in loadedProjects }
      } else {
        false
      }
    }
  }
}