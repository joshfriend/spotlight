package com.fueledbycaffeine.spotlight.dsl

import com.fueledbycaffeine.spotlight.SpotlightSettingsPlugin
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.ALL_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.BuildscriptMatchRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.ProjectPathMatchRule
import org.gradle.api.Action
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.file.BuildLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Property
import org.intellij.lang.annotations.Language
import javax.inject.Inject

/**
 * Configuration options for [SpotlightSettingsPlugin].
 */
@Suppress("UnstableApiUsage")
public abstract class SpotlightExtension @Inject constructor(
  layout: BuildLayout,
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
   * Enable if your project uses type-safe project accessors.
   *
   * https://docs.gradle.org/current/userguide/declaring_dependencies_basics.html#sec:type-safe-project-accessors
   *
   * This reads the [ALL_PROJECTS_LOCATION] file to compute the project accessor mapping, and thus makes your builds
   * configuration caching sensitive to the list of all projects instead of the target projects.
   */
  public val isTypeSafeAccessorsEnabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

  public fun whenBuildscriptMatches(
    @Language("RegExp") pattern: String,
    action: Action<MatchRuleHandler>,
  ) {
    buildscriptMatchRules.create(pattern) { rule ->
      action.execute(rule)
    }
  }

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
}
