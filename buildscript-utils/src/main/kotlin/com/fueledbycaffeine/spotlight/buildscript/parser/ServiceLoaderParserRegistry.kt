package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.parser.impl.BuildscriptParserProvider
import com.fueledbycaffeine.spotlight.buildscript.parser.impl.ParserSelection
import java.util.ServiceLoader

/**
 * Default registry that discovers parsers via Java's [ServiceLoader].
 * This allows different parser implementations to be loaded at runtime if present on the classpath.
 * All parsers are additive and their results are merged together.
 */
internal object ServiceLoaderParserRegistry {
  private val providers: List<BuildscriptParserProvider> by lazy {
    ServiceLoader.load(
      BuildscriptParserProvider::class.java,
      BuildscriptParserProvider::class.java.classLoader,
    ).toList()
  }

  /**
   * Find a parser for the given project.
   * All discovered parsers are collected and wrapped in a composite parser.
   *
   * Returns null if no providers are available.
   */
  fun findParser(rules: Set<DependencyRule>): BuildscriptParser? {
    return ParserSelection.selectParser(providers, rules)
  }
}
