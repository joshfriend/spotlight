package com.fueledbycaffeine.spotlight.dsl

import com.fueledbycaffeine.spotlight.SpotlightSettingsPlugin
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.BuildscriptMatchRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.ProjectPathMatchRule
import org.gradle.api.Action
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.file.BuildLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionContainer
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
