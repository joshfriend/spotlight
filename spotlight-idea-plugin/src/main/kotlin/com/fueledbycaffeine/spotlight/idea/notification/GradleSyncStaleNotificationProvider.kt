package com.fueledbycaffeine.spotlight.idea.notification

import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.IDE_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.buildscript.SpotlightRulesList.Companion.SPOTLIGHT_RULES_LOCATION
import com.fueledbycaffeine.spotlight.idea.SpotlightBundle
import com.fueledbycaffeine.spotlight.idea.gradle.GradleSyncStatusService
import com.fueledbycaffeine.spotlight.idea.gradle.GradleSystemUtils
import com.fueledbycaffeine.spotlight.idea.gradle.SpotlightGradleProjectsService
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.function.Function
import javax.swing.JComponent


/**
 * Shows a banner in the ide-projects.txt or spotlight-rules.json editor when:
 * - No Gradle sync has completed yet (initial sync needed)
 * - The file has changed since the last sync (new paths not covered by existing patterns, or rules changed)
 * 
 * Hides the banner while a Gradle sync is in progress.
 */
class GradleSyncStaleNotificationProvider(project: Project) : EditorNotificationProvider, DumbAware {

  private val gradleProjectsService = project.service<SpotlightGradleProjectsService>()
  private val syncStatusService = project.service<GradleSyncStatusService>()

  override fun collectNotificationData(
    project: Project,
    file: VirtualFile
  ): Function<in FileEditor, out JComponent?>? {
    // Only show on ide-projects.txt or spotlight-rules.json
    val isIdeProjects = file.path.endsWith(IDE_PROJECTS_LOCATION)
    val isRules = file.path.endsWith(SPOTLIGHT_RULES_LOCATION)
    if (!isIdeProjects && !isRules) {
      return null
    }
    
    // Hide while sync is in progress
    if (syncStatusService.isSyncInProgress) {
      return null
    }

    // Check if sync is stale (includes check for never having synced)
    if (!gradleProjectsService.isSyncStale()) {
      return null
    }

    return Function {
      createNotificationPanel(project)
    }
  }

  private fun createNotificationPanel(project: Project): EditorNotificationPanel {
    val panel = EditorNotificationPanel()
    
    val hasEverSynced = gradleProjectsService.hasEverSynced
    val message = if (!hasEverSynced) {
      // No sync has ever completed - show initial sync needed message
      SpotlightBundle.message("notification.sync.needed")
    } else if (gradleProjectsService.haveRulesChanged()) {
      // Rules have changed
      SpotlightBundle.message("notification.sync.stale.rules")
    } else {
      // Sync is stale due to ide-projects.txt changes - show details about what changed
      val unsyncedPaths = gradleProjectsService.getUnsyncedPaths()
      if (unsyncedPaths.size == 1) {
        SpotlightBundle.message("notification.sync.stale.single", unsyncedPaths.first())
      } else {
        SpotlightBundle.message("notification.sync.stale.multiple", unsyncedPaths.size)
      }
    }
    panel.text = message

    panel.createActionLabel(SpotlightBundle.message("notification.action.sync")) {
      GradleSystemUtils.sync(project)
    }

    return panel
  }
}
