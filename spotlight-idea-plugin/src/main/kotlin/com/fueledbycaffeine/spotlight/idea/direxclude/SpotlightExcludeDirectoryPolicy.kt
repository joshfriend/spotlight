package com.fueledbycaffeine.spotlight.idea.direxclude

import com.fueledbycaffeine.spotlight.buildscript.minimize
import com.fueledbycaffeine.spotlight.idea.SpotlightProjectService
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.vfs.VfsUtilCore
import kotlin.io.path.absolutePathString

class SpotlightExcludeDirectoryPolicy(private val project: Project) : DirectoryIndexExcludePolicy {
  override fun getExcludeUrlsForProject(): Array<String> {
    val service = project.serviceOrNull<SpotlightProjectService>() ?: return emptyArray()
    val allProjects = service.allProjects.value
    if (allProjects.isEmpty()) return emptyArray()
    val ideProjects = service.ideProjects.value
    if (ideProjects.isEmpty()) return emptyArray()

    // Non-indexed projects are allProjects - ideProjects
    val excludedProjects = (allProjects - ideProjects).minimize()

    return excludedProjects
      .map {
        val path = it.projectDir.absolutePathString()
        VfsUtilCore.pathToUrl(path)
      }
      .toTypedArray()
  }
}