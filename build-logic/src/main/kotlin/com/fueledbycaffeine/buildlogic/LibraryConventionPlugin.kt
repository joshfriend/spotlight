package com.fueledbycaffeine.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class LibraryConventionPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(ChecksConventionPlugin::class.java)
    project.pluginManager.apply(PublishingConventionPlugin::class.java)
  }
}
