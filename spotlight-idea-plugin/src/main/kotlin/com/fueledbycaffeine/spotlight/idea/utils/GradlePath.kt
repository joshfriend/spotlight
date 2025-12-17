package com.fueledbycaffeine.spotlight.idea.utils

import com.fueledbycaffeine.spotlight.buildscript.GradlePath

/**
 * Determines the pattern that would be used for adding/removing this path to/from the spotlight list.
 * Returns a wildcard pattern (e.g., `:advertising:**`) if the path has child projects,
 * otherwise returns the path itself.
 */
internal fun GradlePath.toSpotlightPattern(): GradlePath {
  val childProjects = expandChildProjects()
  val actualChildProjects = childProjects.filter { it != this }

  return if (actualChildProjects.isNotEmpty()) {
    GradlePath(root, "$path:**")
  } else {
    this
  }
}

/**
 * Returns true if this path represents a wildcard pattern (e.g., `:advertising:**`)
 */
internal val GradlePath.isWildcardPattern: Boolean
  get() = path.endsWith(":**")