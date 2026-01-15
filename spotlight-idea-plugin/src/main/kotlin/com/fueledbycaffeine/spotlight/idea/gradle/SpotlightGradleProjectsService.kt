package com.fueledbycaffeine.spotlight.idea.gradle

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
  private val rootDir = Path.of(project.basePath!!)

  private val _includedProjects = MutableStateFlow<Set<GradlePath>>(emptySet())

  /**
   * Projects that were INCLUDED in the most recent Gradle sync.
   * These are the projects that should be indexed in the IDE.
   * Empty set indicates no sync has completed yet.
   */
  val includedProjects: StateFlow<Set<GradlePath>> = _includedProjects.asStateFlow()

  /**
   * Update the included projects from Gradle sync.
   * Called by [SpotlightGradleProjectsService] during project import.
   */
  fun updateProjects(projectPaths: Set<String>) {
    val gradlePaths = projectPaths
      .map { GradlePath(rootDir, it) }
      // Filter results to match what spotlight parses from build files where intermediate and root
      // projects are omitted
      .filter { it.hasBuildFile && it.path != ":" }
      .toSet()
    _includedProjects.value = gradlePaths
  }

  /**
   * Check if Gradle sync has completed and we know which projects are included.
   * Returns true once at least one sync has completed.
   */
  val hasSyncedProjects: Boolean get() = _includedProjects.value.isNotEmpty()

  /**
   * Clear the synced projects (e.g., when build configuration changes).
   */
  fun clearProjects() {
    _includedProjects.value = emptySet()
  }

  override fun dispose() {}

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SpotlightGradleProjectsService = project.service()
  }
}
