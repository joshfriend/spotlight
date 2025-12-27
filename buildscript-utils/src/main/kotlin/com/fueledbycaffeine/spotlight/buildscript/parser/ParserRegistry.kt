package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.ProjectPathMatchRule
import com.fueledbycaffeine.spotlight.buildscript.parser.ParserMode.ADDITIVE
import com.fueledbycaffeine.spotlight.buildscript.parser.ParserMode.REPLACE
import java.util.ServiceLoader

/**
 * Interface for discovering and providing [BuildscriptParser]s.
 *
 * Implementations can use different discovery mechanisms:
 * - ServiceLoader-based discovery ([ServiceLoaderParserRegistry])
 * - IDE-synced parsers ([IdeParserRegistry])
 * - Custom implementations
 */
public interface ParserRegistry {
  /**
   * Find a parser for the given project.
   *
   * @param project The Gradle project to parse
   * @param rules Additional dependency rules to apply
   * @return A [BuildscriptParser] instance, or null if no parser is available
   */
  public fun findParser(project: GradlePath, rules: Set<DependencyRule>): BuildscriptParser?
}

/**
 * Default registry that discovers parsers via Java's [ServiceLoader].
 * This allows different parser implementations to be loaded at runtime if present on the classpath.
 */
public object ServiceLoaderParserRegistry : ParserRegistry {
  private val providers: List<BuildscriptParserProvider> by lazy {
    ServiceLoader.load(
      BuildscriptParserProvider::class.java,
      BuildscriptParserProvider::class.java.classLoader,
    ).toList().sortedByDescending { it.priority }
  }
  
  /**
   * Find a parser for the given [GradlePath].
   * Providers are checked in priority order (highest first).
   * 
   * If a [REPLACE] mode provider is found, it stops the search.
   * If [ADDITIVE] mode providers are found, they're all collected and wrapped in a composite parser.
   * 
   * If rules contains any [ProjectPathMatchRule] instances, PathMatchingParser is automatically
   * added to handle path-based matching without parsing file content.
   * 
   * Returns null if no provider supports the build file.
   */
  override fun findParser(project: GradlePath, rules: Set<DependencyRule>): BuildscriptParser? {
    return ParserSelection.selectParser(providers, rules)
  }
}
