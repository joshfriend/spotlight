package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule

/**
 * Interface for parsing build scripts to extract project dependencies.
 */
public interface BuildscriptParser {
  /**
   * Parse a build script and extract project dependencies.
   *
   * @param project The gradle project to parse
   * @param rules Additional dependency rules to apply
   * @return Set of [GradlePath]s representing project dependencies
   */
  public fun parse(
    project: GradlePath,
    rules: Set<DependencyRule>,
  ): Set<GradlePath>
}
