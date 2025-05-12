package com.fueledbycaffeine.spotlight.utils

import org.gradle.api.file.RegularFile
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.Property
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

internal fun File.readProjectList(projectList: File): List<GradlePath> {
  return this.toPath().readProjectList(projectList.toPath())
}

internal fun Path.readProjectList(projectList: Path): List<GradlePath> {
  return ProjectListReader.read(this, projectList)
}

internal fun Settings.readProjectList(property: Property<RegularFile>): List<GradlePath> {
  val file = property.get().asFile
  return when {
    file.exists() -> rootDir.readProjectList(file)
    else -> emptyList()
  }
}