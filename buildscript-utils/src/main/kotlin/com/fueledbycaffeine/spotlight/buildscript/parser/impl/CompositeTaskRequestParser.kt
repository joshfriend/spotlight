package com.fueledbycaffeine.spotlight.buildscript.parser.impl

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.parser.TaskRequestParser

/**
 * A composite parser that runs multiple [TaskRequestParser]s and merges their results.
 */
internal class CompositeTaskRequestParser(
  private val parsers: List<TaskRequestParser>,
) : TaskRequestParser {
  override fun parse(taskNames: Set<String>): Set<GradlePath> {
    return parsers.flatMap { parser -> parser.parse(taskNames) }.toSet()
  }
}