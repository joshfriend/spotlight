package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.GradlePath

/**
 * Interface for parsing tasks requests from the command line, to extract project dependencies.
 */
public interface TaskRequestParser {
  /**
   * Parse a build script and extract project dependencies.
   *
   * @param taskNames The names of the tasks requested (as in `StartParameter::getTaskNames`)
   * @return Set of [GradlePath]s representing project dependencies
   */
  public fun parse(taskNames: Set<String>): Set<GradlePath>
}
