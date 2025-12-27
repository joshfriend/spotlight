package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.GradlePath

/**
 * Service provider for the regex-based build script parser.
 * This is the fallback parser that works for both Groovy and Kotlin build scripts.
 */
public class RegexBuildscriptParserProvider : BuildscriptParserProvider {
  override fun getParser(): BuildscriptParser = RegexBuildscriptParser

  override val priority: Int = 0 // Lowest priority - fallback parser
}
