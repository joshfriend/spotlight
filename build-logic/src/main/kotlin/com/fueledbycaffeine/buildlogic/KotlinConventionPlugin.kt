package com.fueledbycaffeine.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper

class KotlinConventionPlugin : Plugin<Project> {
  override fun apply(project: Project): Unit = with(project) {
    pluginManager.apply(KotlinPluginWrapper::class.java)
    val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
    val javaTarget = libs.findVersion("javaTarget").get().requiredVersion

    extensions.getByType(KotlinJvmProjectExtension::class.java).compilerOptions.apply {
      allWarningsAsErrors.set(true)
      jvmTarget.set(JvmTarget.fromTarget(javaTarget))
    }
    extensions.getByType(JavaPluginExtension::class.java).toolchain.languageVersion.set(
      JavaLanguageVersion.of(libs.findVersion("jdk").get().requiredVersion)
    )
    tasks.withType(JavaCompile::class.java).configureEach {
      it.options.release.set(javaTarget.toInt())
    }
  }
}
