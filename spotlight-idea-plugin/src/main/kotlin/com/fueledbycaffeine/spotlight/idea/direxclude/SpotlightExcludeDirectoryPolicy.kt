package com.fueledbycaffeine.spotlight.idea.direxclude

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.minimize
import com.fueledbycaffeine.spotlight.idea.SpotlightProjectService
import com.fueledbycaffeine.spotlight.idea.gradle.SpotlightGradleProjectsService
import com.fueledbycaffeine.spotlight.idea.settings.ExclusionPolicyMode
import com.fueledbycaffeine.spotlight.idea.settings.SpotlightSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.vfs.VfsUtilCore
import kotlin.io.path.absolutePathString

/**
 * Directory exclude policy with configurable behavior based on user settings:
 *
 * ## Mode: ACTIVE_PROJECTS_ONLY (default)
 * 1. **Pre-Gradle sync**:
 *    - All projects from `all-projects.txt`
 *    - IDE projects from `ide-projects.txt`
 *    - Exclude = all - ide
 *
 * 2. **Post-Gradle sync**:
 *    - All projects from `all-projects.txt` (still authoritative for what exists)
 *    - IDE projects from Gradle sync (what was actually included in the build)
 *    - Exclude = all - gradle-included
 *
 * ## Mode: ALL_PROJECTS_EXCLUDE_BUILD
 * All project sources are indexed, but build/ directories from unloaded projects
 * are excluded to reduce indexing overhead.
 *
 * ## Mode: DISABLED
 * No directories are excluded by Spotlight. Normal IDE indexing behavior applies.
 *
 * This allows the plugin to work immediately with file-based configuration, then switch to
 * using Gradle's authoritative knowledge of what's included once sync completes.
 */
class SpotlightExcludeDirectoryPolicy(private val project: Project) : DirectoryIndexExcludePolicy {

  companion object {
    /** Common build output directory names to exclude when in ALL_PROJECTS_EXCLUDE_BUILD mode */
    private val BUILD_DIRECTORIES = listOf("build", ".gradle")
  }

  override fun getExcludeUrlsForProject(): Array<String> {
    val settings = SpotlightSettings.getInstance()

    return when (settings.exclusionPolicyMode) {
      ExclusionPolicyMode.DISABLED -> emptyArray()
      ExclusionPolicyMode.ALL_PROJECTS_EXCLUDE_BUILD -> getExcludedBuildDirectories()
      ExclusionPolicyMode.ACTIVE_PROJECTS_ONLY -> getExcludedProjectDirectories()
    }
  }

  /**
   * Returns URLs for build directories of unloaded projects only.
   * Source files remain indexed but build outputs are excluded.
   */
  private fun getExcludedBuildDirectories(): Array<String> {
    val service = project.service<SpotlightProjectService>()
    val gradleProjectsService = project.service<SpotlightGradleProjectsService>()

    val allProjects = service.allProjects.value
    if (allProjects.isEmpty()) return emptyArray()

    val indexedProjects = if (gradleProjectsService.hasSyncedProjects) {
      gradleProjectsService.includedProjects.value
    } else {
      service.ideProjects.value
    }

    // Only exclude build directories from unloaded projects
    val unloadedProjects = (allProjects - indexedProjects).minimize()

    return unloadedProjects
      .flatMap { project -> getBuildDirectoryUrls(project) }
      .toTypedArray()
  }

  /**
   * Returns URLs for entire project directories of unloaded projects.
   * This is the default behavior that completely excludes unloaded projects.
   */
  private fun getExcludedProjectDirectories(): Array<String> {
    val service = project.service<SpotlightProjectService>()
    val gradleProjectsService = project.service<SpotlightGradleProjectsService>()

    // all-projects.txt is always the authoritative source for ALL projects
    val allProjects = service.allProjects.value
    if (allProjects.isEmpty()) return emptyArray()

    // Determine which projects should be indexed
    val indexedProjects = if (gradleProjectsService.hasSyncedProjects) {
      // Post-sync mode: Use what Gradle actually included in the build
      gradleProjectsService.includedProjects.value
    } else {
      // Pre-sync mode: Use file-based ide-projects.txt
      service.ideProjects.value
    }

    if (indexedProjects.isEmpty()) return emptyArray()

    // Non-indexed projects are allProjects - indexedProjects
    val excludedProjects = (allProjects - indexedProjects).minimize()

    return excludedProjects
      .map {
        val path = it.projectDir.absolutePathString()
        VfsUtilCore.pathToUrl(path)
      }
      .toTypedArray()
  }

  private fun getBuildDirectoryUrls(gradlePath: GradlePath): List<String> {
    val projectDir = gradlePath.projectDir
    return BUILD_DIRECTORIES.map { buildDir ->
      VfsUtilCore.pathToUrl(projectDir.resolve(buildDir).absolutePathString())
    }
  }
}
