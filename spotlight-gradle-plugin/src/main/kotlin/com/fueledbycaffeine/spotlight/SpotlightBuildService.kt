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
    public val spotlightProjects: SetProperty<GradlePath>
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

  /**
   * Indicates if Spotlight is enabled for this build.
   */
  public val isEnabled: Boolean get() = parameters.enabled.get()

  /**
   * The set of all projects loaded in the current build, including empty intermediate projects.
   */
  public val allProjects: Set<GradlePath> get() = parameters.spotlightProjects.get()

  /**
   * The set of real projects loaded in the current build, which excludes empty intermediate projects.
   *
   * "Real" projects contain a buildscript
   */
  public val realProjects: Set<GradlePath> by lazy {
    allProjects.filter { it.hasBuildFile }.toSet()
  }
}