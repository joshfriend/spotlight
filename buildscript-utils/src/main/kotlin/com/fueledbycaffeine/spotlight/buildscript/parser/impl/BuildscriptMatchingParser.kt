package com.fueledbycaffeine.spotlight.buildscript.parser.impl

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildscriptParser
import kotlin.io.path.readText

/**
 * Parser that adds dependencies based on buildscript content pattern matching.
 * This parser uses regex patterns to find specific text within build files and
 * adds configured dependencies when those patterns are found.
 * 
 * @param buildscriptMatchRules The set of BuildscriptMatchRule instances to evaluate
 */
public class BuildscriptMatchingParser(
  private val buildscriptMatchRules: Set<ImplicitDependencyRule.BuildscriptMatchRule>
) : BuildscriptParser {
  override fun parse(project: GradlePath, rules: Set<DependencyRule>): Set<GradlePath> {
    val implicitDependencies = mutableSetOf<GradlePath>()
    val fileContent = project.buildFilePath.readText()
    
    val remainingRules = buildscriptMatchRules.toMutableSet()
    if (remainingRules.isEmpty()) {
      return implicitDependencies
    }

    fileContent.lines().forEach { line ->
      if (remainingRules.isEmpty()) {
        return@forEach
      }
      val matchedRules = mutableSetOf<ImplicitDependencyRule.BuildscriptMatchRule>()
      remainingRules.forEach { rule ->
        if (rule.pattern.containsMatchIn(line)) {
          implicitDependencies.addAll(rule.includedProjects)
          matchedRules.add(rule)
        }
      }
      remainingRules.removeAll(matchedRules)
    }

    return implicitDependencies
  }
}
