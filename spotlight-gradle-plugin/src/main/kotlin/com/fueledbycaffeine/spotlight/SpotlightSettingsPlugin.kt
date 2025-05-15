@file:Suppress("InternalGradleApiUsage")

package com.fueledbycaffeine.spotlight

import com.fueledbycaffeine.spotlight.dsl.SpotlightExtension
import com.fueledbycaffeine.spotlight.dsl.SpotlightExtension.Companion.getSpotlightExtension
import com.fueledbycaffeine.spotlight.buildscript.graph.BreadthFirstSearch
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.gradlePathRelativeTo
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.TypeSafeProjectAccessorRule
import com.fueledbycaffeine.spotlight.buildscript.readProjectList
import com.fueledbycaffeine.spotlight.utils.*
import org.gradle.api.Plugin
import org.gradle.api.file.RegularFile
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.FeaturePreviews
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.internal.buildoption.FeatureFlags
import java.io.FileNotFoundException
import javax.inject.Inject
import kotlin.time.measureTimedValue

private val logger: Logger = Logging.getLogger(SpotlightSettingsPlugin::class.java)

/**
 * A [Settings] plugin to ease management of projects included in large builds.
 *
 * plugins {
 *   id 'com.fueledbycaffeine.spotlight'
 * }
 */
public class SpotlightSettingsPlugin @Inject constructor(
  private val features: FeatureFlags
): Plugin<Settings> {
  private lateinit var options: SpotlightExtension
  private lateinit var allProjects: Set<GradlePath>

  public override fun apply(settings: Settings): Unit = settings.run {
    options = extensions.getSpotlightExtension()
    allProjects = getAllProjects()

    // DSL is not available until after settings evaluation
    gradle.settingsEvaluated {
      val projects = if (isIdeSync) {
        val targets = getIdeProjects()
        if (targets.isNotEmpty()) {
          logger.info("{} contains {} targets", options.ideProjects.get(), targets.size)
          implicitAndTransitiveDependenciesOf(targets)
        } else {
          logger.info(
            """
            {} was missing or empty, including all projects.
            This can result in slow sync times! Spotlight specific projects using the IDE context menu action,
            or by running `spot <list of projects>`
            """.trimIndent(),
            options.ideProjects.get().asFile.toRelativeString(rootDir)
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
  }

  private fun Settings.implicitAndTransitiveDependenciesOf(targets: Set<GradlePath>): Set<GradlePath> {
    val combinedTargets = addImplicitTargetsTo(targets)
    val rules = when (features.isEnabled(FeaturePreviews.Feature.TYPESAFE_PROJECT_ACCESSORS)) {
      true -> {
        val typeSafeProjectAccessorMap = allProjects.associateBy { it.typeSafeAccessorName }
        options.rules + TypeSafeProjectAccessorRule(settings.rootProject.name, typeSafeProjectAccessorMap)
      }
      else -> options.rules
    }
    val bfsResults = measureTimedValue { BreadthFirstSearch.flatten(combinedTargets, rules) }
    logger.info("BFS search of project graph took {}ms", bfsResults.duration.inWholeMilliseconds)
    val transitives = bfsResults.value
    logger.info("Requested targets include {} projects transitively", transitives.size)
    return combinedTargets + transitives
  }

  private fun Settings.addImplicitTargetsTo(targets: Set<GradlePath>): Set<GradlePath> {
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

  internal fun Settings.readProjectList(property: Property<RegularFile>): Set<GradlePath> {
    val file = property.get().asFile
    return when {
      file.exists() -> rootDir.readProjectList(file)
      else -> emptySet()
    }
  }
}