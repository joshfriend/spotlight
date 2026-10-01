package com.fueledbycaffeine.buildlogic

import com.autonomousapps.BuildHealthPlugin
import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import com.gradle.develocity.agent.gradle.DevelocityPlugin
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.toolchains.foojay.FoojayToolchainsConventionPlugin
import java.net.URI

class SettingsConventionPlugin : Plugin<Settings> {
  override fun apply(settings: Settings): Unit = with(settings) {
    pluginManager.apply(DevelocityPlugin::class.java)
    pluginManager.apply(FoojayToolchainsConventionPlugin::class.java)
    pluginManager.apply(BuildHealthPlugin::class.java)

    dependencyResolutionManagement.repositories.apply {
      mavenCentral()
      google()
      gradlePluginPortal()
      maven { repository ->
        repository.url = URI("https://repo.gradle.org/gradle/libs-releases")
        repository.content {
          it.includeModule("org.gradle.experimental", "gradle-public-api")
        }
      }
    }

    val isCi = !providers.environmentVariable("CI").orNull.isNullOrEmpty()
    extensions.getByType(DevelocityConfiguration::class.java).buildScan.apply {
      publishing.onlyIf { true }
      termsOfUseUrl.set("https://gradle.com/terms-of-service")
      termsOfUseAgree.set("yes")
      tag(if (isCi) "CI" else "Local")
    }
  }
}
