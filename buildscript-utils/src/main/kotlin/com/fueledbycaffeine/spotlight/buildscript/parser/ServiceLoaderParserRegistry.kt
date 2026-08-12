package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.parser.impl.BuildscriptParserProvider
import com.fueledbycaffeine.spotlight.buildscript.parser.impl.ParserSelection
import com.fueledbycaffeine.spotlight.buildscript.parser.impl.TaskRequestParserProvider
import java.util.ServiceLoader

/**
 * Default registry that discovers parsers via Java's [ServiceLoader].
 * This allows different parser implementations to be loaded at runtime if present on the classpath.
 * All parsers are additive and their results are merged together.
 */
internal object ServiceLoaderParserRegistry {
  private val buildscriptParserProviders: List<BuildscriptParserProvider> by lazy {
    ServiceLoader.load(
      BuildscriptParserProvider::class.java,
      BuildscriptParserProvider::class.java.classLoader,
    ).toList()
  }

  private val taskRequestParserProviders: List<TaskRequestParserProvider> by lazy {
    ServiceLoader.load(
      TaskRequestParserProvider::class.java,
      TaskRequestParserProvider::class.java.classLoader,
    ).toList()
  }

  /**
   * Find a parser for the given project.
   * All discovered parsers are collected and wrapped in a composite parser.
   *
   * Returns null if no providers are available.
   */
  fun findBuildScriptParser(rules: Set<DependencyRule>): BuildscriptParser? {
    return ParserSelection.selectParser(buildscriptParserProviders, rules)
  }

  /**
   * Find a parser for the given project.
   * All discovered parsers are collected and wrapped in a composite parser.
   *
   * Returns null if no providers are available.
   */
  fun findTaskRequestParser(): TaskRequestParser? {
    return ParserSelection.selectParser(taskRequestParserProviders)
  }
}
