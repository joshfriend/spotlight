package com.fueledbycaffeine.spotlight.functionaltest.fixtures

import com.autonomousapps.kit.AbstractGradleProject
import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.gradle.Dependency.Companion.implementation
import com.autonomousapps.kit.gradle.Plugin
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import com.fueledbycaffeine.spotlight.dsl.SpotlightExtension
import java.io.File

private val INCLUDE_PATTERN = Regex("include\\(?\\s+?[\"'](\\S+)[\"']")

class SpiritboxProject : AbstractGradleProject() {
  fun build(): GradleProject {
    val project = newGradleProjectBuilder()
      .withRootProject {
        withSettingsScript {
          rootProjectName = "spiritbox"
          plugins(Plugin("com.fueledbycaffeine.spotlight", PLUGIN_UNDER_TEST_VERSION))
        }
      }
      .withSubproject(":rotoscope:hysteria") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":rotoscope:rotoscope") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":rotoscope:sew-me-up") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":rotoscope") {
        withBuildScript {
          plugins(Plugin.javaLibrary)
          dependencies(
            implementation(":rotoscope:hysteria"),
            implementation(":rotoscope:rotoscope"),
            implementation(":rotoscope:sew-me-up"),
          )
        }
      }
      .withSubproject(":the-fear-of-fear:cellar-door") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":the-fear-of-fear:jaded") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":the-fear-of-fear:too-close-too-late") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":the-fear-of-fear:angel-eyes") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":the-fear-of-fear:the-void") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":the-fear-of-fear:ultraviolet") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":the-fear-of-fear") {
        withBuildScript {
          plugins(Plugin.javaLibrary)
          dependencies(
            implementation(":the-fear-of-fear:cellar-door"),
            implementation(":the-fear-of-fear:jaded"),
            implementation(":the-fear-of-fear:too-close-too-late"),
            implementation(":the-fear-of-fear:angel-eyes"),
            implementation(":the-fear-of-fear:the-void"),
            implementation(":the-fear-of-fear:ultraviolet"),
          )
        }
      }
      .withSubproject(":eternal-blue:sun-killer") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue:hurt-you") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue:yellowjacket") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue:the-summit") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue:secret-garden") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue:silk-in-the-strings") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue:holy-roller") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue:eternal-blue") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue:we-live-in-a-strange-world") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue:halcyon") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue:circle-with-me") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue:constance") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue") {
        withBuildScript {
          plugins(Plugin.javaLibrary)
          dependencies(
            implementation(":eternal-blue:sun-killer"),
            implementation(":eternal-blue:hurt-you"),
            implementation(":eternal-blue:yellowjacket"),
            implementation(":eternal-blue:the-summit"),
            implementation(":eternal-blue:secret-garden"),
            implementation(":eternal-blue:silk-in-the-strings"),
            implementation(":eternal-blue:holy-roller"),
            implementation(":eternal-blue:eternal-blue"),
            implementation(":eternal-blue:we-live-in-a-strange-world"),
            implementation(":eternal-blue:halcyon"),
            implementation(":eternal-blue:circle-with-me"),
            implementation(":eternal-blue:constance"),
          )
        }
      }
      .withSubproject(":tsunami-sea") { }
      .write()

    val settings = project.rootDir.resolve("settings.gradle")
    val settingsContents = settings.readText()
    val projectPaths = INCLUDE_PATTERN.findAll(settingsContents)
      .map { it ->
        val (path) = it.destructured
        path
      }
    val strippedSettings = settingsContents.lines().filterNot { "include" in it }
    settings.writeText(strippedSettings.joinToString("\n"))

    project.gradleDir.mkdirs()
    project.allProjects.createNewFile()
    project.ideProjects.createNewFile()
    project.allProjects.writeText(projectPaths.joinToString("\n"))

    return project
  }
}

val GradleProject.gradleDir: File get() = rootDir.resolve("gradle")
val GradleProject.allProjects: File get() = rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
val GradleProject.ideProjects: File get() = rootDir.resolve(SpotlightProjectList.IDE_PROJECTS_LOCATION)