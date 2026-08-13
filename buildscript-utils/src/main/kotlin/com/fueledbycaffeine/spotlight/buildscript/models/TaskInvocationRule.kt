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
 * a rule declaring `buildHealth`. Project path qualifiers are ignored; `:foo:check` and `check`
 * both match a rule declaring `check`.
 *
 * @param taskNames Full task names (unqualified, e.g. `buildHealth`) that trigger this rule.
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
   * line) is one of this rule's [taskNames], ignoring any project path qualifier.
   */
  public fun matches(taskRequestArgs: List<String>): Boolean {
    return taskRequestArgs.asSequence()
      .filterNot { it.startsWith("-") }
      .map { it.substringAfterLast(GRADLE_PATH_SEP) }
      .any { it in taskNames }
  }
}
