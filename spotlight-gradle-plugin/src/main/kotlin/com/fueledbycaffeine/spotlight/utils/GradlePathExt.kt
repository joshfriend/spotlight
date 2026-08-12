package com.fueledbycaffeine.spotlight.utils

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.TaskRequests
import org.gradle.TaskExecutionRequest
import org.gradle.api.initialization.Settings
import java.nio.file.Path

internal fun guessProjectsFromTaskRequests(
  rootDir: Path,
  taskRequests: List<TaskExecutionRequest>,
  allProjects: Set<GradlePath>,
): Set<GradlePath> {
  val taskNames = taskNames(taskRequests)
  return taskNames
    .map { GradlePath(rootDir, it.projectPathGuess) }
    .plus(TaskRequests(taskNames, allProjects).parseDependencies())
    .filter { it.hasBuildFile }
    .toSet()
}

internal fun taskNames(taskRequests: List<TaskExecutionRequest>): Set<String> {
  return taskRequests.flatMapTo(sortedSetOf()) { it.args }
}

// Try removing characters that could be the task name, then assume whatever is left is the task's
// project path. If this doesn't neatly map to a location with a buildfile, it is ignored because
// it is probably some other argument like '--tests' or a test classname, etc.
private val String.projectPathGuess get() = this.replace(Regex("\\w+$"), "")

internal fun Settings.include(paths: Iterable<GradlePath>) {
  include(paths.map { it.path })
}

internal fun List<String>.asSortedProjectsContent(): String {
  return sorted().joinToString(separator = "\n", postfix = "\n")
}
