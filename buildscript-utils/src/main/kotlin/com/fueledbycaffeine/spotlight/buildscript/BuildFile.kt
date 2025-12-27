package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.parser.ParserContext

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
  // ParserContext handles both SPI discovery and IDE overrides
  val parser = ParserContext.findParser(project, rules)
    ?: error("No parser available for ${project.path}")

  return parser.parse(project, rules)
}
