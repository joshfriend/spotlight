package com.fueledbycaffeine.spotlight.tasks

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
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

/**
 * Task to automatically fix issues in the all-projects.txt file by:
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
    val rootDir = rootDirectory.asFile.get()
    val allProjects = SpotlightProjectList.allProjects(rootDir.toPath())
    val listedProjects = allProjects.read()

    // Filter out invalid projects (those without build files)
    val validProjects = listedProjects.filter { it.hasBuildFile }
    val removedCount = listedProjects.size - validProjects.size

    // Use BFS to discover all projects
    val discoveredProjects = BreadthFirstSearch.flatten(validProjects)

    // Combine valid listed projects with newly discovered projects
    val allValidProjects = (validProjects + discoveredProjects).toSet()

    // Sort and write back to file
    val sortedProjectPaths = allValidProjects.map { it.path }.sorted()
    projectsFile.asFile.get().writeText(sortedProjectPaths.joinToString("\n"))

    // Log what was done
    val addedCount = allValidProjects.size - validProjects.size
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
