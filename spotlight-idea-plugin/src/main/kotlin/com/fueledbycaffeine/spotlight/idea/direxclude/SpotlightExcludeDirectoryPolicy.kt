package com.fueledbycaffeine.spotlight.idea.direxclude

import com.fueledbycaffeine.spotlight.buildscript.minimize
import com.fueledbycaffeine.spotlight.idea.SpotlightProjectService
import com.fueledbycaffeine.spotlight.idea.gradle.SpotlightGradleProjectsService
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.vfs.VfsUtilCore
import kotlin.io.path.absolutePathString

/**
 * Directory exclude policy that operates in two modes:
 *
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
 * This allows the plugin to work immediately with file-based configuration, then switch to
 * using Gradle's authoritative knowledge of what's included once sync completes.
 */
class SpotlightExcludeDirectoryPolicy(private val project: Project) : DirectoryIndexExcludePolicy {
  override fun getExcludeUrlsForProject(): Array<String> {
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
}
