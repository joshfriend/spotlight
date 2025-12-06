package com.fueledbycaffeine.spotlight

import com.fueledbycaffeine.spotlight.dsl.SpotlightExtension.Companion.getSpotlightExtension
import com.fueledbycaffeine.spotlight.utils.include
import com.fueledbycaffeine.spotlight.utils.isIdeSync
import com.fueledbycaffeine.spotlight.utils.isSpotlightEnabled
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

/**
 * A [Settings] plugin to ease management of projects included in large builds.
 *
 * plugins {
 *   id 'com.fueledbycaffeine.spotlight'
 * }
 */
public class SpotlightSettingsPlugin: Plugin<Settings> {
  public override fun apply(settings: Settings): Unit = settings.run {
    val spotlightOptions = extensions.getSpotlightExtension()
    // DSL is not available until then
    gradle.settingsEvaluated {
      val includedProjects = providers.of(SpotlightIncludedProjectsValueSource::class.java) {
        it.parameters.rootDirectory.set(settingsDir)
        it.parameters.rootProjectName.set(settings.rootProject.name)
        it.parameters.projectDir.set(gradle.startParameter.projectDir)
        it.parameters.taskRequests.set(gradle.startParameter.taskRequests)
        it.parameters.targetsOverride.set(spotlightOptions.targetsOverride)
        it.parameters.typeSafeAccessorInference.set(spotlightOptions.typeSafeAccessorInference)
        it.parameters.ideSync.set(isIdeSync)
        it.parameters.spotlightEnabled.set(isSpotlightEnabled)
      }
      include(includedProjects.get())
    }
  }
}