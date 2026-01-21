package com.fueledbycaffeine.spotlight.buildscript.parser.impl

import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.BuildscriptMatchRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.ProjectPathMatchRule
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildscriptParser

/**
 * Utility functions for selecting and combining parsers.
 */
internal object ParserSelection {

  /**
   * Collect all parsers from [BuildscriptParserProvider]s and add rule-based parsers.
   * All parsers are additive and their results are merged together.
   *
   * If rules contains any [ProjectPathMatchRule] instances, [PathMatchingParser] is automatically
   * added to handle path-based matching without parsing file content.
   *
   * If rules contains any [BuildscriptMatchRule] instances, [BuildscriptMatchingParser] is
   * automatically added to handle regex-based matching.
   *
   * @param providers List of parser providers
   * @param rules Dependency rules that may require additional parsers
   * @return Combined [BuildscriptParser] or null if no parsers available
   */
  fun selectParser(
    providers: List<BuildscriptParserProvider>,
    rules: Set<DependencyRule>
  ): BuildscriptParser? {
    val parsersToRun = buildList {
      // Collect all parsers
      providers
        .map { it.getParser() }
        .forEach { add(it) }

      // Add rule-based parsers
      rules.filterIsInstance<ProjectPathMatchRule>()
        .takeIf { it.isNotEmpty() }
        ?.let { add(PathMatchingParser(it.toSet())) }

      rules.filterIsInstance<BuildscriptMatchRule>()
        .takeIf { it.isNotEmpty() }
        ?.let { add(BuildscriptMatchingParser(it.toSet())) }
    }

    return when (parsersToRun.size) {
      0 -> null
      else -> CompositeParser(parsersToRun)
    }
  }
}
