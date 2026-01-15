package com.fueledbycaffeine.spotlight.buildscript.models

import java.io.Serializable

/**
 * Model provided by the Spotlight Gradle plugin via Gradle Tooling API.
 */
public interface SpotlightModel : Serializable {
  /**
   * Set of Gradle project paths that are included in the build.
   * Format: Gradle path strings (e.g., ":", ":app", ":libraries:core")
   */
  public val includedProjectPaths: Set<String>
}
