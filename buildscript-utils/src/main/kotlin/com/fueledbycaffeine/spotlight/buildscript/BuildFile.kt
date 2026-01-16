package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.parser.ServiceLoaderParserRegistry

public data class BuildFile(public val project: GradlePath) {
  /**
   * Parse dependencies from the build file.
   */
  public fun parseDependencies(rules: Set<DependencyRule> = emptySet()): Set<GradlePath> {
    val parser = ServiceLoaderParserRegistry.findParser(rules)
      ?: error("No parsers available")

    return parser.parse(project, rules)
  }
}
