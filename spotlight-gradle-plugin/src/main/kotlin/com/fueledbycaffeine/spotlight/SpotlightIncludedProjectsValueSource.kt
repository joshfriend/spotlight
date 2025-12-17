package com.fueledbycaffeine.spotlight

import com.fueledbycaffeine.spotlight.SpotlightIncludedProjectsValueSource.Params
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import com.fueledbycaffeine.spotlight.buildscript.SpotlightRulesList
import com.fueledbycaffeine.spotlight.buildscript.computeSpotlightRules
import com.fueledbycaffeine.spotlight.buildscript.gradlePathRelativeTo
import com.fueledbycaffeine.spotlight.buildscript.graph.BreadthFirstSearch
import com.fueledbycaffeine.spotlight.dsl.SpotlightExtension
import com.fueledbycaffeine.spotlight.utils.guessProjectsFromTaskRequests
import com.fueledbycaffeine.spotlight.utils.isIdeSync
import com.fueledbycaffeine.spotlight.utils.isSpotlightEnabled
import org.gradle.TaskExecutionRequest
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.nio.file.Path
import kotlin.time.measureTimedValue

/**
 * Wraps the operations to determine the list of included projects so that reading files here does
 * not get captured in configuration cache.
 */
internal abstract class SpotlightIncludedProjectsValueSource : ValueSource<Set<GradlePath>, Params> {
  override fun obtain(): Set<GradlePath> {
    val projectsOverride = targetPathsOverride
    val projects = if (!parameters.spotlightEnabled.get()) {
      logger.lifecycle(
        "Spotlight is disabled, all projects will be loaded from {}",
        SpotlightProjectList.ALL_PROJECTS_LOCATION,
      )
      getAllProjects()
    } else if (projectsOverride.isNotEmpty()) {
      logger.info("spotlight.targetsOverride contains {} targets", projectsOverride.size)
      implicitAndTransitiveDependenciesOf(projectsOverride)
    } else {
      if (parameters.ideSync.get()) {
        val allProjects = lazy { getAllProjects() }
        val targets = getIdeProjects(allProjects::value)
        if (targets.isNotEmpty()) {
          logger.info("{} matches {} targets", SpotlightProjectList.IDE_PROJECTS_LOCATION, targets.size)
          implicitAndTransitiveDependenciesOf(targets)
        } else {
          logger.info(
            "{} was missing or empty, including all projects. This can result in slow sync times!",
            SpotlightProjectList.IDE_PROJECTS_LOCATION,
          )
          allProjects.value
        }
      } else {
        // TODO: why does start parameters never have a nonnull project path and the task paths are just listed in the args?
        val taskPaths = guessProjectsFromTaskRequests(rootDirectory, parameters.taskRequests.get())
        if (!taskPaths.isEmpty() && taskPaths.none { it.path.isEmpty() }) {
          logger.info("Using transitives for projects of requested tasks")
          implicitAndTransitiveDependenciesOf(taskPaths)
        } else {
          val projectDir = parameters.projectDir.getOrNull()?.asFile?.toPath()
          val target = projectDir?.gradlePathRelativeTo(rootDirectory)
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
    val mainBuildProjects = projects.filter { it.isFromMainBuild }.toSet()
    logger.lifecycle("Spotlight included {} projects", mainBuildProjects.size)
    return mainBuildProjects
  }

  private fun implicitAndTransitiveDependenciesOf(targets: Set<GradlePath>): Set<GradlePath> {
    // Ignore project name and type-safe accessors set in rules JSON as we read the source of truth here
    // TODO or error if not equal?
    val implicitRules = getSpotlightRules().implicitRules
    val projectName = parameters.rootProjectName.get()
    val rules = computeSpotlightRules(rootDirectory, projectName, implicitRules) { getAllProjects() }

    val (targetsAndTransitives, duration) = measureTimedValue { BreadthFirstSearch.flatten(targets, rules) }
    logger.info("BFS search of project graph took {}ms", duration.inWholeMilliseconds)
    logger.info("Requested targets include {} projects transitively", targetsAndTransitives.size - targets.size)
    return targetsAndTransitives
  }

  private val rootDirectory: Path
    get() = parameters.rootDirectory.asFile.get().toPath()

  private val targetPathsOverride: Set<GradlePath>
    get() = parameters.targetsOverride.getOrElse("")
      .split(",", System.lineSeparator())
      .filterNot { it.isEmpty() }
      .map { GradlePath(rootDirectory, it.trim()) }
      .toSet()

  private fun getAllProjects() = SpotlightProjectList.allProjects(rootDirectory).read()
  private fun getIdeProjects(allProjects: () -> Set<GradlePath>) = SpotlightProjectList.ideProjects(rootDirectory, allProjects).read()
  private fun getSpotlightRules() = SpotlightRulesList(rootDirectory).read()

  interface Params : ValueSourceParameters {
    val spotlightEnabled: Property<Boolean>
    val ideSync: Property<Boolean>
    val rootDirectory: DirectoryProperty
    val projectDir: DirectoryProperty
    val taskRequests: ListProperty<TaskExecutionRequest>
    val rootProjectName: Property<String>
    val targetsOverride: Property<String>
  }

  companion object {
    private val logger = Logging.getLogger(SpotlightIncludedProjectsValueSource::class.java)

    @JvmStatic
    fun of(settings: Settings, spotlightOptions: SpotlightExtension): Provider<Set<GradlePath>> {
      return settings.providers.of(SpotlightIncludedProjectsValueSource::class.java) {
        it.parameters.rootDirectory.set(settings.settingsDir)
        it.parameters.rootProjectName.set(settings.rootProject.name)
        it.parameters.projectDir.set(settings.gradle.startParameter.projectDir)
        it.parameters.taskRequests.set(settings.gradle.startParameter.taskRequests)
        it.parameters.targetsOverride.set(spotlightOptions.targetsOverride)
        it.parameters.ideSync.set(settings.isIdeSync)
        it.parameters.spotlightEnabled.set(settings.isSpotlightEnabled)
      }
    }
  }
}