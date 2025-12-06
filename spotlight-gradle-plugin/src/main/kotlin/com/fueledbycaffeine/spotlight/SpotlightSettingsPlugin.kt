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
    // Create the extension
    val extension = extensions.getSpotlightExtension()
    // DSL is not available until then
    gradle.settingsEvaluated {
      val includedProjects = SpotlightIncludedProjectsValueSource.of(this, extension)
      include(includedProjects.get())
    }
  }
}