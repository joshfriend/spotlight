package com.fueledbycaffeine.buildlogic

import com.fueledbycaffeine.autoservice.gradle.AutoServiceGradlePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

class AutoserviceConventionPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(AutoServiceGradlePlugin::class.java)
  }
}
