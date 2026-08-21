package com.fueledbycaffeine.spotlight.buildscript.models

import com.fueledbycaffeine.spotlight.buildscript.GRADLE_PATH_SEP
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.squareup.moshi.JsonClass

/**
 * Rule for including projects when specific tasks are requested on the command line.
 *
 * Some plugins register tasks that operate on projects Spotlight cannot discover by parsing
 * buildscripts, for example a task that aggregates results across every project in the build.
 * This rule includes [includedProjects] (or every project, when [includeAllProjects] is true)
 * whenever one of [taskNames] is requested.
 *
 * ```json
 * {
 *   "taskInvocationRules": [
 *     {
 *       "taskNames": ["buildHealth"],
 *       "includeAllProjects": true
 *     }
 *   ]
 * }
 * ```
 *
 * Task names are matched exactly, so abbreviated invocations like `./gradlew :bH` will not match
 * a rule declaring `buildHealth`. Unqualified names match at any project path, so `:foo:check` and
 * `check` both match a rule declaring `check`. Fully qualified task paths match invocations that
 * select that exact task, so a rule declaring `:foo:check` does not match `:bar:check`.
 *
 * @param taskNames Full task names, either unqualified (e.g. `buildHealth`) or fully qualified
 * task paths (e.g. `:reports:aggregateReports`), that trigger this rule.
 * @param includedProjects Projects to include in the build when this rule matches.
 * @param includeAllProjects When true, all projects are included when this rule matches.
 */
@JsonClass(generateAdapter = true)
public data class TaskInvocationRule(
  val taskNames: Set<String>,
  val includedProjects: Set<GradlePath> = emptySet(),
  val includeAllProjects: Boolean = false,
) {
  /**
   * Returns true if any of [taskRequestArgs] (the raw task request arguments from the command
   * line) matches one of this rule's [taskNames]. Unqualified names ignore the project path while
   * fully qualified task paths match tasks selected relative to [defaultProjectPath].
   *
   * @param defaultProjectPath The default project used to resolve relative Gradle task selectors.
   */
  @JvmOverloads
  public fun matches(
    taskRequestArgs: List<String>,
    defaultProjectPath: GradlePath? = null,
  ): Boolean {
    return taskRequestArgs
      .filterNot { it.startsWith("-") }
      .any { requestedTask ->
        taskNames.any { configuredTask -> matches(requestedTask, configuredTask, defaultProjectPath) }
      }
  }

  private fun matches(requested: String, configured: String, defaultProject: GradlePath?): Boolean {
    if (requested == configured) {
      return true
    }
    // Unqualified rule names match that task name at any project path.
    if (!configured.startsWith(GRADLE_PATH_SEP)) {
      return requested.taskName == configured
    }
    // The rule is a fully qualified task path. Absolute selectors must match it exactly.
    if (requested.startsWith(GRADLE_PATH_SEP)) {
      return requested == configured
    }
    // Relative selectors can only be resolved against a known default project (-p/--project-dir).
    if (defaultProject == null) {
      return false
    }
    return if (GRADLE_PATH_SEP in requested) {
      // Qualified relative selectors like `foo:check` select one task below the default project.
      defaultProject.resolveTaskPath(requested) == configured
    } else {
      // Bare task names run in the default project and all of its subprojects.
      requested == configured.taskName && configured.projectPath.isProjectOrSubprojectOf(defaultProject)
    }
  }
}

private val String.taskName: String
  get() = substringAfterLast(GRADLE_PATH_SEP)

private val String.projectPath: String
  get() = substringBeforeLast(GRADLE_PATH_SEP).ifEmpty { GRADLE_PATH_SEP }

private fun GradlePath.resolveTaskPath(relativeSelector: String): String = when {
  isRootProject -> "$GRADLE_PATH_SEP$relativeSelector"
  else -> "$path$GRADLE_PATH_SEP$relativeSelector"
}

private fun String.isProjectOrSubprojectOf(project: GradlePath): Boolean =
  project.isRootProject || this == project.path || startsWith("${project.path}$GRADLE_PATH_SEP")
