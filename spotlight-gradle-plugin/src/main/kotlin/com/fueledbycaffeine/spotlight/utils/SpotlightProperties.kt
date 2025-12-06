@file:JvmName("SpotlightProperties")

package com.fueledbycaffeine.spotlight.utils

import org.gradle.api.initialization.Settings
import org.gradle.api.provider.ProviderFactory

private const val SPOTLIGHT_ENABLED_PROPERTY = "spotlight.enabled"

private fun ProviderFactory.flagFromGradleOrSystemProperty(property: String, default: Boolean = false): Boolean {
  return gradleProperty(property).orElse(systemProperty(property))
    .map { it.toBoolean() }
    .getOrElse(default)
}

internal val ProviderFactory.isSpotlightEnabled: Boolean
  get() = flagFromGradleOrSystemProperty(SPOTLIGHT_ENABLED_PROPERTY, default = true)

public val Settings.isSpotlightEnabled: Boolean get() = providers.isSpotlightEnabled