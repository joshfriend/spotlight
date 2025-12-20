package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ParsingConfiguration
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildScriptParser
import com.fueledbycaffeine.spotlight.buildscript.parser.GroovyAstParser
import com.fueledbycaffeine.spotlight.buildscript.parser.KotlinPsiParser
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
    // Try AST/PSI parsing first
    val parser: BuildScriptParser = when (project.buildFilePath.name) {
      GRADLE_SCRIPT -> GroovyAstParser
      else -> KotlinPsiParser
    }
    
    try {
      parser.parse(project, rules)
    } catch (_: BuildScriptParser.ParserException) {
      // Fall through to regex parsing if AST/PSI parsing fails
      RegexBuildScriptParser.parse(project, rules)
    }
  } else {
    RegexBuildScriptParser.parse(project, rules)
  }
}