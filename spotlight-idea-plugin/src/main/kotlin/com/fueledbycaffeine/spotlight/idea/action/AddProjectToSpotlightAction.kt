@file:Suppress("UnstableApiUsage")

package com.fueledbycaffeine.spotlight.idea.action

import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.ALL_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.IDE_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.fueledbycaffeine.spotlight.idea.utils.gradlePathsSelected
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger

private val logger = Logger.getInstance(AddProjectToSpotlightAction::class.java)

/**
 * A right-click context action that adds selected projects to [IDE_PROJECTS_LOCATION]
 */
class AddProjectToSpotlightAction : AnAction() {
  override fun actionPerformed(action: AnActionEvent) {
    val projectService = action.project?.spotlightService ?: return
    val allProjects = projectService.allProjects.value
    val pathsInBuild = action.gradlePathsSelected.intersect(allProjects)
    logger.info("Add projects to IDE Spotlight: ${pathsInBuild.joinToString { it.path }}")
    projectService.addIdeProjects(pathsInBuild)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  /**
   * Only offer the action if one of the selected items is a project enumerated in [ALL_PROJECTS_LOCATION]
   */
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.apply {
      icon = AllIcons.General.Add
      val projectService = e.project?.spotlightService
      isVisible = if (projectService != null) {
        val allProjects = projectService.allProjects.value
        val loadedProjects = projectService.ideProjects.value
        val selected = e.gradlePathsSelected
        selected.any { it in allProjects } && selected.any { it !in loadedProjects }
      } else {
        false
      }
    }
  }
}