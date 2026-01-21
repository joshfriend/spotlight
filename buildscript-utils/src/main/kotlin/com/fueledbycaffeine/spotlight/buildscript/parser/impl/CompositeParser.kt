package com.fueledbycaffeine.spotlight.buildscript.parser.impl

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildscriptParser

/**
 * A composite parser that runs multiple [BuildscriptParser]s and merges their results.
 */
internal class CompositeParser(
  private val parsers: List<BuildscriptParser>
) : BuildscriptParser {
  override fun parse(project: GradlePath, rules: Set<DependencyRule>): Set<GradlePath> {
    return parsers.flatMap { parser -> parser.parse(project, rules) }.toSet()
  }
}
