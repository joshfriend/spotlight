@file:Suppress("UnstableApiUsage")

package com.fueledbycaffeine.spotlight

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import com.fueledbycaffeine.spotlight.dsl.SpotlightExtension.Companion.getSpotlightExtension
import com.fueledbycaffeine.spotlight.tasks.CheckSpotlightProjectListTask
import com.fueledbycaffeine.spotlight.tasks.FixSpotlightProjectListTask
import com.fueledbycaffeine.spotlight.tooling.SpotlightModelBuilder
import com.fueledbycaffeine.spotlight.utils.include
import com.fueledbycaffeine.spotlight.utils.isSpotlightEnabled
import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import javax.inject.Inject

/**
 * A [Settings] plugin to ease management of projects included in large builds.
 *
 * plugins {
 *   id 'com.fueledbycaffeine.spotlight'
 * }
 */
public abstract class SpotlightSettingsPlugin @Inject constructor(
  private val registry: ToolingModelBuilderRegistry
) : Plugin<Settings> {
  public override fun apply(settings: Settings): Unit = settings.run {
    // Register the tooling model builder for IDE integration
    registry.register(SpotlightModelBuilder())
    // Create the extension
    extensions.getSpotlightExtension()
    // DSL is not available until then
    gradle.settingsEvaluated { applySpotlightConfiguration() }
  }
}

/**
 * The actual setup required to configure the plugin.
 *
 * It is a separate public method so that other plugins can wrap spotlight and still configure it
 * properly.
 * https://github.com/gradle/gradle/issues/17914
 */
public fun Settings.applySpotlightConfiguration(): Unit = settings.run {
  val extension = extensions.getSpotlightExtension()
  val includedProjects = SpotlightIncludedProjectsValueSource.of(this, extension).get()
  include(includedProjects)

  // Report Spotlight metrics to Develocity if available
  reportToDevelocity(includedProjects)
  gradle.rootProject {
    registerSpotlightLintTasks(it)
  }
}

private fun Settings.registerSpotlightLintTasks(project: Project) {
  val allProjectsFile = settingsDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)

  project.tasks.register(FixSpotlightProjectListTask.NAME, FixSpotlightProjectListTask::class.java) { task ->
    task.group = "spotlight"
    task.description = "Auto-fixes issues in ${SpotlightProjectList.ALL_PROJECTS_LOCATION} by removing invalid projects, adding missing ones, and sorting"
    task.projectsFile.set(allProjectsFile)
    task.rootDirectory.set(settingsDir)
  }

  val checkSpotlightSort = project.tasks
    .register(CheckSpotlightProjectListTask.NAME, CheckSpotlightProjectListTask::class.java) { task ->
      task.group = "spotlight"
      task.description = "Checks if ${SpotlightProjectList.ALL_PROJECTS_LOCATION} is set up correctly"
      task.projectsFile.set(allProjectsFile)
      task.rootDirectory.set(settingsDir)
    }
  project.pluginManager.withPlugin("base") {
    project.tasks.named("check") {
      it.dependsOn(checkSpotlightSort)
    }
  }
}

private fun Settings.reportToDevelocity(includedProjects: Set<GradlePath>) {
  val isSpotlightEnabled = isSpotlightEnabled
  pluginManager.withPlugin("com.gradle.develocity") {
    extensions.getByType(DevelocityConfiguration::class.java)
      .buildScan { scan ->
        // Report values in buildFinished {} so the properties aren't captured as part of configuration cache inputs
        scan.buildFinished {
          scan.value("Spotlight Enabled", isSpotlightEnabled.toString())
          val leafProjects = includedProjects.filter { it.hasBuildFile }
          scan.value("Spotlight Project Count", leafProjects.size.toString())
        }
      }
  }
}