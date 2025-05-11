package com.fueledbycaffeine.bettersettings.utils

import com.fueledbycaffeine.bettersettings.graph.GradlePath
import java.io.File
import java.nio.file.Path
import kotlin.io.path.readLines

private const val COMMENT_CHAR = "#"

internal object ProjectListReader {
  fun read(buildRoot: Path, projectList: Path): List<GradlePath> {
    return projectList.readLines()
      .filter { line -> !line.startsWith(COMMENT_CHAR) }
      .filter { line -> !line.isBlank() }
      .map { GradlePath(buildRoot, it) }
      .distinct()
  }
}

internal fun File.readProjectList(projectList: Path): List<GradlePath> {
  return this.toPath().readProjectList(projectList)
}

internal fun Path.readProjectList(projectList: Path): List<GradlePath> {
  return ProjectListReader.read(this, projectList)
}