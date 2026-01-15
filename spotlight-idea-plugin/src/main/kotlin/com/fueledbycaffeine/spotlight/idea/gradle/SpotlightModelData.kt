package com.fueledbycaffeine.spotlight.idea.gradle

import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import java.io.Serializable

/**
 * Data node containing Spotlight information fetched from Gradle during sync.
 * 
 * The presence of this data node indicates that Spotlight is enabled.
 * The absence means Spotlight is disabled (plugin not applied).
 */
data class SpotlightModelData(
  /**
   * Set of Gradle project paths that are included in the build.
   * Format: Gradle path strings (e.g., ":", ":app", ":libraries:core")
   */
  val includedProjectPaths: Set<String>
) : Serializable {
  companion object {
    @JvmField
    val KEY: Key<SpotlightModelData> = Key.create(
      SpotlightModelData::class.java,
      ProjectKeys.PROJECT.processingWeight + 2
    )
  }
}
