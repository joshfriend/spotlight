package com.fueledbycaffeine.spotlight.idea.gradle

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

/**
 * Service that tracks whether a Gradle sync is currently in progress.
 * Used to hide sync-related UI elements while sync is running.
 * 
 * Sync status is updated by [GradleSyncStatusListener] which is registered as an extension point.
 */
@Service(Service.Level.PROJECT)
class GradleSyncStatusService {

  @Volatile
  private var _isSyncInProgress = false

  /**
   * Whether a Gradle sync is currently in progress for this project.
   */
  val isSyncInProgress: Boolean get() = _isSyncInProgress

  internal fun setSyncInProgress(inProgress: Boolean) {
    _isSyncInProgress = inProgress
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): GradleSyncStatusService = project.service()
  }
}
