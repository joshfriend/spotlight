package com.fueledbycaffeine.spotlight

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import com.fueledbycaffeine.spotlight.buildscript.gradlePathRelativeTo
import com.fueledbycaffeine.spotlight.buildscript.graph.BreadthFirstSearch
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.TypeSafeProjectAccessorRule
import com.fueledbycaffeine.spotlight.dsl.SpotlightExtension
import com.fueledbycaffeine.spotlight.dsl.SpotlightExtension.Companion.getSpotlightExtension
import com.fueledbycaffeine.spotlight.utils.guessProjectsFromTaskRequests
import com.fueledbycaffeine.spotlight.utils.include
import com.fueledbycaffeine.spotlight.utils.isIdeSync
import com.fueledbycaffeine.spotlight.utils.isSpotlightEnabled
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.io.FileNotFoundException
import kotlin.time.measureTimedValue

private val logger: Logger = Logging.getLogger(SpotlightSettingsPlugin::class.java)

/**
 * A [Settings] plugin to ease management of projects included in large builds.
 *
 * plugins {
 *   id 'com.fueledbycaffeine.spotlight'
 * }
 */
public class SpotlightSettingsPlugin: Plugin<Settings> {
  private lateinit var options: SpotlightExtension
  private lateinit var allProjects: Set<GradlePath>

  public override fun apply(settings: Settings): Unit = settings.run {
    options = extensions.getSpotlightExtension()

    // DSL is not available until after settings evaluation
    gradle.settingsEvaluated {
      allProjects = getAllProjects()
      if (isSpotlightEnabled) {
        setupSpotlight()
      } else {
        logger.lifecycle(
          "Spotlight is disabled, all projects will be loaded from {}",
          SpotlightProjectList.ALL_PROJECTS_LOCATION,
        )
        include(allProjects)
      }
    }
  }

  private fun Settings.setupSpotlight() {
    val projects = if (isIdeSync) {
      val targets = getIdeProjects()
      if (targets.isNotEmpty()) {
        logger.info("{} contains {} targets", SpotlightProjectList.IDE_PROJECTS_LOCATION, targets.size)
        implicitAndTransitiveDependenciesOf(targets)
      } else {
        logger.warn(
          """
          {} was missing or empty, including all projects.
          This can result in slow sync times! Spotlight specific projects using the IDE context menu action.
          """.trimIndent(),
          SpotlightProjectList.IDE_PROJECTS_LOCATION,
        )
        allProjects
      }
    } else {
      // TODO: why does start parameters never have a nonnull project path and the task paths are just listed in the args?
      val taskPaths = try {
        guessProjectsFromTaskRequests()
      } catch (e: FileNotFoundException) {
        logger.warn("Not sure how to map all tasks to projects: {}", e.message)
        null
      }
      if (!taskPaths.isNullOrEmpty()) {
        logger.info("Using transitives for projects of requested tasks")
        implicitAndTransitiveDependenciesOf(taskPaths)
      } else {
        val projectDir = gradle.startParameter.projectDir
        val target = projectDir?.gradlePathRelativeTo(rootDir)
        if (target != null && !target.isRootProject) {
          val childProjects = target.expandChildProjects()
          val projectsFromWorkingDir = when (target.hasBuildFile) {
            true -> childProjects + target
            else -> childProjects
          }
          logger.info("Gradle project dir given (-p), using child projects and transitives of {}", target.path)
          implicitAndTransitiveDependenciesOf(projectsFromWorkingDir)
        } else {
          allProjects
        }
      }
    }

    logger.lifecycle("Spotlight included {} projects", projects.size)
    include(projects)
  }

  private fun Settings.implicitAndTransitiveDependenciesOf(targets: Set<GradlePath>): Set<GradlePath> {
    val typeSafeProjectAccessorMap = allProjects.associateBy { it.typeSafeAccessorName }
    val rules = options.rules + TypeSafeProjectAccessorRule(settings.rootProject.name, typeSafeProjectAccessorMap)
    val bfsResults = measureTimedValue { BreadthFirstSearch.flatten(targets, rules) }
    logger.info("BFS search of project graph took {}ms", bfsResults.duration.inWholeMilliseconds)
    val transitives = bfsResults.value
    logger.info("Requested targets include {} projects transitively", transitives.size)
    return targets + transitives
  }

  private fun Settings.getAllProjects() = SpotlightProjectList.allProjects(rootDir.toPath()).read()
  private fun Settings.getIdeProjects() = SpotlightProjectList.ideProjects(rootDir.toPath()).read()
}