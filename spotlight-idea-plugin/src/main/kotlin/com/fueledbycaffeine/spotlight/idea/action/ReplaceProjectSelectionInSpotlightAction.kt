@file:Suppress("UnstableApiUsage")

package com.fueledbycaffeine.spotlight.idea.action

import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.fueledbycaffeine.spotlight.idea.utils.resolveSpotlightPaths
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAwareAction

/**
 * Replaces the current Spotlight selection with the selected Gradle projects.
 */
class ReplaceProjectSelectionInSpotlightAction : DumbAwareAction() {
  override fun actionPerformed(action: AnActionEvent) {
    val projectService = action.project?.spotlightService ?: return
    val replacementPaths = action.resolveSpotlightPaths()

    if (replacementPaths.isNotEmpty()) {
      logger.info("Replace IDE Spotlight projects with: ${replacementPaths.joinToString { it.path }}")
      projectService.replaceIdeProjects(replacementPaths)
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.apply {
      icon = AllIcons.Actions.Refresh
      isVisible = true
      isEnabled = e.resolveSpotlightPaths().isNotEmpty()
    }
  }

  private companion object {
    val logger = Logger.getInstance(ReplaceProjectSelectionInSpotlightAction::class.java)
  }
}
