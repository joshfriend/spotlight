package com.fueledbycaffeine.spotlight.buildscript

import java.io.File
import java.nio.file.Path
import kotlin.io.path.readLines

private const val COMMENT_CHAR = "#"

public class ProjectListReader(private val buildRoot: Path, private val projectList: Path) {
  public fun read(): Set<GradlePath> {
    return projectList.readLines()
      .filter { line -> !line.startsWith(COMMENT_CHAR) }
      .filter { line -> !line.isBlank() }
      .map {
        GradlePath(buildRoot, it).apply {
          // Will throw FileNotFoundException if a buildscript for this project doesn't exist
          buildFilePath
        }
      }
      .toSet()
  }
}

public fun File.readProjectList(projectList: File): Set<GradlePath> {
  return this.toPath().readProjectList(projectList.toPath())
}

public fun Path.readProjectList(projectList: Path): Set<GradlePath> {
  return ProjectListReader(this, projectList).read()
}