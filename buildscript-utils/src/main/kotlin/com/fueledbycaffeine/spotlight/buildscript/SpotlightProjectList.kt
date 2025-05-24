package com.fueledbycaffeine.spotlight.buildscript

import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.notExists
import kotlin.io.path.readLines
import kotlin.io.path.writeText

public class SpotlightProjectList internal constructor(private val buildRoot: Path, private val projectList: Path) {
  public companion object {
    private const val COMMENT_CHAR = "#"
    public const val ALL_PROJECTS_LOCATION: String = "gradle/all-projects.txt"
    public const val IDE_PROJECTS_LOCATION: String = "gradle/ide-projects.txt"

    @JvmStatic
    public fun allProjects(buildRoot: Path): SpotlightProjectList =
      SpotlightProjectList(buildRoot, buildRoot.resolve(ALL_PROJECTS_LOCATION))

    @JvmStatic
    public fun ideProjects(buildRoot: Path): SpotlightProjectList =
      SpotlightProjectList(buildRoot, buildRoot.resolve(IDE_PROJECTS_LOCATION))
  }

  public fun read(): Set<GradlePath> {
    if (projectList.notExists()) return emptySet()

    return projectList.readLines()
      .filterNot { line -> line.trim().startsWith(COMMENT_CHAR) || line.isBlank() }
      .map { GradlePath(buildRoot, it) }
      .toSet()
  }

  public infix fun contains(path: GradlePath): Boolean = read().contains(path)

  public fun add(paths: Iterable<GradlePath>) {
    if (projectList.notExists()) projectList.createFile()
    projectList.writeText((read() + paths).joinToString("\n") { it.path })
  }

  public fun remove(paths: Iterable<GradlePath>) {
    if (projectList.notExists()) return
    projectList.writeText((read().filter { it !in paths }).joinToString("\n") { it.path })
  }
}
