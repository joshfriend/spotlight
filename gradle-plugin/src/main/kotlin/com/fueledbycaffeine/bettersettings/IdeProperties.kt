package com.fueledbycaffeine.bettersettings

import org.gradle.api.initialization.Settings
import org.gradle.api.provider.ProviderFactory

private const val PROP_SYNC_ACTIVE = "idea.sync.active"

val ProviderFactory.isIdeSync: Boolean
  get() = gradleProperty(PROP_SYNC_ACTIVE)
    .getOrElse("false").toBoolean()

val Settings.isIdeSync get() = providers.isIdeSync