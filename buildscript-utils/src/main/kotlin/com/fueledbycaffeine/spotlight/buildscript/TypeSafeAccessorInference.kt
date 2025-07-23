package com.fueledbycaffeine.spotlight.buildscript

public enum class TypeSafeAccessorInference {
  /**
   * No processing of type-safe project accessors is performed
   */
  DISABLED,

  /**
   * Enables some type-safe project accessor processing, but only if project paths are lowercase and kebab-case.
   */
  STRICT,

  /**
   * Enables full processing of type-safe project accessors, with no limitations on path names.
   *
   * Note that this causes [SpotlightProjectList.ALL_PROJECTS_LOCATION] to be captured in the configuration cache because this file must be
   * read to fully compute the mapping of type-safe project accessor names to Gradle project paths.
   */
  FULL,
}