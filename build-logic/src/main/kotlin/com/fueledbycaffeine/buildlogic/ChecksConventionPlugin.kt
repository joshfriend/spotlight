package com.fueledbycaffeine.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

class ChecksConventionPlugin : Plugin<Project> {
  @OptIn(ExperimentalAbiValidation::class)
  override fun apply(project: Project): Unit = with(project) {
    pluginManager.apply(KotlinConventionPlugin::class.java)
    extensions.getByType(KotlinJvmProjectExtension::class.java).apply {
      explicitApi()
      abiValidation { }
    }

    val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
    dependencies.apply {
      add("testImplementation", libs.findLibrary("assertk").get())
      add("testImplementation", platform(libs.findLibrary("junit-platform").get()))
      add("testImplementation", libs.findLibrary("junit-jupiter").get())
      add("testRuntimeOnly", libs.findLibrary("junit-launcher").get())
    }
    tasks.withType(Test::class.java).configureEach {
      it.useJUnitPlatform()
    }
  }
}
