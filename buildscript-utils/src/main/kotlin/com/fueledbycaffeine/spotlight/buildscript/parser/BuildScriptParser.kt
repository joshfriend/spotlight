package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import java.io.Serializable

/**
 * Interface for parsing build scripts to extract project dependencies.
 *
 * Implementations must be Serializable so they can be transferred from
 * the Gradle build process to the IDE plugin via the Tooling API.
 */
public interface BuildscriptParser : Serializable {
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
