@file:Suppress("UnstableApiUsage")

package com.fueledbycaffeine.spotlight.idea.gradle

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * Listens for Gradle sync task start/end events and updates [GradleSyncStatusService].
 * Registered as an extension point in plugin.xml.
 */
class GradleSyncStatusListener : ExternalSystemTaskNotificationListener {
  override fun onStart(projectPath: String, id: ExternalSystemTaskId) {
    if (!id.isGradleResolveProjectTask) return
    val project = id.findProject() ?: return
    GradleSyncStatusService.getInstance(project).setSyncInProgress(true)
  }

  override fun onSuccess(projectPath: String, id: ExternalSystemTaskId) {
    if (!id.isGradleResolveProjectTask) return
    val project = id.findProject() ?: return
    GradleSyncStatusService.getInstance(project).setSyncInProgress(false)
  }

  override fun onFailure(projectPath: String, id: ExternalSystemTaskId, e: Exception) {
    if (!id.isGradleResolveProjectTask) return
    val project = id.findProject() ?: return
    GradleSyncStatusService.getInstance(project).setSyncInProgress(false)
  }

  override fun onCancel(projectPath: String, id: ExternalSystemTaskId) {
    if (!id.isGradleResolveProjectTask) return
    val project = id.findProject() ?: return
    GradleSyncStatusService.getInstance(project).setSyncInProgress(false)
  }
}
