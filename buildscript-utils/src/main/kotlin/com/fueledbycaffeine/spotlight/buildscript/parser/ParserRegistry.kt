package com.fueledbycaffeine.spotlight.buildscript.parser

import java.nio.file.Path
import java.util.ServiceLoader

/**
 * Registry for discovering and managing build script parsers via Java's ServiceLoader.
 * This allows different parser implementations to be loaded at runtime if present on the classpath.
 */
internal object ParserRegistry {
  private val providers: List<BuildScriptParserProvider> by lazy {
    ServiceLoader.load(
      BuildScriptParserProvider::class.java,
      BuildScriptParserProvider::class.java.classLoader
    ).toList().sortedByDescending { it.getPriority() }
  }
  
  /**
   * Find a parser for the given build file path.
   * Providers are checked in priority order (highest first).
   * Returns null if no provider supports the build file.
   */
  fun findParser(buildFilePath: Path): BuildScriptParser? {
    for (provider in providers) {
      val parser = provider.getParser(buildFilePath)
      if (parser != null) {
        return parser
      }
    }
    return null
  }
}
