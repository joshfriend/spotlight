package com.fueledbycaffeine.spotlight.buildscript.parser.impl

import com.fueledbycaffeine.spotlight.buildscript.parser.TaskRequestParser
import java.util.ServiceLoader

/**
 * Service Provider Interface for task request parsers.
 *
 * Implementations of this interface can be discovered via Java's [ServiceLoader] mechanism,
 * allowing different parser implementations (regex, AST, PSI) to be loaded at runtime if present
 * on the classpath.
 *
 * All parsers are additive - they all run and their results are merged together.
 */
public interface TaskRequestParserProvider {
  /**
   * Get the parser instance provided by this provider.
   *
   * @return A [TaskRequestParser] implementation
   */
  public fun getParser(): TaskRequestParser
}
