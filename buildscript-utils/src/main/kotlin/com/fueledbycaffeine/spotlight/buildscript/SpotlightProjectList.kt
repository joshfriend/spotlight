package com.fueledbycaffeine.spotlight.buildscript

import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.notExists
import kotlin.io.path.readLines
import kotlin.io.path.writeText

public sealed interface SpotlightProjectList {
  public val buildRoot: Path
  public val projectList: Path

  public companion object {
    private const val COMMENT_CHAR = "#"
    public const val ALL_PROJECTS_LOCATION: String = "gradle/all-projects.txt"
    public const val IDE_PROJECTS_LOCATION: String = "gradle/ide-projects.txt"

    @JvmStatic
    public fun allProjects(buildRoot: Path): AllProjects =
      AllProjects(buildRoot, buildRoot.resolve(ALL_PROJECTS_LOCATION))

    @JvmStatic
    public fun ideProjects(buildRoot: Path, allProjects: (() -> Set<GradlePath>)? = null): IdeProjects =
      IdeProjects(buildRoot, buildRoot.resolve(IDE_PROJECTS_LOCATION), allProjects)
  }

  public fun read(): Set<GradlePath>

  /**
   * Check if an exact path or pattern exists in the file.
   * Returns true only if the exact path is present, not if it's covered by another pattern.
   */
  public operator fun contains(path: GradlePath): Boolean {
    val existingPaths = readRawPaths(includeComments = false)
    return existingPaths.any { it.trim() == path.path }
  }

  public fun ensureFileExists() {
    if (projectList.notExists()) projectList.createFile()
  }

  public fun readRawPaths(includeComments: Boolean): List<String> {
    if (projectList.notExists()) return emptyList()
    return projectList.readLines()
      .filterNot { line ->
        (!includeComments && line.trim().startsWith(COMMENT_CHAR)) || line.isBlank()
      }
  }
}

public class AllProjects internal constructor(
  override val buildRoot: Path,
  override val projectList: Path
) : SpotlightProjectList {

  override fun read(): Set<GradlePath> {
    return readRawPaths(includeComments = false)
      .map { GradlePath(buildRoot, it) }
      .toSet()
  }
}

public class IdeProjects internal constructor(
  override val buildRoot: Path,
  override val projectList: Path,
  private val allProjects: (() -> Set<GradlePath>)? = null
) : SpotlightProjectList {

  private companion object {
    private const val STAR_CHAR = '*'
    private const val QUESTION_CHAR = '?'

    private fun String.containsGlobChar(): Boolean {
      for (char in this) {
        if (char == STAR_CHAR || char == QUESTION_CHAR) {
          return true
        }
      }
      return false
    }
  }

  /**
   * Add paths to the file. Only prevents exact duplicates.
   * Allows overlapping patterns (e.g., both :advertising:** and :advertising:backend can coexist).
   */
  public fun add(paths: Iterable<GradlePath>) {
    ensureFileExists()
    val existingRawPaths = readRawPaths(includeComments = true)
    val existingNonCommentPaths = readRawPaths(includeComments = false)
      .map { it.trim() }
      .toSet()

    // Only filter out exact duplicates
    val pathsToAdd = paths.filter { it.path !in existingNonCommentPaths }

    if (pathsToAdd.isNotEmpty()) {
      projectList.writeText((existingRawPaths + pathsToAdd.map { it.path }).joinToString("\n"))
    }
  }

  /**
   * Remove paths from the file. Only removes exact matches.
   */
  public fun remove(paths: Iterable<GradlePath>) {
    if (projectList.notExists()) return
    val pathsToRemove = paths.map { it.path }.toSet()
    val rawPaths = readRawPaths(includeComments = true)

    // Remove lines that exactly match the paths to remove
    val filteredPaths = rawPaths.filterNot { rawPath ->
      val trimmed = rawPath.trim()
      // Keep comments and blank lines
      if (trimmed.startsWith("#") || trimmed.isBlank()) {
        false
      } else {
        // Remove if exact match
        trimmed in pathsToRemove
      }
    }

    projectList.writeText(filteredPaths.joinToString("\n"))
  }

  override fun read(): Set<GradlePath> {
    val rawPaths = readRawPaths(includeComments = false)

    return if (allProjects != null) {
      // Apply filtering when allProjects is provided
      rawPaths.flatMap { path ->
        when {
          path.containsGlobChar() -> {
            // Handle glob patterns like :libraries:*
            val globPattern = "glob:$path"
            val pathMatcher = FileSystems.getDefault().getPathMatcher(globPattern)
            allProjects().filter { gradlePath ->
              // Convert gradle path to a Path for matching
              val pathToMatch = FileSystems.getDefault().getPath(gradlePath.path)
              pathMatcher.matches(pathToMatch)
            }
          }

          else -> {
            // Direct project reference
            // Assume it's real and don't cross-reference all-projects.txt as this
            // lets us avoid it getting pulled in to CC
            val gradlePath = GradlePath(buildRoot, path)
            listOf(gradlePath)
          }
        }
      }.toSet()
    } else {
      // Default behavior when no allProjects provided
      rawPaths.map { GradlePath(buildRoot, it) }.toSet()
    }
  }
}
