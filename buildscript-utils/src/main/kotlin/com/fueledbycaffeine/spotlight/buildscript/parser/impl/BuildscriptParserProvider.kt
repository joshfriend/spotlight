package com.fueledbycaffeine.spotlight.buildscript.parser.impl

import com.fueledbycaffeine.spotlight.buildscript.parser.BuildscriptParser
import java.util.ServiceLoader

/**
 * Service Provider Interface for build script parsers.
 * 
 * Implementations of this interface can be discovered via Java's [ServiceLoader] mechanism,
 * allowing different parser implementations (regex, AST, PSI) to be loaded at runtime if present
 * on the classpath.
 *
 * All parsers are additive - they all run and their results are merged together.
 */
public interface BuildscriptParserProvider {
  /**
   * Get the parser instance provided by this provider.
   *
   * @return A [BuildscriptParser] implementation
   */
  public fun getParser(): BuildscriptParser
}
