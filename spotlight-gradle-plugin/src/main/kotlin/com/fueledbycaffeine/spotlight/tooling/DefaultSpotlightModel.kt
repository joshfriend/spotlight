package com.fueledbycaffeine.spotlight.tooling

import com.fueledbycaffeine.spotlight.buildscript.models.SpotlightModel
import java.io.Serializable

/**
 * Default implementation of [SpotlightModel] that is serializable and can be transferred
 * from Gradle to the IDE via Gradle Tooling API.
 */
internal data class DefaultSpotlightModel(
  override val includedProjectPaths: Set<String>
) : SpotlightModel, Serializable
