package com.fueledbycaffeine.spotlight.dsl

import com.fueledbycaffeine.spotlight.SpotlightSettingsPlugin
import com.fueledbycaffeine.spotlight.buildscript.BuildFile
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.BuildscriptMatchRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.ProjectPathMatchRule
import org.gradle.api.Action
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.file.BuildLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Property
import org.intellij.lang.annotations.Language
import javax.inject.Inject

/**
 * Configuration options for [SpotlightSettingsPlugin].
 *
 * Example values represent the defaults:
 *
 * spotlight {
 *   allProjects file("gradle/all-projects.txt")
 *   ideProjects file("gradle/ide-projects.txt")
 *   implicitProjects file("gradle/implicit-projects.txt")
 * }
 */
@Suppress("UnstableApiUsage")
public abstract class SpotlightExtension @Inject constructor(
  layout: BuildLayout,
  objects: ObjectFactory,
) {
  public companion object {
    public const val NAME: String = "spotlight"
    public const val ALL_PROJECTS_FILE: String = "gradle/all-projects.txt"
    public const val IDE_PROJECTS_FILE: String = "gradle/ide-projects.txt"
    public const val IMPLICIT_PROJECTS_FILE: String = "gradle/implicit-projects.txt"

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
   * A file containing a list of every project in the build. You may omit projects from
   * this list if they need to be conditionally included based on conditions managed outside this plugin, but they will
   * have to be added to your build some other way!
   * For build invocations that are not IDE sync, or when [ideProjects] file is missing or empty, all projects are
   * loaded in the build.
   */
  public val allProjects: Property<RegularFile> = objects.fileProperty()
    .convention(layout.rootDirectory.file(ALL_PROJECTS_FILE))

  /**
   * A file containing the list of projects you would like loaded into the IDE. The projects listed here, as well as
   * any of their transitives identified by [BuildFile.parseDependencies] will be used instead of the [allProjects]
   * list during IDE sync.
   */
  public val ideProjects: Property<RegularFile> = objects.fileProperty()
    .convention(layout.rootDirectory.file(IDE_PROJECTS_FILE))

  /**
   * A file containing a list of projects that should always be included in the build. This is useful in cases where
   * your conventions plugins or some other build logic adds project dependencies to your build dynamically (e.g.
   * adding a testing utilities project automatically to every project).
   * This plugin parses your build graph statically without configuring projects, so it does not know about any
   * dependencies added by build logic!
   */
  public val implicitProjects: Property<RegularFile> = objects.fileProperty()
    .convention(layout.rootDirectory.file(IMPLICIT_PROJECTS_FILE))

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
