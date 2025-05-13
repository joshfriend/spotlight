package com.fueledbycaffeine.spotlight

import com.fueledbycaffeine.spotlight.dsl.SpotlightExtension
import com.fueledbycaffeine.spotlight.dsl.SpotlightExtension.Companion.getSpotlightExtension
import com.fueledbycaffeine.spotlight.graph.BreadthFirstSearch
import com.fueledbycaffeine.spotlight.utils.*
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
  public override fun apply(settings: Settings): Unit = settings.run {
    options = extensions.getSpotlightExtension()

    // DSL is not available until after settings evaluation
    gradle.settingsEvaluated {
      val projects = if (isIdeSync) {
        val targets = getIdeProjects()
        if (targets.isNotEmpty()) {
          logger.info("{} contains {} targets", options.ideProjects.get(), targets.size)
          implicitAndTransitiveDependenciesOf(targets)
        } else {
          logger.info("{} was missing or empty, including all projects", options.ideProjects.get())
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
            val target = projectDir.gradlePathRelativeTo(rootDir)
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

      logger.lifecycle("Spotlight included {} projects", projects.size)
      include(projects)
    }
  }

  private fun Settings.implicitAndTransitiveDependenciesOf(targets: List<GradlePath>): List<GradlePath> {
    val combinedTargets = addImplicitTargetsTo(targets)
    val bfsResults = measureTimedValue { BreadthFirstSearch.run(combinedTargets, options.rules) }
    logger.info("BFS search of project graph took {}ms", bfsResults.duration.inWholeMilliseconds)
    val transitives = bfsResults.value
    logger.info("Requested targets include {} projects transitively", transitives.size)
    return combinedTargets + transitives
  }

  private fun Settings.addImplicitTargetsTo(targets: List<GradlePath>): List<GradlePath> {
    val implicitTargets = getImplicitTargets()
    return when {
      implicitTargets.isEmpty() -> targets
      else -> {
        logger.info("{} includes {} implicit targets", options.implicitProjects.get(), implicitTargets.size)
        implicitTargets + targets
      }
    }
  }

  private fun Settings.getAllProjects() = readProjectList(options.allProjects)
  private fun Settings.getIdeProjects() = readProjectList(options.ideProjects)
  private fun Settings.getImplicitTargets() = readProjectList(options.implicitProjects)
}