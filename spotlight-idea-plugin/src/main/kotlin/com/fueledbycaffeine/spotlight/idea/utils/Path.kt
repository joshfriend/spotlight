package com.fueledbycaffeine.spotlight.idea.utils

import com.fueledbycaffeine.spotlight.buildscript.BUILDSCRIPTS
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.gradlePathRelativeTo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * Finds the [GradlePath] that contains this file by checking if the file's path starts with
 * any of the known project directories. Returns the most specific (longest) match.
 */
internal fun VirtualFile.findContainingProject(knownProjects: Set<GradlePath>): GradlePath? {
  return knownProjects
    .filter { path.startsWith(it.projectDir.toString()) }
    .maxByOrNull { it.projectDir.toString().length }
}

internal fun Path.gradlePathRelativeTo(ideaProject: Project): GradlePath {
  return this.gradlePathRelativeTo(Path.of(ideaProject.basePath!!))
}

/**
 * When a file is selected we can chop off most of the path to guess the gradle project directory.
 * Handles files in build/ or src/ directories, as well as build files at the project root.
 */
internal fun Path.dropBuildAndSrc(): Path {
  val pathString = absolutePathString()
  val trimmed = pathString.substringBefore("/build/").substringBefore("/src/")
  val result = Path.of(trimmed)

  // If the file itself is a build file, return its parent directory
  val fileName = result.fileName?.toString()
  return if (fileName in BUILDSCRIPTS) {
    result.parent ?: result
  } else {
    result
  }
}
