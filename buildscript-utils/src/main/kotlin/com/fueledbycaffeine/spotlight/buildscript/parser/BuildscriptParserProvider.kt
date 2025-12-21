package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import java.nio.file.Path
import com.fueledbycaffeine.spotlight.buildscript.parser.ParserMode.ADDITIVE
import com.fueledbycaffeine.spotlight.buildscript.parser.ParserMode.REPLACE
import java.util.ServiceLoader

/**
 * Service Provider Interface for build script parsers.
 * 
 * Implementations of this interface can be discovered via Java's [ServiceLoader] mechanism,
 * allowing different parser implementations (regex, AST, PSI) to be loaded at runtime if present
 * on the classpath.
 * 
 * Implementations should check the build file path and return null if they don't support
 * the given build file.
 */
public interface BuildscriptParserProvider {
  /**
   * Get the parser for the given project, or null if this provider
   * doesn't support the given build file.
   * 
   * @param project The Gradle project
   * @return A [BuildScriptParser] implementation if supported, null otherwise
   */
  public fun getParser(project: GradlePath): BuildScriptParser?

  /**
   * Get the priority of this provider. Higher priority providers are checked first.
   * Default priority is 0. Regex parsers should use priority 0, AST parsers 100.
   * 
   * @return The priority value
   */
  public val priority: Int
    get() = 0

  /**
   * Get the mode of this provider, determining how it interacts with other providers.
   * 
   * [REPLACE] mode (default): This parser replaces lower-priority parsers. The search stops
   * when a [REPLACE] parser is found.
   * 
   * [ADDITIVE] mode: This parser supplements other parsers. The search continues to find
   * additional parsers, and all matching parsers are executed with their results merged.
   * 
   * @return The [ParserMode]
   */
  public val mode: ParserMode
    get() = REPLACE
}
