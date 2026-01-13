package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import java.util.ServiceLoader

/**
 * Thread-local context for parser discovery.
 *
 * By default, uses [ServiceLoader]-based discovery.
 * Can be overridden with a custom [ParserRegistry] (e.g., for IDE).
 */
public object ParserContext {
  private val customRegistry = ThreadLocal<ParserRegistry?>()
  private val customConfiguration = ThreadLocal<ParserConfiguration?>()

  /**
   * Current parser configuration for the calling thread.
   */
  public fun configuration(): ParserConfiguration = customConfiguration.get() ?: ParserConfiguration.EMPTY

  /**
   * Find a parser for the given project.
   *
   * If a custom registry is set (via parserContext), uses that.
   * Otherwise, uses [ServiceLoader]-based discovery.
   */
  public fun findParser(project: GradlePath, rules: Set<DependencyRule>): BuildscriptParser? {
    val registry = customRegistry.get() ?: ServiceLoaderParserRegistry
    return registry.findParser(project, rules, configuration())
  }

  /**
   * Execute a block with a custom parser registry.
   *
   * Example:
   * ```
   * parserContext(customRegistry) {
   *   // All parsing in this block uses customRegistry
   *   BuildFile(project).parseDependencies(rules)
   * }
   * ```
   */
  public fun <T> parserContext(
    registry: ParserRegistry?,
    configuration: ParserConfiguration? = null,
    block: () -> T
  ): T {
    val oldRegistry = customRegistry.get()
    val oldConfiguration = customConfiguration.get()
    try {
      customRegistry.set(registry)
      customConfiguration.set(configuration)
      return block()
    } finally {
      customRegistry.set(oldRegistry)
      customConfiguration.set(oldConfiguration)
    }
  }
}
