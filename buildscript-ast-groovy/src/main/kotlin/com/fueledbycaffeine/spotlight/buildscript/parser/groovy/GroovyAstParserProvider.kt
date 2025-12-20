package com.fueledbycaffeine.spotlight.buildscript.parser.groovy

import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT
import com.fueledbycaffeine.spotlight.buildscript.parser.AstBuildScriptParserProvider
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildScriptParser

/**
 * Service provider for the Groovy AST parser.
 * This enables automatic discovery of the Groovy parser via Java's ServiceLoader.
 */
public class GroovyAstParserProvider : AstBuildScriptParserProvider {
  override fun getParser(fileExtension: String): BuildScriptParser? {
    return if (fileExtension == GRADLE_SCRIPT) {
      GroovyAstParser
    } else {
      null
    }
  }
  
  override fun getPriority(): Int = 100
}
