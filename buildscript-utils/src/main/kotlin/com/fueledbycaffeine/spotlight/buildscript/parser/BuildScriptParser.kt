package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule

/**
 * Interface for parsing build scripts to extract project dependencies.
 */
public interface BuildScriptParser {
  /**
   * Parse a build script and extract project dependencies.
   *
   * @param project The gradle project to parse
   * @param rules Additional dependency rules to apply
   * @return Set of gradle paths representing project dependencies
   */
  public fun parse(
    project: GradlePath,
    rules: Set<DependencyRule>,
  ): Set<GradlePath>

  public class ParserException(message: String, e: Throwable) : Exception(message, e)
}
