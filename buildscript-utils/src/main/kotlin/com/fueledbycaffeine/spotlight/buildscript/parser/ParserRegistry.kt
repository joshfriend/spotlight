package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.BuildscriptMatchRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.ProjectPathMatchRule
import java.nio.file.Path
import java.util.ServiceLoader

/**
 * Registry for discovering and managing build script parsers via Java's ServiceLoader.
 * This allows different parser implementations to be loaded at runtime if present on the classpath.
 */
internal object ParserRegistry {
  private val providers: List<BuildscriptParserProvider> by lazy {
    ServiceLoader.load(
      BuildscriptParserProvider::class.java,
      BuildscriptParserProvider::class.java.classLoader,
    ).toList().sortedByDescending { it.priority }
  }
  
  /**
   * Find a parser for the given project.
   * Providers are checked in priority order (highest first).
   * 
   * If a REPLACE mode provider is found, it stops the search.
   * If ADDITIVE mode providers are found, they're all collected and wrapped in a composite parser.
   * 
   * If rules contains any [ProjectPathMatchRule] instances, PathMatchingParser is automatically
   * added to handle path-based matching without parsing file content.
   * 
   * Returns null if no provider supports the build file.
   */
  fun findParser(project: GradlePath, rules: Set<DependencyRule>): BuildScriptParser? {
    val parsersToRun = mutableListOf<BuildScriptParser>()

    for (provider in providers) {
      val parser = provider.getParser(project)
      if (parser != null) {
        parsersToRun.add(parser)
        
        // If this is a REPLACE parser, stop searching
        if (provider.mode == ParserMode.REPLACE) {
          break
        }
        // If ADDITIVE, continue to find more parsers
      }
    }
    
    // If there are any ProjectPathMatchRule instances, add PathMatchingParser with those rules
    val pathMatchRules = rules.filterIsInstance<ProjectPathMatchRule>().toSet()
    if (pathMatchRules.isNotEmpty()) {
      parsersToRun.add(PathMatchingParser(pathMatchRules))
    }
    
    // If there are any BuildscriptMatchRule instances, add BuildscriptMatchingParser with those rules
    val buildscriptMatchRules = rules.filterIsInstance<BuildscriptMatchRule>().toSet()
    if (buildscriptMatchRules.isNotEmpty()) {
      parsersToRun.add(BuildscriptMatchingParser(buildscriptMatchRules))
    }
    
    return when (parsersToRun.size) {
      0 -> null
      1 -> parsersToRun.first()
      else -> CompositeParser(parsersToRun)
    }
  }
}
