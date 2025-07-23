package com.fueledbycaffeine.spotlight

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import com.fueledbycaffeine.spotlight.buildscript.SpotlightRulesList
import com.fueledbycaffeine.spotlight.buildscript.computeSpotlightRules
import com.fueledbycaffeine.spotlight.buildscript.gradlePathRelativeTo
import com.fueledbycaffeine.spotlight.buildscript.graph.BreadthFirstSearch
import com.fueledbycaffeine.spotlight.dsl.SpotlightExtension
import com.fueledbycaffeine.spotlight.dsl.SpotlightExtension.Companion.getSpotlightExtension
import com.fueledbycaffeine.spotlight.utils.guessProjectsFromTaskRequests
import com.fueledbycaffeine.spotlight.utils.include
import com.fueledbycaffeine.spotlight.utils.isIdeSync
import com.fueledbycaffeine.spotlight.utils.isSpotlightEnabled
import java.io.FileNotFoundException
import kotlin.time.measureTimedValue
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

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

  public override fun apply(settings: Settings): Unit = settings.run {
    options = extensions.getSpotlightExtension()

    if (isSpotlightEnabled) {
      gradle.settingsEvaluated { setupSpotlight() }
    } else {
      logger.lifecycle(
        "Spotlight is disabled, all projects will be loaded from {}",
        SpotlightProjectList.ALL_PROJECTS_LOCATION,
      )
      include(getAllProjects())
    }
  }

  private fun Settings.setupSpotlight() {
    val projectsOverride = options.targetPathsOverride
    val projects = if (projectsOverride.isNotEmpty()) {
      logger.info("spotlight.targetsOverride contains {} targets", projectsOverride.size)
      implicitAndTransitiveDependenciesOf(projectsOverride)
    } else {
      if (isIdeSync) {
        val targets = getIdeProjects()
        if (targets.isNotEmpty()) {
          logger.info("{} contains {} targets", SpotlightProjectList.IDE_PROJECTS_LOCATION, targets.size)
          implicitAndTransitiveDependenciesOf(targets)
        } else {
          logger.info(
            "{} was missing or empty, including all projects. This can result in slow sync times!",
            SpotlightProjectList.IDE_PROJECTS_LOCATION,
          )
          getAllProjects()
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
            getAllProjects()
          }
        }
      }
    }

    // Composite builds may contain projects from other builds besides this one. Included builds cannot have `include()`
    // called for their projects again so we have to filter those from spotlight
    val mainBuildProjects = projects.filter { it.isFromMainBuild }
    logger.lifecycle("Spotlight included {} projects", mainBuildProjects.size)
    include(mainBuildProjects)
  }

  private fun Settings.implicitAndTransitiveDependenciesOf(targets: Set<GradlePath>): Set<GradlePath> {
    val typeSafeInferenceLevel = options.typeSafeAccessorInference.get()
    logger.info("Spotlight type-safe project accessor inference is {}", typeSafeInferenceLevel)

    // Ignore project name and type-safe accessors set in rules JSON as we read the source of truth here
    // TODO or error if not equal?
    val implicitRules = getSpotlightRules().implicitRules
    val projectName = settings.rootProject.name
    val rules = computeSpotlightRules(rootDir.toPath(), projectName, implicitRules, typeSafeInferenceLevel) { getAllProjects() }

    val (targetsAndTransitives, duration) = measureTimedValue { BreadthFirstSearch.flatten(targets, rules) }
    logger.info("BFS search of project graph took {}ms", duration.inWholeMilliseconds)
    logger.info("Requested targets include {} projects transitively", targetsAndTransitives.size - targets.size)
    return targetsAndTransitives
  }

  private fun Settings.getAllProjects() = SpotlightProjectList.allProjects(settingsDir.toPath()).read()
  private fun Settings.getIdeProjects() = SpotlightProjectList.ideProjects(settingsDir.toPath()).read()
  private fun Settings.getSpotlightRules() = SpotlightRulesList(settingsDir.toPath()).read()
}