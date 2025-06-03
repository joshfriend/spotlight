package com.fueledbycaffeine.spotlight

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Provides basic info about the how [SpotlightSettingsPlugin] is configuring the build.
 */
public abstract class SpotlightBuildService : BuildService<SpotlightBuildService.Params> {
  public interface Params : BuildServiceParameters {
    public val enabled: Property<Boolean>
    public val includedProjects: SetProperty<GradlePath>
  }

  @Suppress("UNCHECKED_CAST")
  public companion object {
    public const val NAME: String = "spotlightBuildService"

    @JvmStatic
    public fun of(settings: Settings): Provider<SpotlightBuildService> {
      return settings.gradle.sharedServices.registrations
        .named(NAME).get().service as Provider<SpotlightBuildService>
    }

    @JvmStatic
    public fun of(project: Project): Provider<SpotlightBuildService> {
      return project.gradle.sharedServices.registrations
        .named(NAME).get().service as Provider<SpotlightBuildService>
    }
  }
}