package com.fueledbycaffeine.spotlight.tasks

import com.fueledbycaffeine.spotlight.buildscript.SETTINGS_SCRIPT
import com.fueledbycaffeine.spotlight.buildscript.SETTINGS_SCRIPT_KOTLIN
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import com.fueledbycaffeine.spotlight.throwingSpotlightProblem
import com.fueledbycaffeine.spotlight.utils.asSortedProjectsContent
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import kotlin.io.path.exists

@DisableCachingByDefault(because = "Has no outputs")
public abstract class CheckSpotlightProjectListTask : SpotlightProjectListTask() {
  public companion object {
    public const val NAME: String = "checkAllProjectsList"
  }

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  internal abstract override val projectsFile: RegularFileProperty

  init {
    description = "Checks if ${SpotlightProjectList.ALL_PROJECTS_LOCATION} is set up correctly"
  }

  @TaskAction
  internal fun action() {
    checkSorted()
    checkForIncludeStatements()
    checkValidProjects()
    checkAllProjectsAreDiscovered()
    checkForUnlistedProjects()
  }

  private fun checkSorted() {
    val file = projectsFile.asFile.get()
    val current = file.readLines()

    if (file.readText() != current.asSortedProjectsContent()) {
      val label = "Spotlight's list of all projects is not sorted: ${file.path}"
      val solution = "Run :${FixSpotlightProjectListTask.NAME} to fix it."
      problems.throwingSpotlightProblem(
        id = "project-list-not-sorted",
        displayName = "Project list is not sorted",
        contextualLabel = label,
        solution = solution,
        exceptionMessage = "$label\n$solution",
        file = file.path,
      )
    }
  }

  private fun checkForIncludeStatements() {
    val rootDir = rootDirectory.asFile.get()

    val settingsFile = rootDir.resolve(SETTINGS_SCRIPT).takeIf { it.exists() }
      ?: rootDir.resolve(SETTINGS_SCRIPT_KOTLIN).takeIf { it.exists() }
      ?: return

    val includeLines = settingsFile.readLines()
      .withIndex()
      // Only check lines that start with `include` (ignoring whitespace). This will filter out "include statements"
      // that are only in comments, for example.
      .filter { (_, line) -> line.trim().startsWith("include") }
      .filter { (_, line) -> INCLUDE_PROJECT_PATH.containsMatchIn(line) }

    if (includeLines.isNotEmpty()) {
      val label = "Found 'include' statements in ${settingsFile.name}:"
      val details = buildString {
        includeLines.forEach { (index, line) -> appendLine("  ${settingsFile.name}:${index + 1}: ${line.trim()}") }
        append("Spotlight manages project inclusion automatically.")
      }
      val solution = "Remove these 'include' statements and add the project paths to " +
        "${SpotlightProjectList.ALL_PROJECTS_LOCATION}, or run :${FixSpotlightProjectListTask.NAME}."
      problems.throwingSpotlightProblem(
        id = "settings-include-statements",
        displayName = "Settings contains project include statements",
        contextualLabel = label,
        details = details,
        solution = solution,
        exceptionMessage = "$label\n\n$details\n\n$solution",
        file = settingsFile.path,
        lines = includeLines.map { (index) -> index + 1 },
      )
    }
  }

  private fun checkValidProjects() {
    val rootDir = rootDirectory.asFile.get()
    val allProjects = SpotlightProjectList.allProjects(rootDir.toPath())
    val projects = allProjects.read()

    // Check that all listed projects have build files
    val invalidProjects = projects.filterNot { it.hasBuildFile }

    if (invalidProjects.isNotEmpty()) {
      val label = "Found invalid projects in ${SpotlightProjectList.ALL_PROJECTS_LOCATION}:"
      val details = buildString {
        appendLine("The following projects do not have a build.gradle(.kts) file:")
        invalidProjects.sortedBy { it.path }.forEach { project ->
          appendLine("  ${project.path} (expected at ${project.projectDir})")
        }
      }.trim()
      val solution = "Run :${FixSpotlightProjectListTask.NAME} to remove these invalid projects."
      val invalidPaths = invalidProjects.mapTo(mutableSetOf()) { it.path }
      val invalidLines = projectsFile.asFile.get().readLines().withIndex()
        .filter { (_, line) -> line.trim() in invalidPaths }
        .map { (index) -> index + 1 }
      problems.throwingSpotlightProblem(
        id = "invalid-listed-projects",
        displayName = "Project list contains invalid projects",
        contextualLabel = label,
        details = details,
        solution = solution,
        exceptionMessage = "$label\n\n$details\n\n$solution",
        file = projectsFile.asFile.get().path,
        lines = invalidLines,
      )
    }
  }

