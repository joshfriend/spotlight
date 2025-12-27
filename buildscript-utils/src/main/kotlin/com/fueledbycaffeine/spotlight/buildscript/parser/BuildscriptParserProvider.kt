package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.parser.ParserMode.ADDITIVE
import com.fueledbycaffeine.spotlight.buildscript.parser.ParserMode.REPLACE
import java.io.Serializable
import java.util.ServiceLoader

/**
 * Service Provider Interface for build script parsers.
 * 
 * Implementations of this interface can be discovered via Java's [ServiceLoader] mechanism,
 * allowing different parser implementations (regex, AST, PSI) to be loaded at runtime if present
 * on the classpath.
 *
 * Implementations must be [Serializable] so they can be transferred from the Gradle build
 * to the IDE plugin via the Tooling API.
 */
public interface BuildscriptParserProvider : Serializable {
  /**
   * Get the parser instance provided by this provider.
   *
   * The returned parser is responsible for determining whether it can parse
   * a given project (e.g., by checking file extensions or content).
   *
   * @return A [BuildscriptParser] implementation
   */
  public fun getParser(): BuildscriptParser

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
