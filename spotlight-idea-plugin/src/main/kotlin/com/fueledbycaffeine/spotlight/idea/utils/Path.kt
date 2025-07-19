package com.fueledbycaffeine.spotlight.idea.utils

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.gradlePathRelativeTo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import kotlin.io.path.absolutePathString

internal fun Path.gradlePathRelativeTo(ideaProject: Project): GradlePath {
  return this.gradlePathRelativeTo(Path.of(ideaProject.basePath!!))
}

/**
 * When a file is selected we can chop off most of the path to guess the gradle project directory
 */
internal fun Path.dropBuildAndSrc() = Path.of(absolutePathString().substringBefore("/build/").substringBefore("/src/"))

/**
 * Converts a VirtualFile to a GradlePath if it's part of a known Gradle project
 */
internal fun VirtualFile.toGradlePath(project: Project): GradlePath? {
  return try {
    val filePath = Path.of(path)
    val projectDir = filePath.dropBuildAndSrc()
    projectDir.gradlePathRelativeTo(project)
  } catch (_: Exception) {
    null
  }
}