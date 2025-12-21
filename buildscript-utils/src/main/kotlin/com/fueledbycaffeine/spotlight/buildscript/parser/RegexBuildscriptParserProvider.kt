package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.GradlePath

/**
 * Service provider for the regex-based build script parser.
 * This is the fallback parser that works for both Groovy and Kotlin build scripts.
 */
public class RegexBuildscriptParserProvider : BuildscriptParserProvider {
  override fun getParser(project: GradlePath): BuildScriptParser {
    // Always return the parser - it will handle checking for build files internally
    // without doing file I/O outside of ValueSource
    return RegexBuildScriptParser
  }
  
  override val priority: Int = 0 // Lowest priority - fallback parser
}
