package com.fueledbycaffeine.spotlight.idea.gradle

import com.fueledbycaffeine.spotlight.buildscript.models.SpotlightModel
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import java.io.Serializable

/**
 * Data node containing Spotlight information fetched from Gradle during sync.
 * 
 * The presence of this data node indicates that Spotlight is enabled.
 * The absence means Spotlight is disabled (plugin not applied).
 */
data class SpotlightIdeModelData(
  /**
   * Set of Gradle project paths that are included in the build.
   * Format: Gradle path strings (e.g., ":", ":app", ":libraries:core")
   */
  override val includedProjectPaths: Set<String>
) : SpotlightModel, Serializable {
  companion object {
    @JvmField
    val KEY: Key<SpotlightIdeModelData> = Key.create(
      SpotlightIdeModelData::class.java,
      ProjectKeys.PROJECT.processingWeight + 2
    )
  }
}
