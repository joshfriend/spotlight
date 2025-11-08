@file:Suppress("UnstableApiUsage")

package com.fueledbycaffeine.spotlight

import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import com.fueledbycaffeine.spotlight.dsl.SpotlightExtension.Companion.getSpotlightExtension
import com.fueledbycaffeine.spotlight.tasks.CheckSpotlightProjectListTask
import com.fueledbycaffeine.spotlight.tasks.SortSpotlightProjectsListTask
import com.fueledbycaffeine.spotlight.utils.include
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings

/**
 * A [Settings] plugin to ease management of projects included in large builds.
 *
 * plugins {
 *   id 'com.fueledbycaffeine.spotlight'
 * }
 */
public class SpotlightSettingsPlugin: Plugin<Settings> {
  public override fun apply(settings: Settings): Unit = settings.run {
    // Create the extension
    val extension = extensions.getSpotlightExtension()
    // DSL is not available until then
    gradle.settingsEvaluated {
      val includedProjects = SpotlightIncludedProjectsValueSource.of(this, extension)
      include(includedProjects.get())
    }
    gradle.rootProject {
      registerSpotlightLintTasks(it)
    }
  }

  private fun Settings.registerSpotlightLintTasks(project: Project) {
    val allProjectsFile = settingsDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)

    project.tasks.register(SortSpotlightProjectsListTask.NAME, SortSpotlightProjectsListTask::class.java) { task ->
      task.group = "spotlight"
      task.description = "Sorts the ${SpotlightProjectList.ALL_PROJECTS_LOCATION} file"
      task.projectList.set(allProjectsFile)
    }
    val checkSpotlightSort = project.tasks
      .register(CheckSpotlightProjectListTask.NAME, CheckSpotlightProjectListTask::class.java) { task ->
        task.group = "spotlight"
        task.description = "Checks if ${SpotlightProjectList.ALL_PROJECTS_LOCATION} is sorted"
        task.projectsFile.set(allProjectsFile)
      }
    project.pluginManager.withPlugin("base") {
      project.tasks.named("check") {
        it.dependsOn(checkSpotlightSort)
      }
    }
  }
}