  private fun checkAllProjectsAreDiscovered() {
    val rootDir = rootDirectory.asFile.get()
    val allProjects = SpotlightProjectList.allProjects(rootDir.toPath())
    val listedProjects = allProjects.read()

    // Use BFS to discover all projects starting from the listed ones
    val discoveredProjects = discoverProjectsWithDependencies(listedProjects)

    // Find projects discovered by BFS that are not in the all-projects.txt file
    val missingProjects = discoveredProjects.filterNot { listedProjects.contains(it) }

    if (missingProjects.isNotEmpty()) {
      val label = "Found projects missing from ${SpotlightProjectList.ALL_PROJECTS_LOCATION}:"
      val details = buildString {
        appendLine("The following projects were discovered via dependency graph but are not listed:")
        missingProjects.sortedBy { it.path }.forEach { project ->
          appendLine("  ${project.path}")
        }
      }.trim()
      val solution = "Run :${FixSpotlightProjectListTask.NAME} to add these missing projects."
      problems.throwingSpotlightProblem(
        id = "missing-dependency-projects",
        displayName = "Dependency projects are missing from the project list",
        contextualLabel = label,
        details = details,
        solution = solution,
        exceptionMessage = "$label\n\n$details\n\n$solution",
        file = projectsFile.asFile.get().path,
      )
    }
  }

  private fun checkForUnlistedProjects() {
    val rootDir = rootDirectory.asFile.get().toPath()
    val listedProjects = SpotlightProjectList.allProjects(rootDir).read()
    val unlistedProjects = (discoverProjectsOnDisk() - listedProjects).sortedBy { it.path }

    if (unlistedProjects.isNotEmpty()) {
      val isKotlinDsl = !rootDir.resolve(SETTINGS_SCRIPT).exists() && rootDir.resolve(SETTINGS_SCRIPT_KOTLIN).exists()
      val settingsFileName = if (isKotlinDsl) SETTINGS_SCRIPT_KOTLIN else SETTINGS_SCRIPT
      val label = "Found unlisted projects:"
      val details = buildString {
        appendLine(
          "The following project directories have a build.gradle(.kts) file but are not listed " +
            "in ${SpotlightProjectList.ALL_PROJECTS_LOCATION}:"
        )
        unlistedProjects.forEach { project ->
          appendLine("  ${project.path}")
        }
      }.trim()
      val solution = buildString {
        appendLine("Run :${FixSpotlightProjectListTask.NAME} to add these projects, or remove their build files.")
        appendLine("For intentionally unlisted projects, configure the directory scan in $settingsFileName:")
        appendLine()
        appendLine("spotlight {")
        appendLine("  projectDiscovery {")
        appendLine("    directoryScan {")
        unlistedProjects.forEach { project ->
          if (isKotlinDsl) {
            appendLine("      excludeProjectPaths(\"${project.path}\")")
          } else {
            appendLine("      excludeProjectPaths '${project.path}'")
          }
        }
        appendLine("    }")
        appendLine("  }")
        appendLine("}")
      }.trim()
      problems.throwingSpotlightProblem(
        id = "unlisted-projects",
        displayName = "Projects are missing from the project list",
        contextualLabel = label,
        details = details,
        solution = solution,
        exceptionMessage = "$label\n\n$details\n\n$solution",
        file = projectsFile.asFile.get().path,
      )
    }
  }
}
