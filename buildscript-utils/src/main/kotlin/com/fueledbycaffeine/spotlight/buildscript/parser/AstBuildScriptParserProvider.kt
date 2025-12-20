package com.fueledbycaffeine.spotlight.buildscript.parser

/**
 * Service Provider Interface for AST-based build script parsers.
 * 
 * Implementations of this interface can be discovered via Java's ServiceLoader mechanism,
 * allowing optional AST parsers (Groovy and Kotlin) to be loaded at runtime if present
 * on the classpath.
 * 
 * Implementations should check the file extension and return null if they don't support
 * the given build file type.
 */
public interface AstBuildScriptParserProvider {
  /**
   * Get the parser for the given build file extension, or null if this provider
   * doesn't support the given extension.
   * 
   * @param fileExtension The file extension (e.g., "gradle" or "gradle.kts")
   * @return A BuildScriptParser implementation if supported, null otherwise
   */
  public fun getParser(fileExtension: String): BuildScriptParser?
  
  /**
   * Get the priority of this provider. Higher priority providers are checked first.
   * Default priority is 0. AST parsers should generally use priority 100.
   * 
   * @return The priority value
   */
  public fun getPriority(): Int = 0
}
