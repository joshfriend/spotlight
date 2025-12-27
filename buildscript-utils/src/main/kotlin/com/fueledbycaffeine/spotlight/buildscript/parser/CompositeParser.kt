package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule

/**
 * A composite parser that runs multiple [BuildscriptParser]s and merges their results.
 */
public class CompositeParser(
  private val parsers: List<BuildscriptParser>
) : BuildscriptParser {
  override fun parse(project: GradlePath, rules: Set<DependencyRule>): Set<GradlePath> {
    return parsers.flatMap { parser -> parser.parse(project, rules) }.toSet()
  }
}
