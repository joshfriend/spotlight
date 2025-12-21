package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.BUILDSCRIPTS
import java.nio.file.Path
import kotlin.io.path.name

/**
 * Service provider for the regex-based build script parser.
 * This is the fallback parser that works for both Groovy and Kotlin build scripts.
 */
internal class RegexBuildScriptParserProvider : BuildScriptParserProvider {
  override fun getParser(buildFilePath: Path): BuildScriptParser? {
    return when (buildFilePath.name) {
      in BUILDSCRIPTS -> RegexBuildScriptParser
      else -> null
    }
  }
  
  override fun getPriority(): Int = 0 // Lowest priority - fallback parser
}
