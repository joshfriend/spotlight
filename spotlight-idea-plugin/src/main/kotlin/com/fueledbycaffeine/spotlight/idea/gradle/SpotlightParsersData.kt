package com.fueledbycaffeine.spotlight.idea.gradle

import com.fueledbycaffeine.spotlight.tooling.BuildscriptParserProviderInfo
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import java.io.Serializable

/**
 * Data node containing BuildScript parser provider metadata received during Gradle sync.
 */
data class SpotlightParsersData(
  /**
   * List of parser providers (sorted by priority)
   */
  val providers: List<BuildscriptParserProviderInfo>
) : Serializable {
  companion object {
    @JvmField
    val KEY: Key<SpotlightParsersData> = Key.create(
      SpotlightParsersData::class.java,
      ProjectKeys.PROJECT.processingWeight + 1
    )
  }
}
