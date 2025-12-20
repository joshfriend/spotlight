package com.fueledbycaffeine.spotlight.buildscript.parser.kotlin

import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT_KOTLIN
import com.fueledbycaffeine.spotlight.buildscript.parser.AstBuildScriptParserProvider
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildScriptParser

/**
 * Service provider for the Kotlin PSI parser.
 * This enables automatic discovery of the Kotlin parser via Java's ServiceLoader.
 */
public class KotlinPsiParserProvider : AstBuildScriptParserProvider {
  override fun getParser(fileExtension: String): BuildScriptParser? {
    return if (fileExtension == GRADLE_SCRIPT_KOTLIN) {
      KotlinPsiParser
    } else {
      null
    }
  }
  
  override fun getPriority(): Int = 100
}
