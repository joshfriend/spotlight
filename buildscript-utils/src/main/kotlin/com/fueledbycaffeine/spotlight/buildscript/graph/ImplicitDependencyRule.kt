package com.fueledbycaffeine.spotlight.buildscript.graph

import com.fueledbycaffeine.spotlight.buildscript.GradlePath

public sealed interface ImplicitDependencyRule {
//  public val pattern: Regex
  public val includedProjects: Set<GradlePath>

  public data class BuildscriptMatchRule(
    val pattern: Regex,
    override val includedProjects: Set<GradlePath>,
  ) : ImplicitDependencyRule

  public data class ProjectPathMatchRule(
    val pattern: Regex,
    override val includedProjects: Set<GradlePath>,
  ) : ImplicitDependencyRule

  public data class TypeSafeProjectAccessorRule(
    val rootProjectName: String,
    val typeSafeAccessorMap: Map<String, GradlePath>,
    override val includedProjects: Set<GradlePath> = emptySet(),
  ) : ImplicitDependencyRule

  /**
   * Gradle automatically includes intermediate parent projects of nested projects.
   *
   * ```
   * :libs:foo:impl -> :libs:foo
   * ```
   *
   * For Groovy, this is sort of fine because it doesn't try to compile them
   * until they're executed. For Kotlin Gradle script though, this is a problem
   * because Gradle eagerly compiles them during configuration. So, we must
   * treat them as implicit dependencies to work.
   */
  public data object KotlinGradleScriptNestingRule : ImplicitDependencyRule {
    override val includedProjects: Set<GradlePath> = emptySet()
  }
}