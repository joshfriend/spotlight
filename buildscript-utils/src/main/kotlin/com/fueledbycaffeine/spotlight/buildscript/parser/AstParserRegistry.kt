package com.fueledbycaffeine.spotlight.buildscript.parser

import java.util.ServiceLoader

/**
 * Registry for discovering and managing AST-based build script parsers via Java's ServiceLoader.
 * This allows optional AST parser implementations to be loaded at runtime if present on the classpath.
 */
internal object AstParserRegistry {
  private val providers: List<AstBuildScriptParserProvider> by lazy {
    ServiceLoader.load(
      AstBuildScriptParserProvider::class.java,
      AstBuildScriptParserProvider::class.java.classLoader
    ).toList().sortedByDescending { it.getPriority() }
  }
  
  /**
   * Find a parser for the given file extension.
   * Returns null if no provider supports the extension.
   */
  fun findParser(fileExtension: String): BuildScriptParser? {
    for (provider in providers) {
      val parser = provider.getParser(fileExtension)
      if (parser != null) {
        return parser
      }
    }
    return null
  }
  
  /**
   * Check if any AST parser is available.
   */
  fun hasAnyParser(): Boolean = providers.isNotEmpty()
}
