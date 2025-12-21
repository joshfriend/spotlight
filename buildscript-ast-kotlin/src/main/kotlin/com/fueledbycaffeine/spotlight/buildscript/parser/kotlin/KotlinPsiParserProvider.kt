package com.fueledbycaffeine.spotlight.buildscript.parser.kotlin

import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT_KOTLIN
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildscriptParserProvider
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildScriptParser

/**
 * Service provider for the Kotlin PSI parser.
 * This enables automatic discovery of the Kotlin parser via Java's ServiceLoader.
 */
public class KotlinPsiParserProvider : BuildscriptParserProvider {
  override fun getParser(project: GradlePath): BuildScriptParser? {
    // Always return the parser - it will check for Kotlin build files internally
    // The Groovy or regex parser will handle Groovy files if this parser doesn't match
    return KotlinPsiParser
  }
  
  override val priority: Int = 100
}
