package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.parser.ServiceLoaderParserRegistry

public data class TaskRequests(
  public val taskNames: Set<String>,
  public val allProjects: Set<GradlePath>,
) {
  /**
   * Parse dependencies from the task names (from TaskExecutionRequest). Returns an empty set if there is no
   * TaskRequestParser.
   */
  public fun parseDependencies(): Set<GradlePath> {
    // There is no task request parser by default (also no default rules for this). So, null is not an error.
    val parser = ServiceLoaderParserRegistry.findTaskRequestParser()
    return parser?.parse(taskNames).orEmpty()
  }
}
