package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ParsingConfiguration
import com.fueledbycaffeine.spotlight.buildscript.parser.AstParserRegistry
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildScriptParser
import com.fueledbycaffeine.spotlight.buildscript.parser.RegexBuildScriptParser
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
  return if (config == ParsingConfiguration.AST) {
    // Try to find an AST/PSI parser via ServiceLoader
    val parser = AstParserRegistry.findParser(project.buildFilePath.name)
    
    if (parser != null) {
      try {
        return parser.parse(project, rules)
      } catch (_: BuildScriptParser.ParserException) {
        // Fall through to regex parsing if AST/PSI parsing fails
      }
    }
    
    // Fall back to regex parsing if no AST parser available or it failed
    RegexBuildScriptParser.parse(project, rules)
  } else {
    RegexBuildScriptParser.parse(project, rules)
  }
}