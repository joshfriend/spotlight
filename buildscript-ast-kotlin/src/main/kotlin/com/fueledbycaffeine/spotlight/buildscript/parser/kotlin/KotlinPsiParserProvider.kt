package com.fueledbycaffeine.spotlight.buildscript.parser.kotlin

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildscriptParserProvider
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildscriptParser

/**
 * Service provider for the Kotlin PSI parser.
 * This enables automatic discovery of the Kotlin parser via Java's ServiceLoader.
 */
public class KotlinPsiParserProvider : BuildscriptParserProvider {
  override fun getParser(): BuildscriptParser = KotlinPsiParser

  override val priority: Int = 100
}
