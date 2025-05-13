package com.fueledbycaffeine.spotlight.fixtures

import com.autonomousapps.kit.AbstractGradleProject
import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.gradle.Dependency.Companion.implementation
import com.autonomousapps.kit.gradle.Plugin
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
      .withSubproject("eternal-blue") { }
      .withSubproject("tsunami-sea") { }
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
val GradleProject.allProjects: File get() = rootDir.resolve(SpotlightExtension.ALL_PROJECTS_FILE)
val GradleProject.ideProjects: File get() = rootDir.resolve(SpotlightExtension.IDE_PROJECTS_FILE)