package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.parser.ParserMode.REPLACE

/**
 * Utility functions for selecting and combining parsers.
 */
public object ParserSelection {

  /**
   * Collect parsers from [BuildscriptParserProvider]s by priority, stopping at the first [REPLACE]
   * parser, and add rule-based parsers.
   *
   * @param providers List of parser providers sorted by priority
   * @param rules Dependency rules that may require additional parsers
   * @return Combined [BuildscriptParser] or null if no parsers available
   */
  public fun selectParser(
    providers: List<BuildscriptParserProvider>,
    rules: Set<DependencyRule>
  ): BuildscriptParser? {
    val parsersToRun = buildList {
      // Take providers until we hit a REPLACE parser (inclusive)
      val replaceIndex = providers.indexOfFirst { it.mode == REPLACE }
      val providersToUse = if (replaceIndex >= 0) {
        providers.take(replaceIndex + 1)
      } else {
        providers
      }

      providersToUse
        .map { it.getParser() }
        .forEach { add(it) }

      // Add rule-based parsers
      rules.filterIsInstance<ImplicitDependencyRule.ProjectPathMatchRule>()
        .takeIf { it.isNotEmpty() }
        ?.let { add(PathMatchingParser(it.toSet())) }

      rules.filterIsInstance<ImplicitDependencyRule.BuildscriptMatchRule>()
        .takeIf { it.isNotEmpty() }
        ?.let { add(BuildscriptMatchingParser(it.toSet())) }
    }

    return when (parsersToRun.size) {
      0 -> null
      else -> CompositeParser(parsersToRun)
    }
  }
}
