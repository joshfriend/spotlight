package com.fueledbycaffeine.spotlight.idea.gradle

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.ALL_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.buildscript.SpotlightRulesList.Companion.SPOTLIGHT_RULES_LOCATION
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Whether the Spotlight Gradle plugin is applied, as determined by the most recent Gradle sync.
 */
enum class SpotlightPluginStatus {
  /**
   * No sync has completed yet and Spotlight's config files (all-projects.txt / spotlight-rules.json)
   * exist, so it's likely set up but we can't yet confirm the plugin is applied to this build.
   */
  UNKNOWN,

  /** The most recent sync produced a Spotlight model, so the plugin is applied. */
  APPLIED,

  /** The most recent sync completed without a Spotlight model, so the plugin is not applied. */
  NOT_APPLIED,
}

/**
 * Holds the Spotlight state captured from the most recent Gradle sync and the logic that decides
 * whether the IDE needs to re-sync or show banners.
 *
 * This is intentionally free of IntelliJ platform types (it only needs the project root directory)
 * so it can be unit-tested without the IDE test fixtures. [SpotlightGradleProjectsService] is a thin
 * project-service wrapper around it.
 */
class SpotlightSyncState(private val rootDir: Path) {
  // We only use this to read raw paths, so the allProjects lambda is not needed
  private val ideProjectsList = SpotlightProjectList.ideProjects(rootDir) { emptySet() }
  private val rulesPath = rootDir.resolve(SPOTLIGHT_RULES_LOCATION)
  private val allProjectsPath = rootDir.resolve(ALL_PROJECTS_LOCATION)

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
   * The plugin status established by the most recent sync. [SpotlightPluginStatus.UNKNOWN] until a
   * sync completes; [pluginStatus] refines this with a file-based guess before the first sync.
   */
  @Volatile
  private var _syncedStatus: SpotlightPluginStatus = SpotlightPluginStatus.UNKNOWN

  /**
   * Projects that were INCLUDED in the most recent Gradle sync.
   * These are the projects that should be indexed in the IDE.
   * Empty set indicates no sync has completed yet.
   */
  val includedProjects: StateFlow<Set<GradlePath>> = _includedProjects.asStateFlow()

  /**
   * Update the included projects from Gradle sync.
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
    _syncedStatus = SpotlightPluginStatus.APPLIED

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
   * Whether the Spotlight Gradle plugin is applied.
   *
   * A completed sync is authoritative. Before the first sync we fall back to a best-effort guess
   * from the presence of Spotlight's config files: if none exist, Spotlight isn't set up in this
   * project at all so it can't be applied ([SpotlightPluginStatus.NOT_APPLIED]); otherwise we
   * genuinely can't tell yet ([SpotlightPluginStatus.UNKNOWN]).
   */
  val pluginStatus: SpotlightPluginStatus
    get() = when (_syncedStatus) {
      SpotlightPluginStatus.APPLIED, SpotlightPluginStatus.NOT_APPLIED -> _syncedStatus
      SpotlightPluginStatus.UNKNOWN ->
        if (isSpotlightConfigured) SpotlightPluginStatus.UNKNOWN else SpotlightPluginStatus.NOT_APPLIED
    }

  // Spotlight's committed config files; absence of both means it isn't set up in this project.
  private val isSpotlightConfigured: Boolean
    get() = allProjectsPath.exists() || rulesPath.exists()

  /**
   * Record that a sync completed without a Spotlight model, meaning the Spotlight Gradle
   * plugin is not applied to this project. Clears any stale included-projects state from a
   * previous sync so banners and exclusions don't act on outdated data.
   */
  fun markSpotlightNotApplied() {
    _includedProjects.value = emptySet()
    _syncedRawPaths.value = emptySet()
    _syncedRulesContent = null
    _syncedStatus = SpotlightPluginStatus.NOT_APPLIED
  }

  /**
   * Check if the current ide-projects.txt or spotlight-rules.json has meaningful changes
   * compared to what was synced, or if no sync has ever completed.
   * Returns true if sync is needed.
   */
  fun isSyncStale(): Boolean {
    // The Spotlight Gradle plugin isn't applied (either a sync proved it, or there are no config
    // files), so there's nothing to sync. Don't show banners even if a stale ide-projects.txt is
    // lying around.
    if (pluginStatus == SpotlightPluginStatus.NOT_APPLIED) return false

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

  private fun readCurrentRawPaths(): Set<String> {
    return ideProjectsList.readRawPaths(includeComments = false)
      .map { it.trim() }
      .toSet()
  }

  private fun readCurrentRulesContent(): String? {
    return if (rulesPath.exists()) {
      rulesPath.readText()
    } else {
      null
    }
  }

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

  private fun matchesGlob(path: String, pattern: String): Boolean {
    return try {
      val pathMatcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
      val pathToMatch = FileSystems.getDefault().getPath(path)
      pathMatcher.matches(pathToMatch)
    } catch (e: Exception) {
      false
    }
  }
}
