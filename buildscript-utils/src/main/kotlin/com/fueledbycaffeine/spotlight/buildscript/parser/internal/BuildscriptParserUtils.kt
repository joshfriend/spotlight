package com.fueledbycaffeine.spotlight.buildscript.parser.internal

import com.fueledbycaffeine.spotlight.buildscript.GradlePath

/**
 * Computes implicit parent project dependencies.
 * A call to `Settings#include()` implicitly calls `include` on the parent directories, up to the root project.
 * If one of those directories has a buildscript, it will be included in the build as well, and we need to parse it.
 */
public fun computeImplicitParentProjects(project: GradlePath): Set<GradlePath> {
  val sequence = generateSequence(project) { it.parent }
  return sequence.filterTo(mutableSetOf()) { it != project && !it.isRootProject && it.hasBuildFile }
}

/**
 * Removes suffixes and prefixes from type-safe project accessors that are not part of the actual project path.
 */
public fun String.removeTypeSafeAccessorJunk(rootProjectAccessor: String): String =
  this.removePrefix("projects.")
    .removePrefix("$rootProjectAccessor.")
    .removeSuffix(".dependencyProject")
    .removeSuffix(".path")

