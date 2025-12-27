package com.fueledbycaffeine.spotlight.buildscript.parser.groovy

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildscriptParserProvider
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildscriptParser

/**
 * Service provider for the Groovy AST parser.
 * This enables automatic discovery of the Groovy parser via Java's ServiceLoader.
 */
public class GroovyAstParserProvider : BuildscriptParserProvider {
  override fun getParser(): BuildscriptParser = GroovyAstParser

  override val priority: Int = 100
}
