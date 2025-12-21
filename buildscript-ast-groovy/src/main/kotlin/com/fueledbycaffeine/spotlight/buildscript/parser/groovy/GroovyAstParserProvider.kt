package com.fueledbycaffeine.spotlight.buildscript.parser.groovy

import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildScriptParserProvider
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildScriptParser
import java.nio.file.Path
import kotlin.io.path.name

/**
 * Service provider for the Groovy AST parser.
 * This enables automatic discovery of the Groovy parser via Java's ServiceLoader.
 */
public class GroovyAstParserProvider : BuildScriptParserProvider {
  override fun getParser(buildFilePath: Path): BuildScriptParser? {
    return if (buildFilePath.name == GRADLE_SCRIPT) {
      GroovyAstParser
    } else {
      null
    }
  }
  
  override fun getPriority(): Int = 100
}
