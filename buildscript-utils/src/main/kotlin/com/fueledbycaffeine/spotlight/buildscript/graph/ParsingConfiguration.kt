package com.fueledbycaffeine.spotlight.buildscript.graph

/**
 * Configuration for parsing build scripts.
 * 
 * Specifies whether to use regex-based parsing (fast, simple)
 * or AST/PSI-based parsing (accurate, slower) when analyzing build scripts.
 */
public enum class ParsingConfiguration {
  /**
   * Fast regex-based parsing for maximum performance.
   * Uses simple pattern matching to extract dependencies.
   */
  REGEX,
  
  /**
   * Accurate AST/PSI-based parsing.
   * Attempts to parse using Groovy AST or Kotlin PSI for precise dependency extraction.
   * Falls back to regex if AST/PSI parsing fails.
   */
  AST;
  
  public val useRegex: Boolean get() = this == REGEX
  
  public companion object {
    /**
     * Default configuration that uses regex parsing for maximum performance.
     */
    public val DEFAULT: ParsingConfiguration = REGEX
  }
}
