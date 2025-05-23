package com.fueledbycaffeine.spotlight.idea.utils

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.gradlePathRelativeTo
import com.intellij.openapi.project.Project
import java.nio.file.Path
import kotlin.io.path.absolutePathString

internal fun Path.gradlePathRelativeTo(ideaProject: Project): GradlePath {
  return this.gradlePathRelativeTo(Path.of(ideaProject.basePath!!))
}

/**
 * When a file is selected we can chop off most of the path to guess the gradle project directory
 */
internal fun Path.dropBuildAndSrc() = Path.of(absolutePathString().substringBefore("/build/").substringBefore("/src/"))