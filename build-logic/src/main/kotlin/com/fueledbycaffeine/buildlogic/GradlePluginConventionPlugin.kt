package com.fueledbycaffeine.buildlogic

import com.autonomousapps.DependencyAnalysisSubExtension
import com.autonomousapps.GradleTestKitSupportExtension
import com.gradle.publish.PublishPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.attributes.plugin.GradlePluginApiVersion
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.plugin.compatibility.compatibility
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin
import org.gradle.plugin.devel.tasks.ValidatePlugins

class GradlePluginConventionPlugin : Plugin<Project> {
  override fun apply(project: Project): Unit = with(project) {
    pluginManager.apply(JavaGradlePluginPlugin::class.java)
    pluginManager.apply(PublishPlugin::class.java)
    pluginManager.apply(LibraryConventionPlugin::class.java)
    pluginManager.apply(LintConventionPlugin::class.java)

    extensions.create(
      "conventions",
      GradlePluginConventionsExtension::class.java,
      extensions.getByType(GradlePluginDevelopmentExtension::class.java),
      extensions.getByType(PublishingExtension::class.java),
    )

    extensions.getByType(GradlePluginDevelopmentExtension::class.java).apply {
      vcsUrl.set("https://github.com/joshfriend/spotlight")
      website.set("https://github.com/joshfriend/spotlight")
      plugins.configureEach { plugin ->
        plugin.compatibility {
          it.features.isolatedProjects.set(true)
          it.features.configurationCache.set(true)
        }
      }
    }

    val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
    extensions.getByType(GradleTestKitSupportExtension::class.java).apply {
      withSupportLibrary(libs.findVersion("testkit-support").get().requiredVersion)
      withTruthLibrary()
    }
    extensions.getByType(DependencyAnalysisSubExtension::class.java).issues { issues ->
      issues.onIncorrectConfiguration {
        it.exclude(libs.findLibrary("autonomousapps-testkit-support").get())
      }
    }

    listOf("runtimeElements", "apiElements").forEach { configurationName ->
      configurations.named(configurationName).configure {
        it.attributes.attribute(
          GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE,
          objects.named(GradlePluginApiVersion::class.java, libs.findVersion("minGradle").get().requiredVersion)
        )
      }
    }

    val sourceSets = extensions.getByType(SourceSetContainer::class.java)
    val main = sourceSets.getByName("main")
    sourceSets.named("functionalTest").configure {
      it.compileClasspath += main.output + configurations.getByName("testRuntimeClasspath")
      it.runtimeClasspath += it.output + it.compileClasspath
    }

    // https://github.com/gradle/gradle/issues/29483#issuecomment-2791668178
    val gradleApi = dependencies.create("org.gradle.experimental:gradle-public-api:${gradle.gradleVersion}")
      as ExternalModuleDependency
    gradleApi.capabilities { it.requireCapability("org.gradle.experimental:gradle-public-api-internal") }
    dependencies.add("compileOnly", gradleApi)

    tasks.withType(ValidatePlugins::class.java).configureEach {
      it.enableStricterValidation.set(true)
    }
    val gradleVersion = providers.systemProperty("gradleVersion").orNull
    val develocityVersion = libs.findVersion("develocity").get().requiredVersion
    tasks.withType(Test::class.java).configureEach {
      it.systemProperty("gradleVersion", gradleVersion)
      it.systemProperty("develocityVersion", develocityVersion)
    }
  }
}
