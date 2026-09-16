package com.fueledbycaffeine.spotlight.tasks

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.SETTINGS_SCRIPT
import com.fueledbycaffeine.spotlight.buildscript.SETTINGS_SCRIPT_KOTLIN
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import com.fueledbycaffeine.spotlight.utils.asSortedProjectsContent
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.work.DisableCachingByDefault
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText

/**
 * Task to automatically fix issues in the all-projects.txt file by:
 * - Migrating any include statements from settings.gradle(.kts) to all-projects.txt
 * - Removing invalid projects (those without build files)
 * - Adding missing projects found on disk or through dependencies
 * - Sorting the result
 */
@DisableCachingByDefault(because = "Has no outputs")
@UntrackedTask(because = "Has no outputs")
public abstract class FixSpotlightProjectListTask : SpotlightProjectListTask() {
  public companion object {
    public const val NAME: String = "fixAllProjectsList"
  }

  // Not an @InputFile: the file may not exist yet, and this task creates it if missing.
  @get:Internal
  internal abstract override val projectsFile: RegularFileProperty

  init {
    description = "Auto-fixes issues in ${SpotlightProjectList.ALL_PROJECTS_LOCATION} by removing invalid projects, adding missing ones, and sorting"
  }

  @TaskAction
  internal fun action() {
    val rootDir = rootDirectory.asFile.get().toPath()
    val settingsFile = rootDir.resolve(SETTINGS_SCRIPT).takeIf { it.exists() }
      ?: rootDir.resolve(SETTINGS_SCRIPT_KOTLIN).takeIf { it.exists() }

    // Split out the include lines from the rest of the settings file
    val (includeLines, cleanedLines) = settingsFile?.readLines().orEmpty()
      .partition { line -> INCLUDE_PROJECT_PATH.containsMatchIn(line) }

    // Extract project paths before removing the include lines
    val migratedProjects = includeLines.flatMap { line ->
      INCLUDE_PROJECT_PATH.findAll(line)
        .map { match -> GradlePath(rootDir, match.groupValues[1]) }
    }.toSet()

    val allProjects = SpotlightProjectList.allProjects(rootDir).read() + migratedProjects
    val validProjects = removeInvalidProjects(allProjects)
    val finalProjects = discoverProjectsWithDependencies(validProjects + discoverProjectsOnDisk())

    // Only migrate includes after validating exclusions, so a conflict leaves both files unchanged.
    settingsFile?.writeText(cleanedLines.joinToString("\n"))
    writeSortedProjects(finalProjects)
    logResults(
      allProjects.size - validProjects.size,
      finalProjects.size - validProjects.size + migratedProjects.size,
    )
  }

  private fun removeInvalidProjects(allProjects: Set<GradlePath>): Set<GradlePath> {
    val validProjects = allProjects.filter { it.hasBuildFile }.toSet()
    return validProjects
  }

  private fun writeSortedProjects(projects: Set<GradlePath>) {
    val sortedProjectPaths = projects.map { it.path }
    val file = projectsFile.asFile.get()
    file.parentFile?.mkdirs()
    file.writeText(sortedProjectPaths.asSortedProjectsContent())
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
