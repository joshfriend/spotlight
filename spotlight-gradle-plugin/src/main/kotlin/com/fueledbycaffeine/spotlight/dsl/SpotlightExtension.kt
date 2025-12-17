package com.fueledbycaffeine.spotlight.dsl

import com.fueledbycaffeine.spotlight.SpotlightSettingsPlugin
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.IDE_PROJECTS_LOCATION
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.file.BuildLayout
import org.gradle.api.initialization.Settings
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Configuration options for [SpotlightSettingsPlugin].
 */
@Suppress("UnstableApiUsage")
public abstract class SpotlightExtension @Inject constructor(
  private val layout: BuildLayout,
  objects: ObjectFactory,
) {
  public companion object {
    public const val NAME: String = "spotlight"

    @JvmStatic
    public fun ExtensionContainer.getSpotlightExtension(): SpotlightExtension {
      return try {
        getByType(SpotlightExtension::class.java)
      } catch (_: UnknownDomainObjectException) {
        create(NAME, SpotlightExtension::class.java)
      }
    }
  }

  /**
   * Override the inferred target projects or projects from [IDE_PROJECTS_LOCATION].
   *
   * The value should be a provider where the value is a comma or newline separated list of Gradle project paths.
   *
   * This is useful for gradle-profiler scenarios where generating the IDE target projects list is not possible or for
   * running a task on a specific subset of projects without fully specifying the task path of each task.
   *
   * ``` kotlin
   * def targetProjects = providers.gradleProperty("target-projects")
   *
   * spotlight {
   *   targetsOverride = targetProjects
   * }
   * ```
   */
  public val targetsOverride: Property<String> =
    objects.property(String::class.java).unsetConvention()
}
