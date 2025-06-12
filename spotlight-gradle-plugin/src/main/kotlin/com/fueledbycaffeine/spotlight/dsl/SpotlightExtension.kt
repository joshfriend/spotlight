package com.fueledbycaffeine.spotlight.dsl

import com.fueledbycaffeine.spotlight.SpotlightSettingsPlugin
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.IDE_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.BuildscriptMatchRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.ProjectPathMatchRule
import org.gradle.api.Action
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.file.BuildLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.intellij.lang.annotations.Language
import javax.inject.Inject

/**
 * Configuration options for [SpotlightSettingsPlugin].
 */
@Suppress("UnstableApiUsage")
public abstract class SpotlightExtension @Inject constructor(
  private val layout: BuildLayout,
  objects: ObjectFactory,
) {
  public companion object {
    public const val NAME: String = "spotlight"

    @JvmStatic
    public fun ExtensionContainer.getSpotlightExtension(): SpotlightExtension {
      return try {
        getByType(SpotlightExtension::class.java)
      } catch (_: UnknownDomainObjectException) {
        create(NAME, SpotlightExtension::class.java)
      }
    }
  }

  /**
   * Sets the level of processing to be done to support type-safe project accessors.
   *
   * Defaults to [TypeSafeAccessorInference.STRICT]
   *
   * @see <a href="https://docs.gradle.org/current/userguide/declaring_dependencies_basics.html#sec:type-safe-project-accessors">Gradle type-safe project accessors docs</a>
   */
  public val typeSafeAccessorInference: Property<TypeSafeAccessorInference> =
    objects.property(TypeSafeAccessorInference::class.java).convention(TypeSafeAccessorInference.STRICT)

  /**
   * Override the inferred target projects or projects from [IDE_PROJECTS_LOCATION].
   *
   * The value should be a provider where the value is a comma or newline separated list of Gradle project paths.
   *
   * This is useful for gradle-profiler scenarios where generating the IDE target projects list is not possible or for
   * running a task on a specific subset of projects without fully specifying the task path of each task.
   *
   * ```
   * def targetProjects = providers.gradleProperty("target-projects")
   *
   * spotlight {
   *   targetsOverride = targetProjects
   * }
   * ```
   */
  public val targetsOverride: Property<String> =
    objects.property(String::class.java).unsetConvention()

  /**
   * Add an implicit dependencies rule to include certain projects when the contents of the buildscript matches [pattern]
   */
  public fun whenBuildscriptMatches(
    @Language("RegExp") pattern: String,
    action: Action<MatchRuleHandler>,
  ) {
    buildscriptMatchRules.create(pattern) { rule ->
      action.execute(rule)
    }
  }

  /**
   * Add an implicit dependencies rule to include certain projects when the project path matches [pattern]
   */
  public fun whenProjectPathMatches(
    @Language("RegExp") pattern: String,
    action: Action<MatchRuleHandler>,
  ) {
    projectPathMatchRules.create(pattern) { rule ->
      action.execute(rule)
    }
  }

  private val buildscriptMatchRules = objects.domainObjectContainer(
    MatchRuleHandler::class.java,
    MatchRuleHandler.Factory(layout, objects)
  )
  private val projectPathMatchRules = objects.domainObjectContainer(
    MatchRuleHandler::class.java,
    MatchRuleHandler.Factory(layout, objects)
  )

  internal val rules: Set<ImplicitDependencyRule> get() =
    buildSet {
      addAll(buildscriptMatchRules.map { BuildscriptMatchRule(it.pattern, it.includes.get()) })
      addAll(projectPathMatchRules.map { ProjectPathMatchRule(it.pattern, it.includes.get()) })
    }

  internal val targetPathsOverride: Set<GradlePath>?
    get() = when (targetsOverride.isPresent) {
      true -> targetsOverride.get().split(",\n")
        .map { GradlePath(layout.rootDirectory.asFile, it.trim()) }
        .toSet()
      else -> null
    }
}
