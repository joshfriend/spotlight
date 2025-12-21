package com.fueledbycaffeine.spotlight.buildscript.parser.groovy

import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildscriptParserProvider
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildScriptParser

/**
 * Service provider for the Groovy AST parser.
 * This enables automatic discovery of the Groovy parser via Java's ServiceLoader.
 */
public class GroovyAstParserProvider : BuildscriptParserProvider {
  override fun getParser(project: GradlePath): BuildScriptParser? {
    // Always return the parser for now - it will check for Groovy build files internally
    // The regex parser will handle Kotlin files if this parser doesn't match
    return GroovyAstParser
  }
  
  override val priority: Int = 100
}
