package com.fueledbycaffeine.buildlogic

import com.android.build.gradle.LintPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

class LintConventionPlugin : Plugin<Project> {
  override fun apply(project: Project): Unit = with(project) {
    pluginManager.apply(LintPlugin::class.java)
    val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
    dependencies.add("lintChecks", libs.findLibrary("lint-gradle").get())
  }
}
