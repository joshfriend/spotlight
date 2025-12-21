package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule

/**
 * A composite parser that runs multiple parsers and merges their results.
 * This is used when multiple ADDITIVE parsers match a build file.
 */
internal class CompositeParser(
  private val parsers: List<BuildScriptParser>
) : BuildScriptParser {
  override fun parse(project: GradlePath, rules: Set<DependencyRule>): Set<GradlePath> {
    return parsers.flatMap { parser -> parser.parse(project, rules) }.toSet()
  }
}
