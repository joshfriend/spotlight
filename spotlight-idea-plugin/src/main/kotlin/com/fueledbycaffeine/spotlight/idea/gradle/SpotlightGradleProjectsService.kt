package com.fueledbycaffeine.spotlight.idea.gradle

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import com.fueledbycaffeine.spotlight.buildscript.SpotlightRulesList.Companion.SPOTLIGHT_RULES_LOCATION
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

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
  // We only use this to read raw paths, so allProjects lambda is not needed
  private val ideProjectsList = SpotlightProjectList.ideProjects(rootDir) { emptySet() }
  private val rulesPath = rootDir.resolve(SPOTLIGHT_RULES_LOCATION)

  private val _includedProjects = MutableStateFlow<Set<GradlePath>>(emptySet())
  
  /**
   * The normalized set of raw paths/patterns from ide-projects.txt at the time of the last sync.
   * Used to detect meaningful changes that require a new sync.
   */
  private val _syncedRawPaths = MutableStateFlow<Set<String>>(emptySet())
  
  /**
   * The content of spotlight-rules.json at the time of the last sync.
   * Used to detect changes that require a new sync.
   */
  @Volatile
  private var _syncedRulesContent: String? = null
  
  /**
   * Whether at least one successful sync with Spotlight model data has completed.
   */
  @Volatile
  private var _hasEverSynced = false

  /**
   * Projects that were INCLUDED in the most recent Gradle sync.
   * These are the projects that should be indexed in the IDE.
   * Empty set indicates no sync has completed yet.
   */
  val includedProjects: StateFlow<Set<GradlePath>> = _includedProjects.asStateFlow()

  /**
   * Update the included projects from Gradle sync.
   * Called by [SpotlightModelDataService] during project import.
   * Also records the current ide-projects.txt and spotlight-rules.json as the "synced" state.
   */
  fun updateProjects(projectPaths: Set<String>) {
    val gradlePaths = projectPaths
      .map { GradlePath(rootDir, it) }
      // Filter results to match what spotlight parses from build files where intermediate and root
      // projects are omitted
      .filter { it.hasBuildFile && it.path != ":" }
      .toSet()
    _includedProjects.value = gradlePaths
    _hasEverSynced = true
    
    // Record the raw paths from ide-projects.txt at sync time
    _syncedRawPaths.value = readCurrentRawPaths()
    // Record the rules content at sync time
    _syncedRulesContent = readCurrentRulesContent()
  }

  /**
   * Check if Gradle sync has completed and we know which projects are included.
   * Returns true once at least one sync has completed.
   */
  val hasSyncedProjects: Boolean get() = _includedProjects.value.isNotEmpty()
  
  /**
   * Check if at least one successful sync with Spotlight model data has completed.
   */
  val hasEverSynced: Boolean get() = _hasEverSynced

  /**
   * Clear the synced projects (e.g., when build configuration changes).
   */
  fun clearProjects() {
    _includedProjects.value = emptySet()
    _syncedRawPaths.value = emptySet()
    _syncedRulesContent = null
  }

  /**
   * Check if the current ide-projects.txt or spotlight-rules.json has meaningful changes 
   * compared to what was synced, or if no sync has ever completed.
   * Returns true if sync is needed.
   */
  fun isSyncStale(): Boolean {
    // If no sync has ever completed, it's stale (needs initial sync)
    if (!_hasEverSynced) {
      // Only consider stale if there's content in ide-projects.txt
      return readCurrentRawPaths().isNotEmpty()
    }
    
    // Check if rules have changed
    if (haveRulesChanged()) return true
    
    // Check if ide-projects.txt has new paths
    val currentPaths = readCurrentRawPaths()
    val syncedPaths = _syncedRawPaths.value
    
    // Find paths that are in current but not in synced
    val newPaths = currentPaths - syncedPaths
    if (newPaths.isEmpty()) return false
    
    // Check if any new path is NOT covered by existing synced patterns
    return newPaths.any { newPath -> !isPathCoveredByPatterns(newPath, syncedPaths) }
  }
  
  /**
   * Check if spotlight-rules.json has changed since the last sync.
   */
  fun haveRulesChanged(): Boolean {
    val currentRules = readCurrentRulesContent()
    val syncedRules = _syncedRulesContent
    return currentRules != syncedRules
  }
  
  /**
   * Returns the set of new paths/patterns in ide-projects.txt that aren't covered by the synced state.
   */
  fun getUnsyncedPaths(): Set<String> {
    if (!hasSyncedProjects) return emptySet()
    
    val currentPaths = readCurrentRawPaths()
    val syncedPaths = _syncedRawPaths.value
    
    val newPaths = currentPaths - syncedPaths
    return newPaths.filter { newPath -> !isPathCoveredByPatterns(newPath, syncedPaths) }.toSet()
  }
  
  /**
   * Read the current raw paths from ide-projects.txt, normalized (trimmed, no comments, sorted).
   */
  private fun readCurrentRawPaths(): Set<String> {
    return ideProjectsList.readRawPaths(includeComments = false)
      .map { it.trim() }
      .toSet()
  }
  
  /**
   * Read the current content of spotlight-rules.json, or null if it doesn't exist.
   */
  private fun readCurrentRulesContent(): String? {
    return if (rulesPath.exists()) {
      rulesPath.readText()
    } else {
      null
    }
  }
  
  /**
   * Check if a path is covered by any glob pattern in the given set of patterns.
   */
  private fun isPathCoveredByPatterns(path: String, patterns: Set<String>): Boolean {
    // Direct paths are not covered by anything else (must be exact match)
    if (!path.contains('*') && !path.contains('?')) {
      // Check if any pattern in patterns covers this path
      return patterns.any { pattern ->
        if (pattern.contains('*') || pattern.contains('?')) {
          matchesGlob(path, pattern)
        } else {
          pattern == path
        }
      }
    }
    // New patterns are always considered "new" (even if they overlap with existing ones)
    return false
  }
  
  /**
   * Check if a path matches a glob pattern.
   */
  private fun matchesGlob(path: String, pattern: String): Boolean {
    return try {
      val pathMatcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
      val pathToMatch = FileSystems.getDefault().getPath(path)
      pathMatcher.matches(pathToMatch)
    } catch (e: Exception) {
      false
    }
  }

  override fun dispose() {}

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SpotlightGradleProjectsService = project.service()
  }
}
