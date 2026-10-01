package com.fueledbycaffeine.buildlogic

import com.autonomousapps.DependencyAnalysisSubExtension
import dev.zacsweers.moshix.ir.gradle.MoshiGradleSubplugin
import dev.zacsweers.moshix.ir.gradle.MoshiPluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

class MoshiConventionPlugin : Plugin<Project> {
  override fun apply(project: Project): Unit = with(project) {
    pluginManager.apply(MoshiGradleSubplugin::class.java)
    extensions.getByType(MoshiPluginExtension::class.java).enableSealed.set(true)

    val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
    extensions.getByType(DependencyAnalysisSubExtension::class.java).issues { issues ->
      issues.onUnusedDependencies {
        // The Moshix IR compiler adds these dependencies.
        it.exclude(libs.findLibrary("moshix-runtime").get())
        it.exclude(libs.findLibrary("moshi").get())
        it.exclude(libs.findLibrary("moshix-sealed-runtime").get())
      }
      issues.onIncorrectConfiguration {
        it.exclude(libs.findLibrary("moshi").get())
        it.exclude(libs.findLibrary("moshix-sealed-runtime").get())
      }
    }
  }
}
