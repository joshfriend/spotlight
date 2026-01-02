package com.fueledbycaffeine.spotlight.tasks

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.SETTINGS_SCRIPT
import com.fueledbycaffeine.spotlight.buildscript.SETTINGS_SCRIPT_KOTLIN
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import com.fueledbycaffeine.spotlight.buildscript.graph.BreadthFirstSearch
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText

/**
 * Task to automatically fix issues in the all-projects.txt file by:
 * - Migrating any include statements from settings.gradle(.kts) to all-projects.txt
 * - Removing invalid projects (those without build files)
 * - Adding missing projects discovered via BFS
 * - Sorting the result
 */
@DisableCachingByDefault(because = "Has no outputs")
@UntrackedTask(because = "Has no outputs")
public abstract class FixSpotlightProjectListTask : DefaultTask() {
  public companion object {
    public const val NAME: String = "fixAllProjectsList"
  }

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  internal abstract val projectsFile: RegularFileProperty

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  internal abstract val rootDirectory: DirectoryProperty

  @TaskAction
  internal fun action() {
    val rootDir = rootDirectory.asFile.get().toPath()
    val migratedProjects = migrateIncludeStatementsFromSettings(rootDir)
    val allProjects = SpotlightProjectList.allProjects(rootDir).read() + migratedProjects
    val validProjects = removeInvalidProjects(allProjects)
    val finalProjects = addMissingProjects(validProjects)
    writeSortedProjects(finalProjects)
    logResults(
      allProjects.size - validProjects.size,
      finalProjects.size - validProjects.size + migratedProjects.size,
    )
  }

  /**
   * Migrates include statements from settings.gradle(.kts) to all-projects.txt.
   * Returns the list of migrated project paths and removes the include lines from
   * settings.gradle(.kts).
   */
  private fun migrateIncludeStatementsFromSettings(rootDir: Path): Set<GradlePath> {
    val settingsFile = rootDir.resolve(SETTINGS_SCRIPT).takeIf { it.exists() }
      ?: rootDir.resolve(SETTINGS_SCRIPT_KOTLIN).takeIf { it.exists() }
      ?: return emptySet()

    // Split out the include lines from the rest of the settings file
    val (includeLines, cleanedLines) = settingsFile.readLines()
      .partition { line -> INCLUDE_PROJECT_PATH.containsMatchIn(line) }

    // Extract project paths from removed include lines
    val migratedProjects = includeLines.flatMap { line ->
      INCLUDE_PROJECT_PATH.findAll(line)
        .map { match -> GradlePath(rootDir, match.groupValues[1]) }
    }.toSet()

    // Rewrite settings.gradle(.kts) with include lines removed
    settingsFile.writeText(cleanedLines.joinToString("\n"))

    return migratedProjects
  }

  private fun removeInvalidProjects(allProjects: Set<GradlePath>): Set<GradlePath> {
    val validProjects = allProjects.filter { it.hasBuildFile }.toSet()
    return validProjects
  }

  private fun addMissingProjects(validProjects: Set<GradlePath>): Set<GradlePath> {
    val discoveredProjects = BreadthFirstSearch.flatten(validProjects)
    return validProjects + discoveredProjects
  }

  private fun writeSortedProjects(projects: Set<GradlePath>) {
    val sortedProjectPaths = projects.map { it.path }.sorted()
    projectsFile.asFile.get().writeText(sortedProjectPaths.joinToString("\n"))
  }

  private fun logResults(removedCount: Int, addedCount: Int) {
    if (removedCount > 0 || addedCount > 0) {
      logger.lifecycle(
        "Updated ${SpotlightProjectList.ALL_PROJECTS_LOCATION}: " +
          "removed $removedCount invalid project(s), added $addedCount missing project(s)"
      )
    } else {
      logger.lifecycle("${SpotlightProjectList.ALL_PROJECTS_LOCATION} is already up to date")
    }
  }
}
