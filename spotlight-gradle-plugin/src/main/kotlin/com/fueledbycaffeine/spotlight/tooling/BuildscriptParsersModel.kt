package com.fueledbycaffeine.spotlight.tooling

import java.io.Serializable

/**
 * Tooling API model that provides build script parsing metadata to the IDE.
 *
 * IMPORTANT: Tooling models are deserialized on the IDE side with a restricted classpath.
 * Avoid exposing implementation types from other modules (e.g. BuildscriptParserProvider)
 * unless you're certain they are present for deserialization.
 */
public interface BuildscriptParsersModel : Serializable {

  /**
   * List of parser provider metadata discovered via ServiceLoader.
   * Sorted by priority (highest first).
   */
  public val providers: List<BuildscriptParserProviderInfo>
}

/**
 * A minimal, tooling-safe representation of a parser provider.
 */
public data class BuildscriptParserProviderInfo(
  val implementationClassName: String,
  val priority: Int,
  val mode: String
) : Serializable

internal data class BuildscriptParsersModelImpl(
  override val providers: List<BuildscriptParserProviderInfo>
) : BuildscriptParsersModel