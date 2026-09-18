@file:Suppress("UnstableApiUsage")

package com.fueledbycaffeine.spotlight

import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemSpec
import org.gradle.api.problems.Problems

private val SPOTLIGHT_PROBLEMS = ProblemGroup.create("spotlight", "Spotlight")

/** Reports a fatal Spotlight problem. */
internal fun Problems.throwingSpotlightProblem(
  id: String,
  displayName: String,
  contextualLabel: String,
  details: String? = null,
  solution: String,
  exceptionMessage: String = contextualLabel,
  file: String? = null,
  lines: List<Int> = emptyList(),
  stackLocation: Boolean = false,
): Nothing {
  val problemId = ProblemId.create(id, displayName, SPOTLIGHT_PROBLEMS)
  val configure = Action<ProblemSpec> { spec ->
    spec.contextualLabel(contextualLabel)
    details?.let(spec::details)
    spec.solution(solution)
    when {
      file != null && lines.isNotEmpty() -> lines.forEach { line -> spec.lineInFileLocation(file, line) }
      file != null -> spec.fileLocation(file)
      stackLocation -> spec.stackLocation()
    }
  }
  val exception = InvalidUserDataException(exceptionMessage)
  throw reporter.throwing(exception, problemId, configure)
}
