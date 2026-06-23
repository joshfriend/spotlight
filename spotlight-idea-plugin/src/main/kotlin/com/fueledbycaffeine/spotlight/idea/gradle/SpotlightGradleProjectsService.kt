package com.fueledbycaffeine.spotlight.idea.gradle

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path

/**
 * Service that stores the list of Gradle projects INCLUDED in the current build
 * (i.e., what was actually loaded during the most recent Gradle sync).
 * 
 * These are the projects that should be indexed in the IDE. After Gradle sync,
 * this provides the authoritative source for what's included, and the directory
 * exclude policy can use this to exclude everything else.
 */
@Service(Service.Level.PROJECT)
class SpotlightGradleProjectsService(
  project: Project,
  scope: CoroutineScope,
) : Disposable {
  private val state = SpotlightSyncState(Path.of(project.basePath!!))

  /**
   * Projects that were INCLUDED in the most recent Gradle sync.
   * These are the projects that should be indexed in the IDE.
   * Empty set indicates no sync has completed yet.
   */
  val includedProjects: StateFlow<Set<GradlePath>> get() = state.includedProjects

  /**
   * Update the included projects from Gradle sync.
   * Called by [SpotlightModelDataService] during project import.
   */
  fun updateProjects(projectPaths: Set<String>) = state.updateProjects(projectPaths)

  /**
   * Check if Gradle sync has completed and we know which projects are included.
   */
  val hasSyncedProjects: Boolean get() = state.hasSyncedProjects

  /**
   * Check if at least one successful sync with Spotlight model data has completed.
   */
  val hasEverSynced: Boolean get() = state.hasEverSynced

  /**
   * Whether the Spotlight Gradle plugin was applied as of the most recent sync.
   * Returns [SpotlightPluginStatus.UNKNOWN] if no sync has completed yet.
   */
  val pluginStatus: SpotlightPluginStatus get() = state.pluginStatus

  /**
   * Record that a sync completed without a Spotlight model, meaning the Spotlight Gradle
   * plugin is not applied to this project.
   */
  fun markSpotlightNotApplied() = state.markSpotlightNotApplied()

  /**
   * Check if the current ide-projects.txt or spotlight-rules.json has meaningful changes
   * compared to what was synced, or if no sync has ever completed.
   */
  fun isSyncStale(): Boolean = state.isSyncStale()

  /**
   * Check if spotlight-rules.json has changed since the last sync.
   */
  fun haveRulesChanged(): Boolean = state.haveRulesChanged()

  /**
   * Returns the set of new paths/patterns in ide-projects.txt that aren't covered by the synced state.
   */
  fun getUnsyncedPaths(): Set<String> = state.getUnsyncedPaths()

  override fun dispose() {}

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SpotlightGradleProjectsService = project.service()
  }
}
