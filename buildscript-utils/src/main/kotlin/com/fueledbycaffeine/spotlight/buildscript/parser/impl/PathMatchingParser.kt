package com.fueledbycaffeine.spotlight.buildscript.parser.impl

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.ProjectPathMatchRule
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildscriptParser

/**
 * Parser that adds dependencies based on project path pattern matching.
 * This parser doesn't parse the build script content itself - it only matches
 * the project's path against configured regex patterns.
 * 
 * @param pathMatchRules The set of [ProjectPathMatchRule] instances to evaluate
 */
public class PathMatchingParser(
  private val pathMatchRules: Set<ProjectPathMatchRule>
) : BuildscriptParser {
  override fun parse(project: GradlePath, rules: Set<DependencyRule>): Set<GradlePath> {
    return pathMatchRules
      .filter { it.pattern.containsMatchIn(project.path) }
      .flatMapTo(mutableSetOf()) { it.includedProjects }
  }
}
