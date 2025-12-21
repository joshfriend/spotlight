package com.fueledbycaffeine.spotlight.buildscript.parser

import java.nio.file.Path

/**
 * Service Provider Interface for build script parsers.
 * 
 * Implementations of this interface can be discovered via Java's ServiceLoader mechanism,
 * allowing different parser implementations (regex, AST, PSI) to be loaded at runtime if present
 * on the classpath.
 * 
 * Implementations should check the build file path and return null if they don't support
 * the given build file type.
 */
public interface BuildScriptParserProvider {
  /**
   * Get the parser for the given build file path, or null if this provider
   * doesn't support the given build file type.
   * 
   * @param buildFilePath The full path to the build file
   * @return A BuildScriptParser implementation if supported, null otherwise
   */
  public fun getParser(buildFilePath: Path): BuildScriptParser?
  
  /**
   * Get the priority of this provider. Higher priority providers are checked first.
   * Default priority is 0. Regex parsers should use priority 0, AST parsers 100.
   * 
   * @return The priority value
   */
  public fun getPriority(): Int = 0
}
