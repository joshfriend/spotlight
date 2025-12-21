package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.parser.ParserRegistry

public data class BuildFile(
  public val project: GradlePath,
) {
  /**
   * Parse dependencies from the build file.
   */
  public fun parseDependencies(
    rules: Set<DependencyRule> = emptySet(),
  ): Set<GradlePath> = parseBuildFile(project, rules)
}

internal fun parseBuildFile(
  project: GradlePath,
  rules: Set<DependencyRule>,
): Set<GradlePath> {
  // Find a parser via ServiceLoader - will return highest priority parser that supports the file type
  val parser = ParserRegistry.findParser(project, rules)
    ?: error("No parser available for ${project.path}")

  return parser.parse(project, rules)
}