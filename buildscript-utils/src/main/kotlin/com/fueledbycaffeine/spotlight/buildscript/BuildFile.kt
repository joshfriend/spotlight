package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ParsingConfiguration
import com.fueledbycaffeine.spotlight.buildscript.parser.ParserRegistry
import kotlin.io.path.name

public data class BuildFile(
  public val project: GradlePath,
  public val config: ParsingConfiguration = ParsingConfiguration.DEFAULT,
) {
  /**
   * Parse dependencies from the build file.
   */
  public fun parseDependencies(
    rules: Set<DependencyRule> = emptySet(),
  ): Set<GradlePath> = parseBuildFile(project, rules, config)
}

internal fun parseBuildFile(
  project: GradlePath,
  rules: Set<DependencyRule>,
  config: ParsingConfiguration = ParsingConfiguration.DEFAULT,
): Set<GradlePath> {
  // Find a parser via ServiceLoader - will return highest priority parser that supports the file type
  val parser = ParserRegistry.findParser(project.buildFilePath)
    ?: error("No parser available for ${project.buildFilePath}")
  
  return parser.parse(project, rules)
}