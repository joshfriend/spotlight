package com.fueledbycaffeine.spotlight.buildscript.parser.kotlin

import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT_KOTLIN
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildScriptParserProvider
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildScriptParser
import java.nio.file.Path
import kotlin.io.path.name

/**
 * Service provider for the Kotlin PSI parser.
 * This enables automatic discovery of the Kotlin parser via Java's ServiceLoader.
 */
public class KotlinPsiParserProvider : BuildScriptParserProvider {
  override fun getParser(buildFilePath: Path): BuildScriptParser? {
    return if (buildFilePath.name == GRADLE_SCRIPT_KOTLIN) {
      KotlinPsiParser
    } else {
      null
    }
  }
  
  override fun getPriority(): Int = 100
}
