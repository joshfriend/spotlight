package com.fueledbycaffeine.bettersettings

import com.fueledbycaffeine.bettersettings.graph.BreadthFirstSearch
import com.fueledbycaffeine.bettersettings.graph.GradlePath
import com.fueledbycaffeine.bettersettings.graph.expandChildProjects
import com.fueledbycaffeine.bettersettings.graph.gradlePathRelativeTo
import com.fueledbycaffeine.bettersettings.utils.readProjectList
import org.gradle.TaskExecutionRequest
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import kotlin.collections.plus
import kotlin.io.path.exists

private val logger: Logger = Logging.getLogger(BetterSettingsPlugin::class.java)

class BetterSettingsPlugin: Plugin<Settings> {
  override fun apply(target: Settings) = target.run {
    val projectDir = gradle.startParameter.projectDir

    val projects = if (isIdeSync) {
      val targets = getTargetProjects()
      if (targets.isNotEmpty()) {
        logger.lifecycle("{} contains {} targets", targetProjectListFile, targets.size)
        implicitAndTransitiveDependenciesOf(targets)
      } else {
        logger.lifecycle("{} was missing or empty, including all projects", targetProjectListFile)
        getAllProjects()
      }
    } else if (startParameter.taskRequests.allProjectsSpecified) {
      val taskProjects = startParameter.taskRequests
        .mapNotNull { it.projectPath }
        .map { GradlePath(rootDir, it) }
      logger.lifecycle("Using transitives for projects of requested tasks")
      implicitAndTransitiveDependenciesOf(taskProjects)
    } else if (projectDir != null) {
      val target = projectDir.gradlePathRelativeTo(rootDir)
      val childProjects = target.expandChildProjects()
      logger.lifecycle("Gradle project dir given, using child projects and transitives of {}", target.path)
      implicitAndTransitiveDependenciesOf(listOf(target) + childProjects)
    } else {
      getAllProjects()
    }

    logger.lifecycle("Including {} projects", projects.size)
    include(projects)
  }

  private val Collection<TaskExecutionRequest>.allProjectsSpecified: Boolean
    get() = all { it.projectPath != null }

  private fun Settings.implicitAndTransitiveDependenciesOf(targets: List<GradlePath>): List<GradlePath> {
    val combinedTargets = addImplicitTargetsTo(targets)
    val transitives = BreadthFirstSearch.run(combinedTargets)
    logger.lifecycle("Requested targets include {} projects transitively", transitives.size)
    return combinedTargets + transitives
  }

  private fun Settings.addImplicitTargetsTo(targets: List<GradlePath>): List<GradlePath> {
    val implicitTargets = getImplicitTargets()
    return when {
      implicitTargets.isEmpty() -> targets
      else -> {
        logger.lifecycle("{} includes {} implicit targets", implicitProjectListFile, implicitTargets.size)
        implicitTargets + targets
      }
    }
  }

  private fun Settings.getAllProjects(): List<GradlePath> = when {
    allProjectListFile.exists() -> rootDir.readProjectList(allProjectListFile)
    else -> emptyList()
  }

  private fun Settings.getTargetProjects(): List<GradlePath> = when {
    targetProjectListFile.exists() -> rootDir.readProjectList(targetProjectListFile)
    else -> emptyList()
  }

  private fun Settings.getImplicitTargets(): List<GradlePath> = when {
    implicitProjectListFile.exists() -> rootDir.readProjectList(implicitProjectListFile)
    else -> emptyList()
  }
}