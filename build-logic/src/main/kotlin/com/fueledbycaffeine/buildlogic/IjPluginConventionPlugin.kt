package com.fueledbycaffeine.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformRepositoriesExtension
import org.jetbrains.intellij.platform.gradle.plugins.project.IntelliJPlatformPlugin
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class IjPluginConventionPlugin : Plugin<Project> {
  override fun apply(project: Project): Unit = with(project) {
    pluginManager.apply(KotlinConventionPlugin::class.java)
    pluginManager.apply(IntelliJPlatformPlugin::class.java)

    repositories.mavenCentral()
    (repositories as ExtensionAware).extensions
      .getByType(IntelliJPlatformRepositoriesExtension::class.java).defaultRepositories()

    extensions.getByType(KotlinJvmProjectExtension::class.java).compilerOptions.apply {
      // sinceBuild 252 (2025.2) runs on JBR 21.
      jvmTarget.set(JvmTarget.JVM_21)
      // Inherit platform defaults without compatibility bridges to deprecated APIs.
      jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
    }
    tasks.withType(JavaCompile::class.java).configureEach {
      it.options.release.set(21)
    }

    val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
    dependencies.add("testImplementation", libs.findLibrary("junit4").get())
    dependencies.add("testImplementation", libs.findLibrary("truth").get())

    val baseVersion = providers.gradleProperty("VERSION_NAME").get()
    val isSnapshot = baseVersion.endsWith("SNAPSHOT")
    version = if (isSnapshot) "$baseVersion-${System.currentTimeMillis()}" else baseVersion

    extensions.getByType(IntelliJPlatformExtension::class.java).apply {
      caching.ides.enabled.set(true)
      pluginConfiguration.ideaVersion.sinceBuild.set("252")
      publishing.token.set(
        providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")
          .orElse(providers.gradleProperty("jetbrainsMarketplaceToken"))
      )
      publishing.channels.set(listOf(if (isSnapshot) "EAP" else "Stable"))
    }
  }
}
