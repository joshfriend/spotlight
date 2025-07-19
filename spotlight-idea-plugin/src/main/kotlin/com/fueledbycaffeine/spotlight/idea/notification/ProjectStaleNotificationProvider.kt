package com.fueledbycaffeine.spotlight.idea.notification

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.idea.SpotlightBundle
import com.fueledbycaffeine.spotlight.idea.SpotlightProjectService
import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.fueledbycaffeine.spotlight.idea.utils.toGradlePath
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.function.Function
import javax.swing.JComponent


/**
 * Shows a banner in editors for files that belong to projects not currently indexed by Spotlight
 */
class ProjectStaleNotificationProvider(project: Project) : EditorNotificationProvider {

  private val spotlightService = project.service<SpotlightProjectService>()

  override fun collectNotificationData(
    project: Project,
    file: VirtualFile
  ): Function<in FileEditor, out JComponent?>? {
    // Check if gradle/ide-projects.txt exists - if not, don't show banners
    val ideProjects = spotlightService.ideProjects.value
    if (ideProjects.isEmpty()) {
      // No ide-projects.txt file or it's empty, so no banners needed
      return null
    }

    // Get the gradle path for this file
    val gradlePath = file.toGradlePath(project) ?: return null

    // Check if this file's project is in the all-projects list but not in ide-projects
    val allProjects = spotlightService.allProjects.value
    if (gradlePath !in allProjects || gradlePath in ideProjects) {
      // Either not a valid project or already indexed
      return null
    }

    return Function {
      createNotificationPanel(project, gradlePath)
    }
  }


  private fun createNotificationPanel(project: Project, gradlePath: GradlePath): EditorNotificationPanel {
    val spotlightService = project.spotlightService
    val panel = EditorNotificationPanel()
    panel.text = SpotlightBundle.message("notification.project.not.indexed", gradlePath.path)

    panel.createActionLabel(SpotlightBundle.message("notification.action.add.addOnly")) {
      // Add the project to spotlight using the same logic as AddProjectToSpotlightAction
      spotlightService.addIdeProjects(listOf(gradlePath))

      // Refresh editor notifications to hide this banner after adding the project
      EditorNotifications.getInstance(project).updateAllNotifications()

      // Open the ide-projects
      project.spotlightService.openIdeProjectsInEditor()
    }

    panel.createActionLabel(SpotlightBundle.message("notification.action.add.addAndSync")) {
      spotlightService.addIdeProjects(listOf(gradlePath))

      // Trigger a fresh Gradle sync
      val importSpec = ImportSpecBuilder(project, GradleConstants.SYSTEM_ID).build()
      ExternalSystemUtil.refreshProject(project.basePath!!, importSpec)

      // Refresh editor notifications to hide this banner after adding the project
      EditorNotifications.getInstance(project).updateAllNotifications()
    }

    return panel
  }
}