package com.fueledbycaffeine.spotlight.tasks

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.SETTINGS_SCRIPT
import com.fueledbycaffeine.spotlight.buildscript.SETTINGS_SCRIPT_KOTLIN
import com.fueledbycaffeine.spotlight.buildscript.gradlePathRelativeTo
import com.fueledbycaffeine.spotlight.buildscript.graph.BreadthFirstSearch
import com.fueledbycaffeine.spotlight.throwingSpotlightProblem
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.problems.Problems
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import javax.inject.Inject

/** Shared disk inventory and exclusions for checking and fixing the project list. */
@DisableCachingByDefault(because = "Has no outputs")
public abstract class SpotlightProjectListTask : DefaultTask() {
  private companion object {
    val NON_PROJECT_DIRS = listOf("build", "buildSrc", "src", "src-gen", "tmp")
  }

  // The check task overrides this with @InputFile; the fix task may create the file.
  @get:Internal
  internal abstract val projectsFile: RegularFileProperty

  // These tasks always run. Avoid tracking the entire source tree as an input.
  @get:Internal
  internal abstract val rootDirectory: DirectoryProperty

  @get:Input
  internal abstract val discoverUnlistedProjects: Property<Boolean>

  @get:Input
  internal abstract val excludedDirectoryPatterns: SetProperty<Regex>

  /**
   * Exact Gradle paths to exclude from disk discovery.
   *
   * Excluding a project required by the dependency graph is an error.
   */
  @get:Input
  public abstract val excludedProjectPaths: SetProperty<String>

  @get:Inject
  internal abstract val problems: Problems

  init {
    group = "spotlight"
    discoverUnlistedProjects.convention(true)
    excludedProjectPaths.convention(emptySet())
    excludedDirectoryPatterns.convention(emptySet())
  }

  internal fun discoverProjectsOnDisk(): Set<GradlePath> {
    if (!discoverUnlistedProjects.get()) return emptySet()

    val rootDir = rootDirectory.asFile.get().toPath()
    val excludedProjects = excludedProjectPaths.get()
    val patterns = excludedDirectoryPatterns.get()
    val projects = mutableSetOf<GradlePath>()

    fun scan(directory: Path) {
      Files.newDirectoryStream(directory).use { children ->
        for (child in children) {
          if (!child.isDirectory() || child.name in NON_PROJECT_DIRS || child.matchesExclusion(rootDir, patterns)) continue
          val project = child.gradlePathRelativeTo(rootDir)
          if (project.hasSettingsFile) continue
          if (project.hasBuildFile && project.path !in excludedProjects) projects.add(project)
          scan(child)
        }
      }
    }

    scan(rootDir)
    return projects
  }

  internal fun discoverProjectsWithDependencies(projects: Set<GradlePath>): Set<GradlePath> {
    val dependencies = BreadthFirstSearch.run(projects).values.flatten().toSet()
    val excludedProjects = excludedProjectPaths.get()
    val rootDir = rootDirectory.asFile.get().toPath()
    val patterns = excludedDirectoryPatterns.get()
    val conflicts = dependencies.filter { project ->
      project.path in excludedProjects || generateSequence(project.projectDir) { it.parent }
        .takeWhile { it != rootDir }
        .any { it.matchesExclusion(rootDir, patterns) }
    }.sortedBy { it.path }
    if (conflicts.isNotEmpty()) {
      val label = "Found excluded projects required by the dependency graph"
      val details = conflicts.joinToString("\n") { project -> "  ${project.path}" }
      val solution =
        "Update excludeProjectPaths or excludeDirectoriesMatchingRegex in spotlight.projectDiscovery.directoryScan."
      val settingsFile = rootDir.resolve(SETTINGS_SCRIPT).takeIf { it.exists() }
        ?: rootDir.resolve(SETTINGS_SCRIPT_KOTLIN).takeIf { it.exists() }
      problems.throwingSpotlightProblem(
        id = "excluded-required-projects",
        displayName = "Required projects are excluded",
        contextualLabel = label,
        details = details,
        solution = solution,
        exceptionMessage = "$label:\n$details\n\n$solution",
        file = settingsFile?.toString(),
        stackLocation = settingsFile == null,
      )
    }
    return projects + dependencies
  }

  private fun Path.matchesExclusion(rootDir: Path, patterns: Set<Regex>): Boolean {
    val relativePath = relativeTo(rootDir).invariantSeparatorsPathString
    return patterns.any { it.matches(relativePath) }
  }
}
